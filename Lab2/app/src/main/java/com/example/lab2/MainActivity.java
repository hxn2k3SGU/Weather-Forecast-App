package com.example.lab2;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.os.Build;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.example.lab2.databinding.ActivityMainBinding;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private FusedLocationProviderClient fusedLocationClient;
    private static final int PERMISSION_REQUEST_CODE = 1001;
    private static final String CHANNEL_ID = "weather_alerts";
    
    // Lưu tọa độ hiện tại
    private double currentLat = 21.02; // Mặc định HN
    private double currentLon = 105.83;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        if (binding.toolbar != null) {
            setSupportActionBar(binding.toolbar);
        }

        createNotificationChannel();
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        checkAndRequestPermissions();

        binding.btnViewMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WeatherMapActivity.class);
            // TRUYỀN TỌA ĐỘ SANG ACTIVITY BẢN ĐỒ
            intent.putExtra("lat", currentLat);
            intent.putExtra("lon", currentLon);
            startActivity(intent);
        });
    }

    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            getLastLocation();
        }
    }

    private void getLastLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.getCurrentLocation(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(this, location -> {
                        if (location != null) {
                            currentLat = location.getLatitude();
                            currentLon = location.getLongitude();
                            fetchWeatherData(currentLat, currentLon);
                        } else {
                            Toast.makeText(this, "Location null. Please enable GPS.", Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void fetchWeatherData(double lat, double lon) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        WeatherApiService service = retrofit.create(WeatherApiService.class);
        service.getForecast(
                lat,
                lon,
                true,
                "temperature_2m,weathercode,precipitation_probability,cloudcover",
                "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max",
                "auto"
        )
                .enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    updateUI(response.body());
                    checkWeatherAlerts(response.body());
                }
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(WeatherResponse weather) {
        String cityName = String.format(Locale.getDefault(), "Lat: %.2f, Lon: %.2f", weather.latitude, weather.longitude);
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            List<Address> addresses = geocoder.getFromLocation(weather.latitude, weather.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String locality = addr.getLocality();
                String adminArea = addr.getAdminArea();
                
                if (locality != null) cityName = locality;
                else if (adminArea != null) cityName = adminArea;
                else if (addr.getCountryName() != null) cityName = addr.getCountryName();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        binding.tvLocation.setText(cityName);
        binding.tvLocation.setTextColor(android.graphics.Color.BLACK);
        
        binding.tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.1f°C", weather.currentWeather.temperature));
        binding.tvCurrentTemp.setTextColor(android.graphics.Color.BLACK);

        binding.tvWeatherDescription.setText(getWeatherDescription(weather.currentWeather.weatherCode));
        binding.tvWeatherDescription.setTextColor(android.graphics.Color.DKGRAY);

        binding.rvHourly.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.rvHourly.setAdapter(new HourlyAdapter(weather.getHourlyItems()));

        binding.rvDaily.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDaily.setAdapter(new DailyAdapter(weather.getDailyItems()));
    }

    private String getWeatherDescription(int code) {
        switch (code) {
            case 0: return "Trời quang";
            case 1: case 2: case 3: return "Mây rải rác";
            case 45: case 48: return "Sương mù";
            case 51: case 53: case 55: return "Mưa phùn";
            case 61: case 63: case 65: return "Mưa nhẹ";
            case 71: case 73: case 75: return "Tuyết rơi";
            case 80: case 81: case 82: return "Mưa rào";
            case 95: case 96: case 99: return "Dông bão";
            default: return "Thời tiết khác (" + code + ")";
        }
    }

    private void checkWeatherAlerts(WeatherResponse weather) {
        if (weather.currentWeather.temperature > 35) {
            sendNotification("Extreme Heat", "Temperature is over 35°C!");
        } else if (weather.currentWeather.weatherCode >= 80) {
            sendNotification("Rain Alert", "Heavy rain detected!");
        }
    }

    private void sendNotification(String title, String message) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(1, builder.build());
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Weather Alerts", NotificationManager.IMPORTANCE_HIGH);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            getLastLocation();
        }
    }
}
