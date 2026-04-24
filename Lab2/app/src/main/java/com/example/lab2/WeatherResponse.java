package com.example.lab2;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/**
 * WeatherResponse - Lop model du lieu tu API Open-Meteo
 * Chua du lieu thoi tiet tra ve tu API
 * Chuyen doi JSON response thanh Java objects (via Gson)
 */
public class WeatherResponse {
    @SerializedName("latitude")
    public double latitude; // Vi do cua vi tri
    @SerializedName("longitude")
    public double longitude; // Kinh do cua vi tri
    @SerializedName("timezone")
    public String timezone; // Mui gio cua vi tri

    // Thoi tiet hien tai (lay ngay tu API)
    @SerializedName("current_weather")
    public CurrentWeather currentWeather;

    // Du lieu theo gio (24 gio tiep theo)
    @SerializedName("hourly")
    public HourlyData rawHourly;

    // Du lieu hang ngay (7 ngay tiep theo)
    @SerializedName("daily")
    public DailyData rawDaily;

    /**
     * CurrentWeather - Thoi tiet hien tai
     * Don vi nen: Celsius hoac Fahrenheit (tuy theo request)
     * Don vi gio: km/h
     */
    public static class CurrentWeather {
        @SerializedName("temperature")
        public double temperature; // Nhiet do (C hoac F)
        @SerializedName("weathercode")
        public int weatherCode; // Ma WMO (0=Trong, 1-3=May, 45-48=Suong, 51-67=Mua, 80-82=Mua rao, 95+=Dong)
        @SerializedName("windspeed")
        public double windSpeed; // Toc do gio 10m (km/h)
    }

    /**
     * HourlyData - Du lieu thoi tiet theo gio (24 gio)
     * Moi field la mot list voi 24 phan tu (1 gio = 1 phan tu)
     */
    public static class HourlyData {
        @SerializedName("time")
        public List<String> time; // Thoi gian: format "2023-10-27T00:00"
        @SerializedName("temperature_2m")
        public List<Double> temperatures; // Nhiet do 2m (C hoac F)
        @SerializedName("weathercode")
        public List<Integer> weatherCodes; // Ma WMO cho moi gio
        @SerializedName("precipitation_probability")
        public List<Double> precipitationProbability; // Xac suat mua (%)
        @SerializedName("cloudcover")
        public List<Double> cloudCover; // Do che phu may (%)
        @SerializedName("relative_humidity_2m")
        public List<Integer> relativeHumidity; // Do am tuong doi 2m (%)
        @SerializedName("windspeed_10m")
        public List<Double> windSpeed; // Toc do gio 10m (km/h)
    }

    /**
     * DailyData - Du lieu thoi tiet theo ngay (7 ngay)
     * Moi field la mot list voi 7 phan tu (1 ngay = 1 phan tu)
     */
    public static class DailyData {
        @SerializedName("time")
        public List<String> time; // Ngay: format "2023-10-27"
        @SerializedName("temperature_2m_max")
        public List<Double> tempMax; // Nhiet do cao nhat trong ngay (C hoac F)
        @SerializedName("temperature_2m_min")
        public List<Double> tempMin; // Nhiet do thap nhat trong ngay (C hoac F)
        @SerializedName("weathercode")
        public List<Integer> weatherCodes; // Ma WMO cho moi ngay
        @SerializedName("precipitation_probability_max")
        public List<Double> precipitationProbabilityMax; // Xac suat mua cao nhat (%)
        @SerializedName("relative_humidity_2m_max")
        public List<Integer> relativeHumidityMax; // Do am cao nhat (%)
        @SerializedName("windspeed_10m_max")
        public List<Double> windSpeedMax; // Toc do gio cao nhat (km/h)
    }

    /**
     * HourlyItem - Helper class de luu tru du lieu 1 gio
     * Dung cho HourlyAdapter hien thi du bao theo gio
     */
    public static class HourlyItem {
        public String time; // Thoi gian "HH:MM"
        public double temp; // Nhiet do
        public int code; // Ma WMO
        public double precipitationProbability; // Xac suat mua
        public double cloudCover; // Do che phu may
        public int humidity; // Do am
        public double windSpeed; // Toc do gio

        public HourlyItem(String t, double tmp, int c, double precipitation, double cloud, int h, double w) {
            time = t;
            temp = tmp;
            code = c;
            precipitationProbability = precipitation;
            cloudCover = cloud;
            humidity = h;
            windSpeed = w;
        }
    }

