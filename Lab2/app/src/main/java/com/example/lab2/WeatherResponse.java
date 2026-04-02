package com.example.lab2;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

public class WeatherResponse {
    @SerializedName("latitude")
    public double latitude;
    @SerializedName("longitude")
    public double longitude;
    @SerializedName("timezone")
    public String timezone;

    @SerializedName("current_weather")
    public CurrentWeather currentWeather;

    @SerializedName("hourly")
    public HourlyData rawHourly;

    @SerializedName("daily")
    public DailyData rawDaily;

    public static class CurrentWeather {
        @SerializedName("temperature")
        public double temperature;
        @SerializedName("weathercode")
        public int weatherCode;
        @SerializedName("windspeed")
        public double windSpeed;
    }

    public static class HourlyData {
        @SerializedName("time")
        public List<String> time;
        @SerializedName("temperature_2m")
        public List<Double> temperatures;
        @SerializedName("weathercode")
        public List<Integer> weatherCodes;
        @SerializedName("precipitation_probability")
        public List<Double> precipitationProbability;
        @SerializedName("cloudcover")
        public List<Double> cloudCover;
    }

    public static class DailyData {
        @SerializedName("time")
        public List<String> time;
        @SerializedName("temperature_2m_max")
        public List<Double> tempMax;
        @SerializedName("temperature_2m_min")
        public List<Double> tempMin;
        @SerializedName("weathercode")
        public List<Integer> weatherCodes;
        @SerializedName("precipitation_probability_max")
        public List<Double> precipitationProbabilityMax;
    }

    // Helper classes for Adapters
    public static class HourlyItem {
        public String time;
        public double temp;
        public int code;
        public double precipitationProbability;
        public double cloudCover;

        public HourlyItem(String t, double tmp, int c, double precipitation, double cloud) {
            time = t;
            temp = tmp;
            code = c;
            precipitationProbability = precipitation;
            cloudCover = cloud;
        }
    }

    public static class DailyItem {
        public String date;
        public double max;
        public double min;
        public int code;
        public double precipitationProbabilityMax;

        public DailyItem(String d, double mx, double mn, int c, double precipitation) {
            date = d;
            max = mx;
            min = mn;
            code = c;
            precipitationProbabilityMax = precipitation;
        }
    }

    public List<HourlyItem> getHourlyItems() {
        List<HourlyItem> items = new ArrayList<>();
        if (rawHourly != null && rawHourly.time != null) {
            for (int i = 0; i < rawHourly.time.size(); i++) {
                double precipitation = getDoubleValue(rawHourly.precipitationProbability, i);
                double cloud = getDoubleValue(rawHourly.cloudCover, i);
                items.add(new HourlyItem(
                        rawHourly.time.get(i),
                        rawHourly.temperatures.get(i),
                        rawHourly.weatherCodes.get(i),
                        precipitation,
                        cloud
                ));
            }
        }
        return items;
    }

    public List<DailyItem> getDailyItems() {
        List<DailyItem> items = new ArrayList<>();
        if (rawDaily != null && rawDaily.time != null) {
            for (int i = 0; i < rawDaily.time.size(); i++) {
                double precipitation = getDoubleValue(rawDaily.precipitationProbabilityMax, i);
                items.add(new DailyItem(
                        rawDaily.time.get(i),
                        rawDaily.tempMax.get(i),
                        rawDaily.tempMin.get(i),
                        rawDaily.weatherCodes.get(i),
                        precipitation
                ));
            }
        }
        return items;
    }

    private double getDoubleValue(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size() || values.get(index) == null) {
            return 0;
        }
        return values.get(index);
    }
    
    public static String getWeatherIcon(int code) {
        // Mapping Open-Meteo codes to icons (simplified)
        if (code == 0) return "01d"; // Clear
        if (code <= 3) return "02d"; // Partly cloudy
        if (code <= 48) return "50d"; // Fog
        if (code <= 67) return "10d"; // Rain
        if (code <= 77) return "13d"; // Snow
        if (code <= 82) return "09d"; // Showers
        if (code <= 99) return "11d"; // Thunderstorm
        return "03d";
    }
}
