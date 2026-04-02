package com.example.lab2;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface WeatherApiService {
    // API Open-Meteo: Hoàn toàn miễn phí, không cần Key
    @GET("v1/forecast")
    Call<WeatherResponse> getForecast(
            @Query("latitude") double lat,
            @Query("longitude") double lon,
            @Query("current_weather") boolean current,
            @Query("hourly") String hourlyParams,
            @Query("daily") String dailyParams,
            @Query("timezone") String timezone
    );
}
