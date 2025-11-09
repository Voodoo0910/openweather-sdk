package org.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
record WeatherResponse(
        List<Weather> weather,
        Main main,
        int visibility,
        Wind wind,
        long dt,
        Sys sys,
        int timezone,
        String name
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Weather(String main, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Main(double temp, double feels_like) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Wind(double speed) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Sys(long sunrise, long sunset) {
    }

}
