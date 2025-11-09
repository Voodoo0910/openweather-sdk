package org.sdk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record CityInfoResponse(String name, double lat, double lon, String country, String state) {
}
