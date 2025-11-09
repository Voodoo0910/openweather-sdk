# OpenWeather SDK

A lightweight Java SDK for fetching current weather data from the [OpenWeather API](https://openweathermap.org/api).  
Supports caching, asynchronous polling, multithreading, and graceful shutdown.

---

## ⚙️ Features

- Fetch current weather by city name
- In-memory caching with TTL expiration
- Asynchronous polling mode for automatic updates
- Thread-safe cache access
- Retry mechanism with exponential backoff
- Factory for easy SDK instance management

---

## 📐 Architecture Overview

| Class | Description |
|-------|--------------|
| `OpenWeatherSdk` | Main entry point for fetching weather and managing cache |
| `OpenWeatherClient` | HTTP client for OpenWeather API requests |
| `OpenWeatherSdkFactory` | Factory for creating and managing SDK instances |
| `SdkConfig` | Configuration object (TTL, cache size, polling interval, etc.) |
| `SdkMode` | SDK operation mode: `ON_DEMAND` or `POLLING` |
| `SdkException` | Custom exception class for SDK errors |
| `CityInfoResponse` / `WeatherResponse` | DTOs for OpenWeather API responses |

---

## 🏗️ Installation

### Add the required dependency (e.g. via Maven):

```xml
<dependencies>
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.0</version>
    </dependency>
</dependencies>
```

---

## 🏗️ Usage

### Basic example

```java
import org.sdk.*;

public class Main {
    public static void main(String[] args) {
        String apiKey = "YOUR_API_KEY";

        // Create SDK with default configuration
        OpenWeatherSdk sdk = OpenWeatherSdkFactory.create(apiKey);

        try {
            String weatherJson = sdk.getWeatherByCity("London");
            System.out.println(weatherJson);
        } catch (SdkException e) {
            System.err.println("Weather request failed: " + e.getMessage());
        } finally {
            OpenWeatherSdkFactory.shutdown();
        }
    }
}
```

---

### Custom configuration example

```java
SdkConfig config = SdkConfig.builder()
        .mode(SdkMode.POLLING)               // asynchronous polling mode
        .ttlSeconds(300)                     // cache TTL = 5 minutes
        .pollingIntervalSeconds(120)         // update every 2 minutes
        .maxCacheSize(50)                    // store up to 50 cities
        .threadPoolSize(4)                   // 4 worker threads
        .build();

OpenWeatherSdk sdk = OpenWeatherSdkFactory.create("YOUR_API_KEY", config);
```

---

## 🧠 Implementation Details

### 🔄 Caching

- Uses an LRU-like LinkedHashMap limited by maxCacheSize.

- Entries expire after ttlSeconds.

- When in POLLING mode, cached cities are periodically refreshed in the background.

### 🌐 API Integration

- Step 1: Fetch city coordinates via /geo/1.0/direct

- Step 2: Fetch weather data via /data/2.5/weather

- Includes retry logic (up to 3 attempts) with exponential delay.

### 🧵 Thread Safety

- Uses ReadWriteLock for concurrent access to the cache.

- Asynchronous updates run in a separate thread pool.

- Proper shutdown implemented via close() and OpenWeatherSdkFactory.shutdown().

---

## 🧪 Example JSON output

```json
{
        "weather": {
        "main": "Clouds",
        "description": "overcast clouds"
        },
        "temperature": {
        "temp": 282.55,
        "feels_like": 281.86
        },
        "visibility": 10000,
        "wind": {
        "speed": 3.6
        },
        "datetime": 1730976000,
        "sys": {
        "sunrise": 1730930000,
        "sunset": 1730964000
        },
        "timezone": 3600,
        "name": "London"
        }

```

---

## 🧩 Configuration Parameters

| Parameter                | Description                               | Default             |
| ------------------------ | ----------------------------------------- | ------------------- |
| `mode`                   | Operation mode (`ON_DEMAND` or `POLLING`) | `ON_DEMAND`         |
| `ttlSeconds`             | Cache Time-To-Live (in seconds)           | `600`               |
| `pollingIntervalSeconds` | Polling interval (in seconds)             | `600`               |
| `maxCacheSize`           | Maximum cached cities                     | `10`                |
| `threadPoolSize`         | Number of async worker threads            | `min(4, CPU cores)` |

---

## 🏭 Factory Methods

| Method                                    | Description                     |
| ----------------------------------------- | ------------------------------- |
| `create(String apiKey)`                   | Creates SDK with default config |
| `create(String apiKey, SdkConfig config)` | Creates SDK with custom config  |
| `get(String apiKey)`                      | Returns existing instance       |
| `delete(String apiKey)`                   | Deletes and closes instance     |
| `shutdown()`                              | Closes all SDK instances        |

---

## 🧩 SDK Modes

| Mode        | Behavior                                            |
| ----------- | --------------------------------------------------- |
| `ON_DEMAND` | Fetches data directly from API when requested       |
| `POLLING`   | Keeps cached data fresh by periodically polling API |

---

## 👨‍💻 Author

**Amin Anaroghlu**

📧 Email: mamedov.amin0910@gmail.com

💼 GitHub: https://github.com/Voodoo0910
