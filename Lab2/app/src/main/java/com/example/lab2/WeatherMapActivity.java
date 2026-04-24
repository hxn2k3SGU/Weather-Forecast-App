package com.example.lab2;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentActivity;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polygon;
import com.google.android.gms.maps.model.PolygonOptions;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class WeatherMapActivity extends FragmentActivity implements OnMapReadyCallback {

    private static final String MAP_POINT_HOURLY_PARAMS =
            "temperature_2m,weathercode,precipitation_probability,cloudcover,windspeed_10m";
    private static final String MAP_GRID_HOURLY_PARAMS =
            "temperature_2m,precipitation_probability,cloudcover";
    private static final int FORECAST_HOURS = 1;
    private static final int CAMERA_IDLE_DEBOUNCE_MS = 320;
    private static final int MAX_GRID_POINTS = 132;
    private static final double VIEWPORT_PADDING_RATIO = 0.12;

    public enum MapMode {
        TEMPERATURE,
        CLOUD_COVER,
        PRECIPITATION
    }

    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = this::refreshMapFromCamera;

    private GoogleMap mMap;
    private Marker centerMarker;
    private WeatherApiService weatherApiService;
    private Call<WeatherResponse> markerWeatherCall;
    private Call<List<WeatherResponse>> gridWeatherCall;

    private Button btnBack;
    private Button btnModeTemperature;
    private Button btnModeCloudCover;
    private Button btnModePrecipitation;
    private TextView legendTitle;
    private TextView legendText;
    private View legendGradientBar;
    private final TextView[] legendValues = new TextView[6];

    private boolean isCelsius = true;
    private MapMode currentMode = MapMode.TEMPERATURE;
    private int gridRequestVersion = 0;
    private String legendStatusMessage = "Dang tai lop phu...";

    private final List<WeatherCell> weatherCells = new ArrayList<>();
    private final List<Polygon> weatherCellPolygons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_weather_map);

        isCelsius = getIntent().getBooleanExtra("isCelsius", true);

        btnBack = findViewById(R.id.btnBack);
        btnModeTemperature = findViewById(R.id.btnModeTemperature);
        btnModeCloudCover = findViewById(R.id.btnModeCloudCover);
        btnModePrecipitation = findViewById(R.id.btnModePrecipitation);
        legendTitle = findViewById(R.id.legendTitle);
        legendText = findViewById(R.id.legendText);
        legendGradientBar = findViewById(R.id.legendGradientBar);
        legendValues[0] = findViewById(R.id.legendValue1);
        legendValues[1] = findViewById(R.id.legendValue2);
        legendValues[2] = findViewById(R.id.legendValue3);
        legendValues[3] = findViewById(R.id.legendValue4);
        legendValues[4] = findViewById(R.id.legendValue5);
        legendValues[5] = findViewById(R.id.legendValue6);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        weatherApiService = retrofit.create(WeatherApiService.class);

        btnBack.setOnClickListener(v -> finish());
        btnModeTemperature.setOnClickListener(v -> switchMode(MapMode.TEMPERATURE));
        btnModeCloudCover.setOnClickListener(v -> switchMode(MapMode.CLOUD_COVER));
        btnModePrecipitation.setOnClickListener(v -> switchMode(MapMode.PRECIPITATION));

        updateModeUi();

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
                .title("Dang tai du lieu thoi tiet..."));

        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(startPoint, 7.2f));
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.getUiSettings().setCompassEnabled(true);

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMapClickListener(latLng -> {
            if (centerMarker != null) {
                centerMarker.setPosition(latLng);
            }
            mMap.animateCamera(CameraUpdateFactory.newLatLng(latLng));
        });

        mMap.setOnCameraIdleListener(this::scheduleCameraRefresh);
        scheduleCameraRefresh();
    }

    @Override
    protected void onDestroy() {
        refreshHandler.removeCallbacksAndMessages(null);
        if (markerWeatherCall != null) {
            markerWeatherCall.cancel();
        }
        if (gridWeatherCall != null) {
            gridWeatherCall.cancel();
        }
        super.onDestroy();
    }

    private void switchMode(MapMode newMode) {
        if (currentMode == newMode) {
            return;
        }
        currentMode = newMode;
        updateModeUi();
        if (hasWeatherCells()) {
            renderWeatherOverlay();
        } else {
            scheduleCameraRefresh();
        }
    }

    private void updateModeUi() {
        applyModeButtonStyle(btnModeTemperature, currentMode == MapMode.TEMPERATURE, Color.parseColor("#D96A3A"));
        applyModeButtonStyle(btnModeCloudCover, currentMode == MapMode.CLOUD_COVER, Color.parseColor("#5E89C6"));
        applyModeButtonStyle(btnModePrecipitation, currentMode == MapMode.PRECIPITATION, Color.parseColor("#3576D2"));
        updateLegendUi();
    }

    private void applyModeButtonStyle(Button button, boolean active, int activeColor) {
        if (button.getBackground() != null) {
            button.getBackground().mutate().setTint(active ? activeColor : Color.parseColor("#66444C35"));
        }
        button.setTextColor(active ? Color.WHITE : Color.parseColor("#EAF4E5"));
        button.setAlpha(active ? 1f : 0.88f);
    }

    private void updateLegendUi() {
        legendTitle.setText(getLegendTitle());
        legendText.setText(legendStatusMessage);

        String[] labels = getLegendLabels();
        for (int i = 0; i < legendValues.length; i++) {
            legendValues[i].setText(labels[i]);
        }

        GradientDrawable drawable = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                getLegendBarColors());
        drawable.setCornerRadius(dpToPx(999));
        legendGradientBar.setBackground(drawable);
    }

    private String getLegendTitle() {
        switch (currentMode) {
            case CLOUD_COVER:
                return "May";
            case PRECIPITATION:
                return "Mua";
            case TEMPERATURE:
            default:
                return isCelsius ? "Nhiet do (C)" : "Nhiet do (F)";
        }
    }

    private String[] getLegendLabels() {
        switch (currentMode) {
            case CLOUD_COVER:
                return new String[]{"100%", "80%", "60%", "40%", "20%", "0%"};
            case PRECIPITATION:
                return new String[]{"100%", "80%", "60%", "40%", "20%", "0%"};
            case TEMPERATURE:
            default:
                if (isCelsius) {
                    return new String[]{"50", "40", "30", "20", "10", "-10"};
                }
                return new String[]{"122", "104", "86", "68", "50", "14"};
        }
    }

    private int[] getLegendBarColors() {
        switch (currentMode) {
            case CLOUD_COVER:
                return reverseColors(new int[]{
                        Color.parseColor("#425A75"),
                        Color.parseColor("#6D89A7"),
                        Color.parseColor("#96B0C4"),
                        Color.parseColor("#BDD0DE"),
                        Color.parseColor("#DCE8F2")
                });
            case PRECIPITATION:
                return reverseColors(new int[]{
                        Color.parseColor("#0B2D6E"),
                        Color.parseColor("#1555A6"),
                        Color.parseColor("#2886D8"),
                        Color.parseColor("#5FB8F2"),
                        Color.parseColor("#BEEBFF")
                });
            case TEMPERATURE:
            default:
                return reverseColors(new int[]{
                        Color.parseColor("#43238F"),
                        Color.parseColor("#2A68D8"),
                        Color.parseColor("#40BFC9"),
                        Color.parseColor("#DCCF58"),
                        Color.parseColor("#F19A35"),
                        Color.parseColor("#D64A2D")
                });
        }
    }

    private void scheduleCameraRefresh() {
        refreshHandler.removeCallbacks(refreshRunnable);
        refreshHandler.postDelayed(refreshRunnable, CAMERA_IDLE_DEBOUNCE_MS);
    }

    private void refreshMapFromCamera() {
        if (mMap == null) {
            return;
        }
        LatLng center = mMap.getCameraPosition().target;
        if (centerMarker != null) {
            centerMarker.setPosition(center);
        }
        fetchWeatherForMarker(center.latitude, center.longitude);
        fetchViewportOverlayData();
    }

    private void fetchWeatherForMarker(double lat, double lon) {
        if (markerWeatherCall != null) {
            markerWeatherCall.cancel();
        }

        markerWeatherCall = weatherApiService.getMapPointForecast(
                lat,
                lon,
                true,
                MAP_POINT_HOURLY_PARAMS,
                "auto",
                isCelsius ? "celsius" : "fahrenheit",
                FORECAST_HOURS
        );

        markerWeatherCall.enqueue(new Callback<WeatherResponse>() {
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (call.isCanceled() || !response.isSuccessful() || response.body() == null || centerMarker == null) {
                    return;
                }

                WeatherResponse data = response.body();
                if (data.currentWeather == null) {
                    return;
                }

                double rainProbability = getFirstValue(data.rawHourly != null ? data.rawHourly.precipitationProbability : null);
                double cloudCover = getFirstValue(data.rawHourly != null ? data.rawHourly.cloudCover : null);
                String tempUnit = isCelsius ? "C" : "F";

                centerMarker.setTitle(getWeatherStatus(data.currentWeather.weatherCode) + " "
                        + String.format(Locale.getDefault(), "%.1f %s", data.currentWeather.temperature, tempUnit));
                centerMarker.setSnippet(String.format(
                        Locale.getDefault(),
                        "Gio %.1f km/h | Mua %.0f%% | May %.0f%% | %.2f, %.2f",
                        data.currentWeather.windSpeed,
                        rainProbability,
                        cloudCover,
                        lat,
                        lon
                ));
                centerMarker.showInfoWindow();
            }

            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                if (!call.isCanceled()) {
                    android.util.Log.e("WeatherMapActivity", "Marker weather error: " + t.getMessage(), t);
                }
            }
        });
    }

    private void fetchViewportOverlayData() {
        if (mMap == null) {
            return;
        }

        if (gridWeatherCall != null) {
            gridWeatherCall.cancel();
        }

        clearWeatherOverlay();
        legendStatusMessage = "Dang tai lop phu cho khung nhin hien tai...";
        updateLegendUi();

        BatchGridRequest request = buildBatchGridRequest();
        if (request.sampleCount == 0) {
            legendStatusMessage = "Khong tao duoc luoi du lieu.";
            updateLegendUi();
            return;
        }

        final int requestVersion = ++gridRequestVersion;
        gridWeatherCall = weatherApiService.getMapGridForecast(
                request.latitudes,
                request.longitudes,
                false,
                MAP_GRID_HOURLY_PARAMS,
                "GMT",
                isCelsius ? "celsius" : "fahrenheit",
                FORECAST_HOURS
        );

        gridWeatherCall.enqueue(new Callback<List<WeatherResponse>>() {
            @Override
            public void onResponse(@NonNull Call<List<WeatherResponse>> call,
                                   @NonNull Response<List<WeatherResponse>> response) {
                if (call.isCanceled() || requestVersion != gridRequestVersion) {
                    return;
                }
                if (!response.isSuccessful() || response.body() == null) {
                    legendStatusMessage = "Khong tai duoc du lieu lop phu.";
                    updateLegendUi();
                    return;
                }

                populateWeatherCells(request, response.body());
                if (!hasWeatherCells()) {
                    legendStatusMessage = "Khong co du lieu cho lop phu nay.";
                    updateLegendUi();
                    return;
                }

                legendStatusMessage = String.format(
                        Locale.getDefault(),
                        "Lop phu %d o cho vung dang xem",
                        weatherCells.size()
                );
                updateLegendUi();
                renderWeatherOverlay();
            }

            @Override
            public void onFailure(@NonNull Call<List<WeatherResponse>> call, @NonNull Throwable t) {
                if (!call.isCanceled() && requestVersion == gridRequestVersion) {
                    legendStatusMessage = "Tai du lieu lop phu that bai.";
                    updateLegendUi();
                    android.util.Log.e("WeatherMapActivity", "Grid weather error: " + t.getMessage(), t);
                }
            }
        });
    }

    private BatchGridRequest buildBatchGridRequest() {
        BatchGridRequest request = new BatchGridRequest();
        if (mMap == null) {
            return request;
        }

        LatLngBounds bounds = mMap.getProjection().getVisibleRegion().latLngBounds;
        double south = bounds.southwest.latitude;
        double north = bounds.northeast.latitude;
        double west = bounds.southwest.longitude;
        double east = bounds.northeast.longitude;

        double latSpan = Math.max(0.2, Math.abs(north - south));
        double lonSpan = Math.max(0.2, Math.abs(east - west));
        double paddedSouth = clampLatitude(south - (latSpan * VIEWPORT_PADDING_RATIO));
        double paddedNorth = clampLatitude(north + (latSpan * VIEWPORT_PADDING_RATIO));
        double paddedWest = west - (lonSpan * VIEWPORT_PADDING_RATIO);
        double paddedEast = east + (lonSpan * VIEWPORT_PADDING_RATIO);

        int columns = getGridColumnsForZoom(mMap.getCameraPosition().zoom);
        int rows = Math.max(7, (int) Math.round(columns * ((paddedNorth - paddedSouth) / Math.max(0.25, paddedEast - paddedWest))));
        rows = Math.min(rows, 15);
        while (rows * columns > MAX_GRID_POINTS && rows > 6) {
            rows--;
        }

        if (rows < 2 || columns < 2) {
            return request;
        }

        double latStep = (paddedNorth - paddedSouth) / rows;
        double lonStep = (paddedEast - paddedWest) / columns;
        StringBuilder latBuilder = new StringBuilder();
        StringBuilder lonBuilder = new StringBuilder();
        int total = rows * columns;
        int index = 0;

        for (int row = 0; row < rows; row++) {
            double northEdge = paddedNorth - (row * latStep);
            double southEdge = northEdge - latStep;
            double latitude = (northEdge + southEdge) / 2d;

            for (int col = 0; col < columns; col++) {
                double westEdge = paddedWest + (col * lonStep);
                double eastEdge = westEdge + lonStep;
                double longitude = (westEdge + eastEdge) / 2d;

                if (index > 0) {
                    latBuilder.append(',');
                    lonBuilder.append(',');
                }
                latBuilder.append(String.format(Locale.US, "%.4f", latitude));
                lonBuilder.append(String.format(Locale.US, "%.4f", longitude));
                request.cellBounds.add(new CellBounds(northEdge, southEdge, westEdge, eastEdge));
                index++;
            }
        }

        request.latitudes = latBuilder.toString();
        request.longitudes = lonBuilder.toString();
        request.sampleCount = total;
        return request;
    }

    private int getGridColumnsForZoom(float zoom) {
        if (zoom >= 11f) {
            return 12;
        }
        if (zoom >= 9f) {
            return 11;
        }
        if (zoom >= 7f) {
            return 9;
        }
        return 7;
    }

    private void populateWeatherCells(BatchGridRequest request, List<WeatherResponse> responses) {
        weatherCells.clear();
        int limit = Math.min(request.cellBounds.size(), responses.size());
        for (int i = 0; i < limit; i++) {
            WeatherResponse response = responses.get(i);
            if (response == null || response.rawHourly == null) {
                continue;
            }

            double temperature = getFirstValue(response.rawHourly.temperatures);
            double rainProbability = getFirstValue(response.rawHourly.precipitationProbability);
            double cloudCover = getFirstValue(response.rawHourly.cloudCover);

            CellBounds bounds = request.cellBounds.get(i);
            weatherCells.add(new WeatherCell(
                    bounds,
                    normalizeTemperature(temperature),
                    clamp01(cloudCover / 100d),
                    clamp01(rainProbability / 100d)
            ));
        }
    }

    private void renderWeatherOverlay() {
        if (mMap == null) {
            return;
        }

        clearPolygonOverlay();
        if (weatherCells.isEmpty()) {
            return;
        }

        for (WeatherCell cell : weatherCells) {
            Polygon polygon = mMap.addPolygon(new PolygonOptions()
                    .add(
                            new LatLng(cell.bounds.north, cell.bounds.west),
                            new LatLng(cell.bounds.north, cell.bounds.east),
                            new LatLng(cell.bounds.south, cell.bounds.east),
                            new LatLng(cell.bounds.south, cell.bounds.west)
                    )
                    .fillColor(getCellFillColor(cell))
                    .strokeColor(Color.TRANSPARENT)
                    .strokeWidth(0f)
                    .zIndex(1f));
            weatherCellPolygons.add(polygon);
        }
    }

    private int getCellFillColor(WeatherCell cell) {
        return interpolateColor(getModePalette(), getActiveValue(cell), getModeAlpha());
    }

    private double getActiveValue(WeatherCell cell) {
        switch (currentMode) {
            case CLOUD_COVER:
                return cell.cloudCover;
            case PRECIPITATION:
                return cell.precipitation;
            case TEMPERATURE:
            default:
                return cell.temperature;
        }
    }

    private int[] getModePalette() {
        switch (currentMode) {
            case CLOUD_COVER:
                return new int[]{
                        Color.parseColor("#E8F6FB"),
                        Color.parseColor("#BFD8EA"),
                        Color.parseColor("#97B9CF"),
                        Color.parseColor("#6B8CA7"),
                        Color.parseColor("#465B75")
                };
            case PRECIPITATION:
                return new int[]{
                        Color.parseColor("#BFEFFF"),
                        Color.parseColor("#73C5F4"),
                        Color.parseColor("#348BDE"),
                        Color.parseColor("#1D5DB9"),
                        Color.parseColor("#0C2F75")
                };
            case TEMPERATURE:
            default:
                return new int[]{
                        Color.parseColor("#43238F"),
                        Color.parseColor("#2A68D8"),
                        Color.parseColor("#40BFC9"),
                        Color.parseColor("#DCCF58"),
                        Color.parseColor("#F19A35"),
                        Color.parseColor("#D64A2D")
                };
        }
    }

    private int getModeAlpha() {
        switch (currentMode) {
            case CLOUD_COVER:
                return 118;
            case PRECIPITATION:
                return 132;
            case TEMPERATURE:
            default:
                return 145;
        }
    }

    private int interpolateColor(int[] palette, double fraction, int alpha) {
        if (palette.length == 0) {
            return Color.TRANSPARENT;
        }
        double clamped = clamp01(fraction);
        if (palette.length == 1) {
            return withAlpha(palette[0], alpha);
        }

        double scaled = clamped * (palette.length - 1);
        int startIndex = (int) Math.floor(scaled);
        int endIndex = Math.min(palette.length - 1, startIndex + 1);
        double localFraction = scaled - startIndex;

        int start = palette[startIndex];
        int end = palette[endIndex];

        int red = (int) Math.round(Color.red(start) + ((Color.red(end) - Color.red(start)) * localFraction));
        int green = (int) Math.round(Color.green(start) + ((Color.green(end) - Color.green(start)) * localFraction));
        int blue = (int) Math.round(Color.blue(start) + ((Color.blue(end) - Color.blue(start)) * localFraction));
        return Color.argb(alpha, red, green, blue);
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void clearWeatherOverlay() {
        weatherCells.clear();
        clearPolygonOverlay();
    }

    private void clearPolygonOverlay() {
        for (Polygon polygon : weatherCellPolygons) {
            polygon.remove();
        }
        weatherCellPolygons.clear();
    }

    private boolean hasWeatherCells() {
        return !weatherCells.isEmpty();
    }

    private int[] reverseColors(int[] colors) {
        int[] reversed = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            reversed[i] = colors[colors.length - 1 - i];
        }
        return reversed;
    }

    private double normalizeTemperature(double temperature) {
        double tempC = temperature;
        if (!isCelsius) {
            tempC = (temperature - 32d) * 5d / 9d;
        }
        double normalized = (tempC + 10d) / 60d;
        return clamp01(normalized);
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private double clampLatitude(double latitude) {
        return Math.max(-85d, Math.min(85d, latitude));
    }

    private double getFirstValue(List<Double> values) {
        if (values == null || values.isEmpty() || values.get(0) == null) {
            return 0d;
        }
        return values.get(0);
    }

    private String getWeatherStatus(int code) {
        if (code == 0) return "Troi quang";
        if (code <= 3) return "Co may";
        if (code >= 45 && code <= 48) return "Suong mu";
        if (code >= 51 && code <= 67) return "Dang mua";
        if (code >= 80 && code <= 82) return "Mua rao";
        if (code >= 95) return "Dong bao";
        return "Thoi tiet";
    }

    private float dpToPx(int dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private static class BatchGridRequest {
        String latitudes = "";
        String longitudes = "";
        int sampleCount = 0;
        List<CellBounds> cellBounds = new ArrayList<>();
    }

    private static class CellBounds {
        final double north;
        final double south;
        final double west;
        final double east;

        CellBounds(double north, double south, double west, double east) {
            this.north = north;
            this.south = south;
            this.west = west;
            this.east = east;
        }
    }

    private static class WeatherCell {
        final CellBounds bounds;
        final double temperature;
        final double cloudCover;
        final double precipitation;

        WeatherCell(CellBounds bounds, double temperature, double cloudCover, double precipitation) {
            this.bounds = bounds;
            this.temperature = temperature;
            this.cloudCover = cloudCover;
            this.precipitation = precipitation;
        }
    }
}
