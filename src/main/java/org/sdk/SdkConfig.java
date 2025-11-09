package org.sdk;

public record SdkConfig(
        SdkMode mode,
        long ttlSeconds,
        long pollingIntervalSeconds,
        int maxCacheSize,
        int threadPoolSize
) {

    public SdkConfig {
        if (mode == null) {
            throw new IllegalArgumentException("Mode must not be null");
        }
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("TTL must be positive");
        }
        if (pollingIntervalSeconds <= 0) {
            throw new IllegalArgumentException("Polling interval must be positive");
        }
        if (maxCacheSize <= 0) {
            throw new IllegalArgumentException("Max cache size must be positive");
        }
        if (threadPoolSize <= 0) {
            throw new IllegalArgumentException("Thread pool size must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private SdkMode mode = SdkMode.ON_DEMAND;
        private long ttlSeconds = 600;
        private long pollingIntervalSeconds = 600;
        private int maxCacheSize = 10;
        private int threadPoolSize = Math.min(4, Runtime.getRuntime().availableProcessors());

        public Builder mode(SdkMode mode) {
            this.mode = mode;
            return this;
        }

        public Builder ttlSeconds(long ttlSeconds) {
            this.ttlSeconds = ttlSeconds;
            return this;
        }

        public Builder pollingIntervalSeconds(long pollingIntervalSeconds) {
            this.pollingIntervalSeconds = pollingIntervalSeconds;
            return this;
        }

        public Builder maxCacheSize(int maxCacheSize) {
            this.maxCacheSize = maxCacheSize;
            return this;
        }

        public Builder threadPoolSize(int threadPoolSize) {
            this.threadPoolSize = threadPoolSize;
            return this;
        }

        public SdkConfig build() {
            return new SdkConfig(
                    mode,
                    ttlSeconds,
                    pollingIntervalSeconds,
                    maxCacheSize,
                    threadPoolSize
            );
        }
    }

}