    /**
     * DailyItem - Helper class de luu tru du lieu 1 ngay
     * Dung cho DailyAdapter hien thi du bao 7 ngay
     */
    public static class DailyItem {
        public String date; // Ngay dinh dang "2023-10-27"
        public double max; // Nhiet do max cua ngay
        public double min; // Nhiet do min cua ngay
        public int code; // Ma WMO
        public double precipitationProbabilityMax; // Xac suat mua cao nhat
        public int humidityMax; // Do am cao nhat
        public double windSpeedMax; // Toc do gio cao nhat

        public DailyItem(String d, double mx, double mn, int c, double precipitation, int h, double w) {
            date = d;
            max = mx;
            min = mn;
            code = c;
            precipitationProbabilityMax = precipitation;
            humidityMax = h;
            windSpeedMax = w;
        }
    }

    /**
     * getHourlyItems - Chuyen doi du lieu HourlyData thanh list HourlyItem
     * Dung de cap du lieu cho HourlyAdapter
     * @return List<HourlyItem> chua 24 gio du lieu
     */
    public List<HourlyItem> getHourlyItems() {
        List<HourlyItem> items = new ArrayList<>();
        if (rawHourly != null && rawHourly.time != null) {
            for (int i = 0; i < rawHourly.time.size(); i++) {
                double precipitation = getDoubleValue(rawHourly.precipitationProbability, i);
                double cloud = getDoubleValue(rawHourly.cloudCover, i);
                int humidity = getIntValue(rawHourly.relativeHumidity, i);
                double wind = getDoubleValue(rawHourly.windSpeed, i);
                items.add(new HourlyItem(
                        rawHourly.time.get(i),
                        rawHourly.temperatures.get(i),
                        rawHourly.weatherCodes.get(i),
                        precipitation,
                        cloud,
                        humidity,
                        wind
                ));
            }
        }
        return items;
    }

    /**
     * getDailyItems - Chuyen doi du lieu DailyData thanh list DailyItem
     * Dung de cap du lieu cho DailyAdapter
     * @return List<DailyItem> chua 7 ngay du lieu
     */
    public List<DailyItem> getDailyItems() {
        List<DailyItem> items = new ArrayList<>();
        if (rawDaily != null && rawDaily.time != null) {
            for (int i = 0; i < rawDaily.time.size(); i++) {
                double precipitation = getDoubleValue(rawDaily.precipitationProbabilityMax, i);
                int humidity = getIntValue(rawDaily.relativeHumidityMax, i);
                double wind = getDoubleValue(rawDaily.windSpeedMax, i);
                items.add(new DailyItem(
                        rawDaily.time.get(i),
                        rawDaily.tempMax.get(i),
                        rawDaily.tempMin.get(i),
                        rawDaily.weatherCodes.get(i),
                        precipitation,
                        humidity,
                        wind
                ));
            }
        }
        return items;
    }

    /**
     * getDoubleValue - Lay gia tri double tu list voi xu ly null/out-of-bounds
     * Tra ve 0 neu list null, index khong hop le, hoac gia tri null
     * @param values List can lay gia tri
     * @param index Chi so phan tu
     * @return Gia tri hoac 0 neu khong tim thay
     */
    private double getDoubleValue(List<Double> values, int index) {
        if (values == null || index < 0 || index >= values.size() || values.get(index) == null) {
            return 0;
        }
        return values.get(index);
    }

    /**
     * getIntValue - Lay gia tri integer tu list voi xu ly null/out-of-bounds
     * Tra ve 0 neu list null, index khong hop le, hoac gia tri null
     * @param values List can lay gia tri
     * @param index Chi so phan tu
     * @return Gia tri hoac 0 neu khong tim thay
     */
    private int getIntValue(List<Integer> values, int index) {
        if (values == null || index < 0 || index >= values.size() || values.get(index) == null) {
            return 0;
        }
        return values.get(index);
    }
    
    /**
     * getWeatherIcon - Chuyen doi ma WMO thanh ma icon OpenWeather
     * Dung de lay icon tu CDN OpenWeather API
     * @param code Ma WMO tu API
     * @return Ma icon OpenWeather (vd: "01d", "02d", ...)
     */
    public static String getWeatherIcon(int code) {
        // Mapping ma Open-Meteo WMO sang ma icon OpenWeather
        if (code == 0) return "01d"; // Troi quang
        if (code <= 3) return "02d"; // May rai rac
        if (code <= 48) return "50d"; // Suong mu
        if (code <= 67) return "10d"; // Mua
        if (code <= 77) return "13d"; // Tuyet
        if (code <= 82) return "09d"; // Mua rao
        if (code <= 99) return "11d"; // Dong bao
        return "03d"; // Mac dinh: may
    }
}
