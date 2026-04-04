package com.example.lab2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.Circle;
import com.google.android.gms.maps.model.CircleOptions;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private enum LayerType {
        TEMPERATURE,
        RAIN,
        CLOUD
    }

    private GoogleMap mMap;
    private Marker centerMarker;
    private final List<Circle> weatherCircles = new ArrayList<>();
    private WeatherApiService weatherApiService;
    private Button btnTempLayer;
    private Button btnRainLayer;
    private Button btnCloudLayer;
    private Button btnBack;
    private LayerType selectedLayer = LayerType.TEMPERATURE;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_map);

        btnTempLayer = findViewById(R.id.btnTempLayer);
        btnRainLayer = findViewById(R.id.btnRainLayer);
        btnCloudLayer = findViewById(R.id.btnCloudLayer);
        btnBack = findViewById(R.id.btnBack);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherApiService = retrofit.create(WeatherApiService.class);

        btnTempLayer.setOnClickListener(v -> switchLayer(LayerType.TEMPERATURE));
        btnRainLayer.setOnClickListener(v -> switchLayer(LayerType.RAIN));
        btnCloudLayer.setOnClickListener(v -> switchLayer(LayerType.CLOUD));
        
        btnBack.setOnClickListener(v -> finish());

        updateLayerButtons();

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        double startLat = getIntent().getDoubleExtra("lat", 21.02);
        double startLon = getIntent().getDoubleExtra("lon", 105.83);
        LatLng startPoint = new LatLng(startLat, startLon);

        centerMarker = mMap.addMarker(new MarkerOptions()
                .position(startPoint)
                .title("Đang tải dữ liệu thời tiết..."));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 9.8f));
        mMap.getUiSettings().setZoomControlsEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMapClickListener(latLng -> {
            if (centerMarker != null) {
                centerMarker.setPosition(latLng);
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
            fetchWeatherForMap(latLng.latitude, latLng.longitude);
            renderWeatherLayer(latLng);
        });

        mMap.setOnCameraIdleListener(() -> {
            LatLng center = mMap.getCameraPosition().target;
            if (centerMarker != null) {
                centerMarker.setPosition(center);
            }
            fetchWeatherForMap(center.latitude, center.longitude);
            renderWeatherLayer(center);
        });

        fetchWeatherForMap(startLat, startLon);
        renderWeatherLayer(startPoint);
    }

    private void switchLayer(LayerType layerType) {
        selectedLayer = layerType;
        updateLayerButtons();
        if (mMap != null) {
            renderWeatherLayer(mMap.getCameraPosition().target);
        }
    }

    private void updateLayerButtons() {
        styleLayerButton(btnTempLayer, selectedLayer == LayerType.TEMPERATURE);
        styleLayerButton(btnRainLayer, selectedLayer == LayerType.RAIN);
        styleLayerButton(btnCloudLayer, selectedLayer == LayerType.CLOUD);
    }

    private void styleLayerButton(Button button, boolean selected) {
        int backgroundColor = selected ? Color.parseColor("#1565C0") : Color.WHITE;
        int textColor = selected ? Color.WHITE : Color.parseColor("#1F2937");
        button.setBackgroundColor(backgroundColor);
        button.setTextColor(textColor);
    }

    private void renderWeatherLayer(LatLng center) {
        clearWeatherLayer();

        double step = getGridStep();
        double radius = getCircleRadiusMeters();

        for (int row = -1; row <= 1; row++) {
            for (int col = -1; col <= 1; col++) {
                double sampleLat = center.latitude + (row * step);
                double sampleLon = center.longitude + (col * step);
                requestLayerSample(sampleLat, sampleLon, radius);
            }
        }
    }

    private void requestLayerSample(double lat, double lon, double radius) {
        weatherApiService.getForecast(
                lat,
                lon,
                true,
                "temperature_2m,weathercode,precipitation_probability,cloudcover",
                "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max",
                "auto"
        ).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (!response.isSuccessful() || response.body() == null || mMap == null) {
                    return;
                }

                WeatherResponse data = response.body();
                if (data.rawHourly == null || data.rawHourly.temperatures == null || data.rawHourly.temperatures.isEmpty()) {
                    return;
                }

                double temperature = getFirstValue(data.rawHourly.temperatures);
                double rainProbability = getFirstValue(data.rawHourly.precipitationProbability);
                double cloudCover = getFirstValue(data.rawHourly.cloudCover);

                Circle circle = mMap.addCircle(new CircleOptions()
                        .center(new LatLng(lat, lon))
                        .radius(radius)
                        .strokeWidth(1.5f)
                        .strokeColor(getStrokeColor())
                        .fillColor(getFillColor(temperature, rainProbability, cloudCover)));
                weatherCircles.add(circle);
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
            }
        });
    }

    private void fetchWeatherForMap(double lat, double lon) {
        weatherApiService.getForecast(
                lat,
                lon,
                true,
                "temperature_2m,weathercode,precipitation_probability,cloudcover",
                "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max",
                "auto"
        ).enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null && centerMarker != null) {
                    WeatherResponse data = response.body();
                    double rainProbability = getFirstValue(data.rawHourly != null ? data.rawHourly.precipitationProbability : null);
                    double cloudCover = getFirstValue(data.rawHourly != null ? data.rawHourly.cloudCover : null);

                    centerMarker.setTitle(getWeatherStatus(data.currentWeather.weatherCode) + " "
                            + String.format(Locale.getDefault(), "%.1f°C", data.currentWeather.temperature));
                    centerMarker.setSnippet(String.format(
                            Locale.getDefault(),
                            "Gió: %.1f km/h | Mưa: %.0f%% | Mây: %.0f%% | Tọa độ: %.2f, %.2f",
                            data.currentWeather.windSpeed,
                            rainProbability,
                            cloudCover,
                            lat,
                            lon
                    ));
                    centerMarker.showInfoWindow();
                }
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
            }
        });
    }

    private void clearWeatherLayer() {
        for (Circle circle : weatherCircles) {
            circle.remove();
        }
        weatherCircles.clear();
    }

    private double getGridStep() {
        if (mMap == null) {
            return 0.25;
        }
        float zoom = mMap.getCameraPosition().zoom;
        double rawStep = 18.0 / Math.pow(2, zoom);
        return Math.max(0.08, Math.min(0.35, rawStep));
    }

    private double getCircleRadiusMeters() {
        if (mMap == null) {
            return 12000;
        }
        float zoom = mMap.getCameraPosition().zoom;
        double rawRadius = 250000 / Math.pow(2, zoom - 3);
        return Math.max(5000, Math.min(22000, rawRadius));
    }

    private double getFirstValue(List<Double> values) {
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0;
        }
        return values.get(0);
    }

    private int getStrokeColor() {
        switch (selectedLayer) {
            case RAIN:
                return Color.parseColor("#1565C0");
            case CLOUD:
                return Color.parseColor("#546E7A");
            case TEMPERATURE:
            default:
                return Color.parseColor("#E65100");
        }
    }

    private int getFillColor(double temperature, double rainProbability, double cloudCover) {
        switch (selectedLayer) {
            case RAIN:
                return getRainColor(rainProbability);
            case CLOUD:
                return getCloudColor(cloudCover);
            case TEMPERATURE:
            default:
                return getTemperatureColor(temperature);
        }
    }

    private int getTemperatureColor(double temperature) {
        if (temperature >= 35) return Color.argb(135, 198, 40, 40);
        if (temperature >= 30) return Color.argb(120, 239, 108, 0);
        if (temperature >= 25) return Color.argb(110, 255, 167, 38);
        if (temperature >= 20) return Color.argb(105, 255, 213, 79);
        return Color.argb(105, 79, 195, 247);
    }

    private int getRainColor(double rainProbability) {
        if (rainProbability >= 80) return Color.argb(150, 13, 71, 161);
        if (rainProbability >= 60) return Color.argb(130, 30, 136, 229);
        if (rainProbability >= 40) return Color.argb(115, 79, 195, 247);
        if (rainProbability >= 20) return Color.argb(95, 144, 202, 249);
        return Color.argb(70, 227, 242, 253);
    }

    private int getCloudColor(double cloudCover) {
        if (cloudCover >= 85) return Color.argb(145, 84, 110, 122);
        if (cloudCover >= 60) return Color.argb(125, 120, 144, 156);
        if (cloudCover >= 35) return Color.argb(100, 176, 190, 197);
        return Color.argb(70, 236, 239, 241);
    }

    private String getWeatherStatus(int code) {
        if (code == 0) return "Trời quang";
        if (code <= 3) return "Có mây";
        if (code >= 45 && code <= 48) return "Sương mù";
        if (code >= 51 && code <= 67) return "Đang mưa";
        if (code >= 80 && code <= 82) return "Mưa rào";
        if (code >= 95) return "Dông bão";
        return "Thời tiết";
    }
}
