package org.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OpenWeatherSdkTest {

    private OpenWeatherClient mockClient;
    private OpenWeatherSdk sdk;

    @BeforeEach
    void setUp() {
        mockClient = mock(OpenWeatherClient.class);

        SdkConfig config = SdkConfig.builder()
                .mode(SdkMode.ON_DEMAND)
                .ttlSeconds(5)
                .pollingIntervalSeconds(5)
                .maxCacheSize(3)
                .threadPoolSize(2)
                .build();

        sdk = new OpenWeatherSdk("test-key", config) {
            {
                try {
                    var field = OpenWeatherSdk.class.getDeclaredField("openWeatherClient");
                    field.setAccessible(true);
                    field.set(this, mockClient);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        };
    }

    @AfterEach
    void tearDown() {
        sdk.close();
    }

    @Test
    void getWeatherByCity_fetchesFromApi_whenNotInCache() throws Exception {
        when(mockClient.fetchWeatherFromApi("London"))
                .thenReturn("{\"weather\":{\"main\":\"Clouds\"}}");

        String result = sdk.getWeatherByCity("London");

        assertTrue(result.contains("Clouds"));
        assertEquals(1, sdk.getCacheSize());
        verify(mockClient, times(1)).fetchWeatherFromApi("London");
    }

    @Test
    void getWeatherByCity_returnsCached_whenNotExpired() throws Exception {
        when(mockClient.fetchWeatherFromApi("Paris"))
                .thenReturn("{\"weather\":{\"main\":\"Sunny\"}}");

        sdk.getWeatherByCity("Paris");
        String secondCall = sdk.getWeatherByCity("Paris");

        verify(mockClient, times(1)).fetchWeatherFromApi("Paris");
        assertEquals(1, sdk.getCacheSize());
        assertTrue(secondCall.contains("Sunny"));
    }

    @Test
    void getWeatherByCity_throwsWhenClosed() {
        sdk.close();
        SdkException ex = assertThrows(SdkException.class, () -> sdk.getWeatherByCity("Rome"));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void clearCache_emptiesCache() throws Exception {
        when(mockClient.fetchWeatherFromApi("Baku")).thenReturn("{\"weather\":\"Hot\"}");
        sdk.getWeatherByCity("Baku");
        assertEquals(1, sdk.getCacheSize());
        sdk.clearCache();
        assertEquals(0, sdk.getCacheSize());
    }

    @Test
    void deleteCity_removesFromCache() throws Exception {
        when(mockClient.fetchWeatherFromApi("Berlin")).thenReturn("{\"weather\":\"Cold\"}");
        sdk.getWeatherByCity("Berlin");
        assertEquals(1, sdk.getCacheSize());
        sdk.deleteCity("Berlin");
        assertEquals(0, sdk.getCacheSize());
    }

    @Test
    void getWeatherByCity_handlesIOException() throws Exception {
        when(mockClient.fetchWeatherFromApi("ErrorCity"))
                .thenThrow(new IOException("network down"));

        SdkException ex = assertThrows(SdkException.class, () -> sdk.getWeatherByCity("ErrorCity"));
        assertTrue(ex.getMessage().contains("I/O error"));
    }

}
