package com.example.lab2;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

/**
 * WeatherApiService - Retrofit interface de goi API Open-Meteo
 * 
 * API: Open-Meteo (https://api.open-meteo.com/)
 * - Hoang toan mien phi
 * - Khong can API key
 * - Tro cap du doan thoi tiet 7 ngay va thong tin realtime
 * 
 * Endpoint: GET /v1/forecast
 * 
 * Parameters:
 * - latitude, longitude: Toa do vi tri
 * - current_weather: Lay thoi tiet hien tai (boolean)
 * - hourly: Tieu chi du bao theo gio (string)
 * - daily: Tieu chi du bao hang ngay (string)
 * - timezone: Mui gio (auto = tu IP)
 * - temperature_unit: Celsius hoac Fahrenheit
 */
public interface WeatherApiService {
    /**
     * getForecast - Goi API Open-Meteo de lay du bao thoi tiet
     * 
     * @param lat Vi do
     * @param lon Kinh do
     * @param current true de lay thoi tiet hien tai
     * @param hourlyParams Cac tieu chi theo gio (comma-separated string)
     * @param dailyParams Cac tieu chi hang ngay (comma-separated string)
     * @param timezone Mui gio ("auto" = tu IP dia chi)
     * @param temperatureUnit "celsius" hoac "fahrenheit"
     * @return Call<WeatherResponse> - API response
     */
    // API Open-Meteo: Hoan toan mien phi, khong can Key
    @GET("v1/forecast")
    Call<WeatherResponse> getForecast(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current_weather") boolean current,
            @Query("hourly") String hourlyParams,
            @Query("daily") String dailyParams,
            @Query("timezone") String timezone,
            @Query("temperature_unit") String temperatureUnit
    );

    /**
     * getMapPointForecast - Goi forecast gon nhe cho 1 diem tren ban do.
     */
    @GET("v1/forecast")
    Call<WeatherResponse> getMapPointForecast(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current_weather") boolean current,
            @Query("hourly") String hourlyParams,
            @Query("timezone") String timezone,
            @Query("temperature_unit") String temperatureUnit,
            @Query("forecast_hours") int forecastHours
    );

    /**
     * getMapGridForecast - Goi forecast cho nhieu toa do trong cung 1 request.
     * Open-Meteo cho phep latitude/longitude truyen dang danh sach cach nhau boi dau phay.
     */
    @GET("v1/forecast")
    Call<java.util.List<WeatherResponse>> getMapGridForecast(
            @Query("latitude") String latitudes,
            @Query("longitude") String longitudes,
            @Query("current_weather") boolean current,
            @Query("hourly") String hourlyParams,
            @Query("timezone") String timezone,
            @Query("temperature_unit") String temperatureUnit,
            @Query("forecast_hours") int forecastHours
    );
}
