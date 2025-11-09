package org.sdk;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class OpenWeatherSdkFactory {

    private static final String API_KEY_ERROR_MSG = "API key must not be null";
    private static final ConcurrentMap<String, OpenWeatherSdk> INSTANCES = new ConcurrentHashMap<>();

    private OpenWeatherSdkFactory() {
    }

    /**
     * Create SDK with default config
     */
    public static OpenWeatherSdk create(String apiKey) {
        SdkConfig config = SdkConfig.builder()
                .build();
        return create(apiKey, config);
    }

    /**
     * Create SDK with custom config
     */
    public static OpenWeatherSdk create(String apiKey, SdkConfig config) {
        Objects.requireNonNull(apiKey, API_KEY_ERROR_MSG);
        Objects.requireNonNull(config, "Config must not be null");

        return INSTANCES.computeIfAbsent(apiKey, k -> new OpenWeatherSdk(k, config));
    }

    /**
     * Delete SDK instance and close it properly
     */
    public static void delete(String apiKey) {
        Objects.requireNonNull(apiKey, API_KEY_ERROR_MSG);

        OpenWeatherSdk sdk = INSTANCES.remove(apiKey);
        if (sdk != null) {
            try {
                sdk.close();
            } catch (Exception e) {
                System.err.println("Error closing SDK instance: " + e.getMessage());
            }
        }
    }

    /**
     * Get existing SDK instance or null if not found
     */
    public static OpenWeatherSdk get(String apiKey) {
        Objects.requireNonNull(apiKey, API_KEY_ERROR_MSG);
        return INSTANCES.get(apiKey);
    }

    /**
     * Close and remove all SDK instances
     */
    public static void shutdown() {
        INSTANCES.keySet().forEach(OpenWeatherSdkFactory::delete);
    }

}
