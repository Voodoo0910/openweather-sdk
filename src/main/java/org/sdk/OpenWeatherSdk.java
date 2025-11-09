package org.sdk;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class OpenWeatherSdk {

    private final String apiKey;
    private final SdkConfig config;
    private final ExecutorService asyncPool;
    private final Map<String, CachedWeather> cache;
    private final ScheduledExecutorService scheduler;
    private final OpenWeatherClient openWeatherClient;
    private final ReadWriteLock cacheLock = new ReentrantReadWriteLock();
    private volatile boolean closed = false;

    OpenWeatherSdk(String apiKey, SdkConfig config) {
        this.apiKey = Objects.requireNonNull(apiKey, "API key must not be null");
        this.config = Objects.requireNonNull(config, "Config must not be null");
        this.openWeatherClient = new OpenWeatherClient(apiKey);

        this.cache = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, CachedWeather> eldest) {
                return size() > config.maxCacheSize();
            }
        };

        if (config.mode() == SdkMode.POLLING) {
            this.asyncPool = Executors.newFixedThreadPool(config.threadPoolSize(), r -> {
                Thread t = new Thread(r, "owm-async-" + System.identityHashCode(this));
                t.setDaemon(true);
                return t;
            });

            this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "owm-poller-" + System.identityHashCode(this));
                t.setDaemon(true);
                return t;
            });

            scheduler.scheduleAtFixedRate(
                    this::pollAllStoredCitiesAsync,
                    0,
                    config.pollingIntervalSeconds(),
                    TimeUnit.SECONDS
            );
        } else {
            this.asyncPool = null;
            this.scheduler = null;
        }
    }

    /**
     * Returns JSON with the current weather for the city name
     */
    public String getWeatherByCity(String cityName) throws SdkException {
        ensureOpen();

        if (cityName == null || cityName.trim().isEmpty()) {
            throw new SdkException("City name must not be null or empty");
        }

        String key = normalizeKey(cityName);

        cacheLock.readLock().lock();
        try {
            CachedWeather cached = cache.get(key);
            if (cached != null && !cached.isExpired(config.ttlSeconds())) {
                return cached.json();
            }
        } finally {
            cacheLock.readLock().unlock();
        }

        cacheLock.writeLock().lock();
        try {
            String json = openWeatherClient.fetchWeatherFromApi(cityName);
            cache.put(key, new CachedWeather(json, Instant.now().getEpochSecond()));

            return json;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SdkException("Request interrupted", e);
        } catch (IOException e) {
            throw new SdkException("I/O error: " + e.getMessage(), e);
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Clearing the cache
     */
    public void clearCache() {
        cacheLock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Removing a specific city from the cache
     */
    public void deleteCity(String cityName) {
        if (cityName == null || cityName.trim().isEmpty()) {
            return;
        }

        cacheLock.writeLock().lock();
        try {
            cache.remove(normalizeKey(cityName));
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    /**
     * Returns current cache size
     */
    public int getCacheSize() {
        cacheLock.readLock().lock();
        try {
            return cache.size();
        } finally {
            cacheLock.readLock().unlock();
        }
    }

    /**
     * Closes the SDK and stops background threads gracefully.
     */
    public void close() {
        cacheLock.writeLock().lock();
        try {
            if (closed) {
                return;
            }
            closed = true;
        } finally {
            cacheLock.writeLock().unlock();
        }

        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (asyncPool != null) {
            asyncPool.shutdown();
            try {
                if (!asyncPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    asyncPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                asyncPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        cacheLock.writeLock().lock();
        try {
            cache.clear();
        } finally {
            cacheLock.writeLock().unlock();
        }
    }

    private void ensureOpen() throws SdkException {
        if (closed) {
            throw new SdkException("SDK instance is closed");
        }
    }

    private String normalizeKey(String cityName) {
        return cityName.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * Asynchronous update of all cities from cache
     */
    private void pollAllStoredCitiesAsync() {
        List<String> cities;
        cacheLock.readLock().lock();
        try {
            if (closed) {
                return;
            }
            cities = new ArrayList<>(cache.keySet());
        } finally {
            cacheLock.readLock().unlock();
        }

        for (String city : cities) {
            asyncPool.submit(() -> {
                try {
                    String json = openWeatherClient.fetchWeatherFromApi(city);

                    cacheLock.writeLock().lock();
                    try {
                        if (!closed) {
                            cache.put(city, new CachedWeather(json, Instant.now().getEpochSecond()));
                        }
                    } finally {
                        cacheLock.writeLock().unlock();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new SdkException("Request interrupted", e);
                } catch (IOException e) {
                    throw new SdkException("I/O error: " + e.getMessage(), e);
                }
            });
        }
    }

    private record CachedWeather(String json, long fetchedAt) {
        boolean isExpired(long ttlSeconds) {
            return Instant.now().getEpochSecond() - fetchedAt > ttlSeconds;
        }
    }

}
