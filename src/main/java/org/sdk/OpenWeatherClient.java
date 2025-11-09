package org.sdk;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

class OpenWeatherClient {

    private static final String OWM_WEATHER_ENDPOINT =
            "https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s";
    private static final String OWM_GEOCODING_ENDPOINT =
            "https://api.openweathermap.org/geo/1.0/direct?q=%s&limit=1&appid=%s";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_RETRIES = 3;
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient httpClient;
    private final String apiKey;

    OpenWeatherClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(REQUEST_TIMEOUT)
                .build();
    }

    String fetchWeatherFromApi(String city) throws IOException, InterruptedException {
        CityInfoResponse cityResponse = fetchCityInfoFromApi(city);

        URI uri = URI.create(String.format(
                OWM_WEATHER_ENDPOINT,
                cityResponse.lat(),
                cityResponse.lon(),
                apiKey
        ));

        HttpResponse<String> resp = sendWithRetry(uri);

        if (resp.statusCode() != 200) {
            throw new SdkException("OpenWeather API returned status " + resp.statusCode()
                    + " for /data/2.5/weather : " + truncate(resp.body()));
        }

        WeatherResponse weatherResponse = MAPPER.readValue(resp.body(), WeatherResponse.class);
        return mapWeatherResponseToJsonString(weatherResponse);
    }

    private CityInfoResponse fetchCityInfoFromApi(String cityName)
            throws SdkException, InterruptedException {

        String encodedCity = URLEncoder.encode(cityName, StandardCharsets.UTF_8);
        URI geoCodingUri = URI.create(String.format(OWM_GEOCODING_ENDPOINT, encodedCity, apiKey));

        HttpResponse<String> cityInfoResponse = sendWithRetry(geoCodingUri);

        if (cityInfoResponse.statusCode() != 200) {
            throw new SdkException("OpenWeather API returned status " +
                    cityInfoResponse.statusCode() + " for /geo/1.0/direct : " +
                    truncate(cityInfoResponse.body()));
        }

        CityInfoResponse[] cities = MAPPER.readValue(cityInfoResponse.body(), CityInfoResponse[].class);

        if (cities.length == 0) {
            throw new SdkException("No cities were found with name '" + cityName + "'");
        }

        return cities[0];
    }

    private HttpResponse<String> sendWithRetry(URI uri) throws SdkException, InterruptedException {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .GET()
                        .uri(uri)
                        .timeout(REQUEST_TIMEOUT)
                        .build();

                HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int code = resp.statusCode();

                if (code >= 400 && code < 500) {
                    throw new SdkException("Client error (" + code + ") from OpenWeather API: " + truncate(resp.body()));
                }

                if (code >= 500 && attempt + 1 < MAX_RETRIES) {
                    sleepBackoff(attempt);
                    continue;
                }

                return resp;

            } catch (IOException e) {
                if (attempt + 1 < MAX_RETRIES) {
                    sleepBackoff(attempt);
                    continue;
                }
                throw new SdkException("Network error when calling OpenWeather API: " + e.getMessage(), e);
            }
        }

        throw new SdkException("Failed to get response from OpenWeather API after " + MAX_RETRIES + " attempts");
    }

    private static void sleepBackoff(int attempt) throws InterruptedException {
        long delaySeconds = (long) Math.pow(2, attempt);
        Thread.sleep(delaySeconds * 1000L);
    }

    private String mapWeatherResponseToJsonString(WeatherResponse weatherResponse) {
        ObjectNode out = MAPPER.createObjectNode();

        if (weatherResponse.weather() != null && !weatherResponse.weather().isEmpty()) {
            var firstElement = weatherResponse.weather().getFirst();
            ObjectNode weather = MAPPER.createObjectNode();
            weather.put("main", firstElement.main());
            weather.put("description", firstElement.description());
            out.set("weather", weather);
        }

        if (weatherResponse.main() != null) {
            ObjectNode temp = MAPPER.createObjectNode();
            temp.put("temp", weatherResponse.main().temp());
            temp.put("feels_like", weatherResponse.main().feels_like());
            out.set("temperature", temp);
        }

        out.put("visibility", weatherResponse.visibility());

        if (weatherResponse.wind() != null) {
            ObjectNode wind = MAPPER.createObjectNode();
            wind.put("speed", weatherResponse.wind().speed());
            out.set("wind", wind);
        }

        out.put("datetime", weatherResponse.dt());

        if (weatherResponse.sys() != null) {
            ObjectNode sys = MAPPER.createObjectNode();
            sys.put("sunrise", weatherResponse.sys().sunrise());
            sys.put("sunset", weatherResponse.sys().sunset());
            out.set("sys", sys);
        }

        out.put("timezone", weatherResponse.timezone());
        out.put("name", weatherResponse.name());

        return MAPPER.writeValueAsString(out);
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 512 ? s : s.substring(0, 512) + "...";
    }

}