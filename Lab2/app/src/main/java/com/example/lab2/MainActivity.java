package com.example.lab2;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.media.RingtoneManager;
import android.net.Uri;
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
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * ============================================================
 * MainActivity - Màn hình chính của ứng dụng Dự báo thời tiết
 * ============================================================
 * 
 * Chức năng chính:
 * 1. Hiển thị thời tiết hiện tại (nhiệt độ, điều kiện, gió, độ ẩm)
 * 2. Lấy vị trí GPS của người dùng (hoặc dùng vị trí mặc định)
 * 3. Gọi API Open-Meteo để lấy dữ liệu thời tiết
 * 4. Hiển thị dự báo theo giờ (24 giờ) và dự báo 7 ngày
 * 5. Kiểm tra các điều kiện thời tiết xấu và gửi thông báo
 * 6. Cho phép toggle giữa Celsius và Fahrenheit
 * 7. Chuyển sang màn hình bản đồ thời tiết
 * 
 * @author Weather App Team
 * @version 1.0
 */
public class MainActivity extends AppCompatActivity {

    // ===== UI Components =====
    private ActivityMainBinding binding; // View binding để truy cập views từ layout
    
    // ===== Location Services =====
    private FusedLocationProviderClient fusedLocationClient; // Google Location Services
    
    // ===== Constants =====
    private static final int PERMISSION_REQUEST_CODE = 1001; // ID request quyền
    private static final String CHANNEL_ID = "weather_alerts"; // ID notification channel
    
    // ===== Location Data =====
    private double currentLat = 21.02; // Vĩ độ hiện tại (mặc định Hà Nội)
    private double currentLon = 105.83; // Kinh độ hiện tại (mặc định Hà Nội)
    private LocationCallback locationCallback; // Callback khi nhận location update
    
    // ===== Temperature Unit =====
    private boolean isCelsius = true; // true = °C, false = °F
    
    /**
     * ===== ALERT FLAGS (Chống spam thông báo) =====
     * Mỗi flag được set true khi gửi thông báo, reset false khi điều kiện bình thường
     * Để tránh spam người dùng với cùng 1 cảnh báo nhiều lần
     */
    private boolean heatAlertSent = false; // Đã báo nắng nóng
    private boolean coldAlertSent = false; // Đã báo rét
    private boolean highHumidityAlertSent = false; // Đã báo độ ẩm cao
    private boolean strongWindAlertSent = false; // Đã báo gió mạnh
    private boolean stormAlertSent = false; // Đã báo dông bão/mưa lớn

    /**
     * onCreate - Khởi tạo Activity
     * Được gọi khi Activity được tạo lần đầu
     * 
     * Công việc:
     * 1. Khởi tạo view binding
     * 2. Tạo notification channel cho thông báo
     * 3. Khởi tạo location services
     * 4. Yêu cầu quyền GPS
     * 5. Gán listener cho các button
     */
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

        // Button: Xem bản đồ thời tiết
        binding.btnViewMap.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, WeatherMapActivity.class);
            intent.putExtra("lat", currentLat);
            intent.putExtra("lon", currentLon);
            intent.putExtra("isCelsius", isCelsius);
            startActivity(intent);
        });

        // Button: Reload lại vị trí và cập nhật dữ liệu thời tiết
        binding.btnReload.setOnClickListener(v -> {
            Toast.makeText(this, "🔄 Đang cập nhật vị trí...", Toast.LENGTH_SHORT).show();
            // Xóa vị trí cũ và lấy lại vị trí mới
            getLastLocation();
        });

        // Button: Thoát ứng dụng
        binding.btnExit.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });

        // Button: Đổi đơn vị nhiệt độ (°C ↔ °F)
        binding.btnToggleUnit.setOnClickListener(v -> {
            isCelsius = !isCelsius; // Chuyển đổi đơn vị
            updateUnitButtonText(); // Cập nhật text button
            resetAllAlertFlags(); // Reset flag để tránh logic lỗi khi đổi unit
            fetchWeatherData(currentLat, currentLon); // Lấy lại dữ liệu với unit mới
        });
    }

    /**
     * checkAndRequestPermissions - Kiểm tra và yêu cầu quyền cần thiết
     * 
     * Quyền cần thiết:
     * 1. ACCESS_FINE_LOCATION - Lấy vị trí GPS chính xác (dùng để lấy tọa độ)
     * 2. ACCESS_COARSE_LOCATION - Lấy vị trí xấp xỉ (từ cell tower, WiFi)
     * 3. POST_NOTIFICATIONS - Gửi thông báo cảnh báo (Android 13+)
     * 
     * Công việc:
     * - Kiểm tra từng quyền đã được cấp chưa
     * - Nếu chưa, thêm vào danh sách permissionsNeeded
     * - Gọi requestPermissions() để yêu cầu quyền từ user
     * - Nếu đã có tất cả, gọi getLastLocation() ngay lập tức
     */
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        // Kiểm tra quyền vị trí chính xác
        boolean fineGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        // Kiểm tra quyền vị trí xấp xỉ
        boolean coarseGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        // Nếu không có cả 2 quyền vị trí, yêu cầu cấp quyền
        if (!fineGranted && !coarseGranted) {
            permissionsNeeded.add(Manifest.permission.ACCESS_FINE_LOCATION);
            permissionsNeeded.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        
        // Từ Android 13+ cần quyền riêng để gửi thông báo
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        // Nếu còn quyền chưa cấp, hiển thị dialog yêu cầu
        if (!permissionsNeeded.isEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        } else {
            // Nếu có tất cả quyền, lấy vị trí ngay
            getLastLocation();
        }
    }

    /**
     * getLastLocation - Lấy vị trí cuối cùng từ device
     * 
     * Quá trình:
     * 1. Kiểm tra quyền GPS có được cấp không
     * 2. Kiểm tra GPS/Vị trí có được bật không
     *    - Nếu không, dùng vị trí mặc định (Hà Nội: 21.02, 105.83)
     * 3. Lấy vị trí cuối cùng lưu trong cache (nhanh nhất)
     * 4. Nếu có vị trí, gọi handleNewLocation() để xử lý
     * 5. Nếu không có, gọi requestSingleCurrentLocation() để lấy realtime
     */
    private void getLastLocation() {
        // Kiểm tra quyền GPS
        if (!hasLocationPermission()) {
            return;
        }

        // Kiểm tra GPS/Vị trí có được bật không
        if (!isLocationEnabled()) {
            Toast.makeText(this, "Hãy bật GPS hoặc Vị trí để lấy dữ liệu chính xác.", Toast.LENGTH_LONG).show();
            fetchWeatherData(currentLat, currentLon);
            return;
        }

        // Lấy vị trí cuối cùng từ cache (nhanh hơn lấy realtime)
        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(this, location -> {
                    // Nếu có vị trí cũ, xử lý nó trước
                    if (location != null) {
                        handleNewLocation(location);
                    }
                    // Sau đó, lấy vị trí realtime mới nhất
                    requestSingleCurrentLocation();
                })
                // Nếu lỗi khi lấy vị trí cũ, chuyển sang realtime ngay
                .addOnFailureListener(this, e -> requestSingleCurrentLocation());
    }

    /**
     * requestSingleCurrentLocation - Lấy 1 lần vị trí realtime hiện tại
     * 
     * Sử dụng Priority.PRIORITY_HIGH_ACCURACY để có độ chính xác cao nhất
     * Đây là bước sau getLastLocation() - để lấy vị trí mới nhất
     * 
     * Nếu thất bại, fallback sang requestLocationUpdatesFallback()
     */
    private void requestSingleCurrentLocation() {
        // Kiểm tra quyền GPS
        if (!hasLocationPermission()) {
            return;
        }

        // Lấy vị trí realtime 1 lần với độ chính xác cao
        fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        handleNewLocation(location);
                    } else {
                        // Nếu không lấy được, dùng phương pháp fallback
                        requestLocationUpdatesFallback();
                    }
                })
                .addOnFailureListener(this, e -> requestLocationUpdatesFallback());
    }

    /**
     * requestLocationUpdatesFallback - Phương pháp thay thế để lấy vị trí
     * 
     * Được gọi khi getCurrentLocation() hoặc getLastLocation() thất bại
     * 
     * Cài đặt LocationRequest:
     * - Priority.PRIORITY_HIGH_ACCURACY: Độ chính xác cao nhất
     * - 3000ms: Lấy update mỗi 3 giây
     * - MinUpdateIntervalMillis 1500ms: Tối thiểu 1.5 giây giữa updates
     * - MaxUpdates 3: Chỉ lấy tối đa 3 lần update (để không lãng phí pin)
     */
    private void requestLocationUpdatesFallback() {
        // Kiểm tra quyền GPS
        if (!hasLocationPermission()) {
            return;
        }

        // Tạo request để lấy update vị trí theo định kỳ
        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000)
                .setMinUpdateIntervalMillis(1500) // Tối thiểu 1.5 giây giữa các update
                .setMaxUpdates(3) // Chỉ lấy 3 lần để tiết kiệm pin
                .build();

        // Tạo callback để xử lý khi nhận được update vị trí
        if (locationCallback == null) {
            locationCallback = new LocationCallback() {
                @Override
                public void onLocationResult(@NonNull LocationResult locationResult) {
                    Location location = locationResult.getLastLocation();
                    if (location != null) {
                        handleNewLocation(location);
                        // Sau khi có vị trí, dừng cập nhật để tiết kiệm pin
                        fusedLocationClient.removeLocationUpdates(this);
                    }
                }
            };
        }

        // Bắt đầu lắng nghe update vị trí
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, getMainLooper());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * handleNewLocation - Xử lý khi nhận location mới từ GPS
     * 
     * Công việc:
     * 1. Cập nhật vị trí hiện tại (lat/lon)
     * 2. Reset tất cả alert flags (để check lại ở vị trí mới)
     * 3. Lấy dữ liệu thời tiết tại vị trí mới
     * 4. Hiển thị thông báo cảnh báo (nếu có)
     * 
     * @param location - Object Location từ GPS
     */
    private void handleNewLocation(@NonNull Location location) {
        currentLat = location.getLatitude();
        currentLon = location.getLongitude();
        
        // Reset alert flags khi location thay đổi
        // Để kiểm tra cảnh báo lại tại vị trí mới
        resetAllAlertFlags();
        
        // Hiển thị Toast để user biết app đang kiểm tra thời tiết
        Toast.makeText(this, "📍 Kiểm tra thời tiết tại vị trí mới...", Toast.LENGTH_SHORT).show();
        
        // Lấy dữ liệu thời tiết (sẽ gọi checkWeatherAlerts() sau)
        fetchWeatherData(currentLat, currentLon);
    }

    /**
     * hasLocationPermission - Kiểm tra user đã cấp quyền GPS hay chưa
     * 
     * @return true nếu có ít nhất 1 trong 2 quyền vị trí
     */
    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * isLocationEnabled - Kiểm tra GPS hoặc Network Location có được bật không
     * 
     * @return true nếu GPS hoặc Network Location được bật
     */
    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (locationManager == null) {
            return false;
        }
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
                || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    /**
     * fetchWeatherData - Gọi API Open-Meteo để lấy dữ liệu thời tiết
     * 
     * API Request gồm các tham số:
     * - Vị trí: lat, lon
     * - Dữ liệu từng giờ: nhiệt độ, mã thời tiết, xác suất mưa, v.v.
     * - Dữ liệu hàng ngày: nhiệt độ max/min, mã thời tiết, v.v.
     * - Đơn vị: Celsius hoặc Fahrenheit
     * 
     * Khi nhận response:
     * 1. updateUI() - cập nhật giao diện với dữ liệu mới
     * 2. checkWeatherAlerts() - kiểm tra điều kiện thời tiết xấu
     * 
     * @param lat - Vĩ độ
     * @param lon - Kinh độ
     */
    private void fetchWeatherData(double lat, double lon) {
        // Tạo Retrofit client để gọi API
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.open-meteo.com/")
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        // Tạo service từ interface
        WeatherApiService service = retrofit.create(WeatherApiService.class);
        // Chuyển đổi đơn vị nhiệt độ theo lựa chọn của user
        String tempUnit = isCelsius ? "celsius" : "fahrenheit";
        // Gọi API với các tham số cần thiết
        service.getForecast(
                lat,
                lon,
                true,
                "temperature_2m,weathercode,precipitation_probability,cloudcover,relative_humidity_2m,windspeed_10m",
                "temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max,relative_humidity_2m_max,windspeed_10m_max",
                "auto",
                tempUnit
        ).enqueue(new Callback<WeatherResponse>() {
            /**
             * onResponse - Được gọi khi API trả về response thành công
             */
            @Override
            public void onResponse(@NonNull Call<WeatherResponse> call, @NonNull Response<WeatherResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    // Cập nhật UI với dữ liệu mới
                    updateUI(response.body());
                    // Kiểm tra xem có cảnh báo thời tiết nào không
                    checkWeatherAlerts(response.body());
                }
            }

            /**
             * onFailure - Được gọi khi API call thất bại (lỗi mạng, timeout, v.v.)
             */
            @Override
            public void onFailure(@NonNull Call<WeatherResponse> call, @NonNull Throwable t) {
                Toast.makeText(MainActivity.this, "Lỗi: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * updateUI - Cập nhật toàn bộ giao diện với dữ liệu thời tiết mới
     * 
     * Cập nhật các thành phần:
     * 1. Tên thành phố (dùng Geocoder để lấy tên từ tọa độ)
     * 2. Nhiệt độ hiện tại (Celsius/Fahrenheit)
     * 3. Mô tả thời tiết (Trời quang, mưa, dông, v.v.)
     * 4. Tốc độ gió
     * 5. Độ ẩm
     * 6. RecyclerView dự báo theo giờ (24 giờ)
     * 7. RecyclerView dự báo 7 ngày
     * 
     * @param weather - Object WeatherResponse chứa dữ liệu API trả về
     */
    private void updateUI(WeatherResponse weather) {
        // Khởi tạo tên thành phố với tọa độ (fallback nếu Geocoder thất bại)
        String cityName = String.format(Locale.getDefault(), "Vĩ độ: %.2f, Kinh độ: %.2f", weather.latitude, weather.longitude);
        
        // Dùng Geocoder để chuyển tọa độ thành tên địa chỉ
        Geocoder geocoder = new Geocoder(this, Locale.getDefault());
        try {
            // Lấy địa chỉ dựa trên tọa độ (1 kết quả)
            List<Address> addresses = geocoder.getFromLocation(weather.latitude, weather.longitude, 1);
            if (addresses != null && !addresses.isEmpty()) {
                Address addr = addresses.get(0);
                String locality = addr.getLocality(); // Tên thành phố
                String adminArea = addr.getAdminArea(); // Tên tỉnh/bang

                // Ưu tiên: Thành phố > Tỉnh > Quốc gia
                if (locality != null) cityName = locality;
                else if (adminArea != null) cityName = adminArea;
                else if (addr.getCountryName() != null) cityName = addr.getCountryName();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Hiển thị tên thành phố
        binding.tvLocation.setText(cityName);
        binding.tvLocation.setTextColor(android.graphics.Color.BLACK);

        // Hiển thị nhiệt độ hiện tại với đơn vị phù hợp
        String tempUnit = isCelsius ? "°C" : "°F";
        binding.tvCurrentTemp.setText(String.format(Locale.getDefault(), "%.1f%s", weather.currentWeather.temperature, tempUnit));
        binding.tvCurrentTemp.setTextColor(android.graphics.Color.BLACK);

        // Hiển thị mô tả thời tiết (Trời quang, mưa, dông, v.v.)
        binding.tvWeatherDescription.setText(getWeatherDescription(weather.currentWeather.weatherCode));
        binding.tvWeatherDescription.setTextColor(android.graphics.Color.DKGRAY);

        // Hiển thị tốc độ gió hiện tại
        String windStr = String.format(Locale.getDefault(), "Gió: %.1f km/h", weather.currentWeather.windSpeed);
        binding.tvWindSpeed.setText(windStr);
        binding.tvWindSpeed.setTextColor(android.graphics.Color.DKGRAY);
        
        // Trích xuất độ ẩm từ dữ liệu theo giờ (giờ thứ 0 = giờ hiện tại)
        int humidity = 0;
        if (weather.rawHourly != null && weather.rawHourly.relativeHumidity != null 
                && !weather.rawHourly.relativeHumidity.isEmpty() 
                && weather.rawHourly.relativeHumidity.get(0) != null) {
            humidity = weather.rawHourly.relativeHumidity.get(0);
        }
        // Hiển thị độ ẩm
        String humidityStr = String.format(Locale.getDefault(), "Độ ẩm: %d%%", humidity);
        binding.tvHumidity.setText(humidityStr);
        binding.tvHumidity.setTextColor(android.graphics.Color.DKGRAY);

        // Cập nhật text của button toggle đơn vị
        updateUnitButtonText();

        // Thiết lập RecyclerView dự báo theo giờ (cuộn ngang)
        binding.rvHourly.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<WeatherResponse.HourlyItem> hourlyItems = weather.getHourlyItems();
        // Giới hạn tối đa 24 giờ (từ 00:00 đến 23:00)
        if (hourlyItems.size() > 24) {
            hourlyItems = hourlyItems.subList(0, 24);
        }
        binding.rvHourly.setAdapter(new HourlyAdapter(hourlyItems, isCelsius));

        // Thiết lập RecyclerView dự báo 7 ngày (cuộn dọc)
        binding.rvDaily.setLayoutManager(new LinearLayoutManager(this));
        binding.rvDaily.setAdapter(new DailyAdapter(weather.getDailyItems(), isCelsius));
    }

    /**
     * updateUnitButtonText - Cập nhật text của button toggle đơn vị
     * Hiển thị hướng chuyển đổi: Nếu hiện tại là °C thì show "°C → °F"
     */
    private void updateUnitButtonText() {
        binding.btnToggleUnit.setText(isCelsius ? "°C → °F" : "°F → °C");
    }

    /**
     * getWeatherDescription - Chuyển đổi mã thời tiết (WMO code) thành mô tả tiếng Việt
     * 
     * Mã thời tiết WMO:
     * - 0: Trời quang
     * - 1-3: Mây rải rác
     * - 45-48: Sương mù
     * - 51-55: Mưa phùn (rất nhẹ)
     * - 61-65: Mưa nhẹ đến vừa
     * - 71-75: Tuyết
     * - 80-82: Mưa rào (bất thường, hay đột ngột)
     * - 95-99: Dông bão
     * 
     * @param code - Mã thời tiết từ API
     * @return String mô tả thời tiết tiếng Việt
     */
    private String getWeatherDescription(int code) {
        switch (code) {
            case 0: return "Trời quang";
            case 1:
            case 2:
            case 3: return "Mây rải rác";
            case 45:
            case 48: return "Sương mù";
            case 51:
            case 53:
            case 55: return "Mưa phùn";
            case 61:
            case 63:
            case 65: return "Mưa nhẹ";
            case 71:
            case 73:
            case 75: return "Tuyết rơi";
            case 80:
            case 81:
            case 82: return "Mưa rào";
            case 95:
            case 96:
            case 99: return "Dông bão";
            default: return "Thời tiết khác (" + code + ")";
        }
    }

    /**
     * checkWeatherAlerts - Kiểm tra các điều kiện thời tiết xấu và gửi thông báo
     * 
     * Các loại cảnh báo được kiểm tra:
     * 1. Nắng nóng: Nhiệt độ > 25°C (77°F) - DANGER
     * 2. Rét đặc biệt: Nhiệt độ < 5°C (41°F) - DANGER
     * 3. Độ ẩm cao: Độ ẩm > 80% - WARNING
     * 4. Gió mạnh: Tốc độ gió > 40 km/h - WARNING
     * 5. Dông bão: Mã thời tiết 95-99 - EXTREME
     * 6. Mưa lớn: Mã thời tiết 80-94 - WARNING
     * 
     * Hệ thống flag:
     * - Mỗi loại cảnh báo có 1 flag (ví dụ heatAlertSent)
     * - Flag = true khi đã gửi cảnh báo (tránh spam)
     * - Flag reset = false khi điều kiện bình thường trở lại
     * 
     * @param weather - Object WeatherResponse chứa dữ liệu thời tiết
     */
    private void checkWeatherAlerts(WeatherResponse weather) {
        // Kiểm tra dữ liệu hợp lệ
        if (weather == null || weather.currentWeather == null) return;
        
        // Trích xuất các thông số thời tiết hiện tại
        double temp = weather.currentWeather.temperature;
        double windSpeed = weather.currentWeather.windSpeed;
        int weatherCode = weather.currentWeather.weatherCode;
        double humidity = 0;
        
        // Trích xuất độ ẩm từ dữ liệu theo giờ
        if (weather.rawHourly != null && weather.rawHourly.relativeHumidity != null 
                && !weather.rawHourly.relativeHumidity.isEmpty()) {
            humidity = weather.rawHourly.relativeHumidity.get(0);
        }
        
        // ===== CẢNH BÁO 1: Nắng nóng =====
        double heatThreshold = isCelsius ? 25 : 77; // 25°C = 77°F
        if (temp > heatThreshold && !heatAlertSent) {
            // Chưa gửi cảnh báo nắng nóng mà nhiệt độ cao -> gửi cảnh báo
            heatAlertSent = true;
            String tempStr = String.format(Locale.getDefault(), "%.1f°%s", temp, isCelsius ? "C" : "F");
            sendNotification("⚠️ CẢNH BÁO: Nắng nóng", 
                "Nhiệt độ vượt mức nguy hiểm: " + tempStr + " (Nên cập nhập nước & ở trong bóng mát!)", 
                AlertLevel.DANGER);
        } else if (temp <= heatThreshold && heatAlertSent) {
            // Nhiệt độ giảm xuống bình thường -> reset flag
            heatAlertSent = false;
        }
        
        // ===== CẢNH BÁO 2: Rét đặc biệt =====
        double coldThreshold = isCelsius ? 5 : 41; // 5°C = 41°F
        if (temp < coldThreshold && !coldAlertSent) {
            // Chưa gửi cảnh báo rét mà nhiệt độ thấp -> gửi cảnh báo
            coldAlertSent = true;
            String tempStr = String.format(Locale.getDefault(), "%.1f°%s", temp, isCelsius ? "C" : "F");
            sendNotification("❄️ CẢNH BÁO: Rét đặc biệt", 
                "Nhiệt độ rất lạnh: " + tempStr + " (Cần mặc đủ ấm)", 
                AlertLevel.DANGER);
        } else if (temp >= coldThreshold && coldAlertSent) {
            // Nhiệt độ tăng lên bình thường -> reset flag
            coldAlertSent = false;
        }
        
        // ===== CẢNH BÁO 3: Độ ẩm cao =====
        if (humidity > 80 && !highHumidityAlertSent) {
            // Chưa gửi cảnh báo độ ẩm cao mà độ ẩm vượt 80% -> gửi cảnh báo
            highHumidityAlertSent = true;
            sendNotification("💧 CẢNH BÁO: Độ ẩm cao", 
                String.format(Locale.getDefault(), "Độ ẩm: %.0f%% ", humidity), 
                AlertLevel.WARNING);
        } else if (humidity <= 80 && highHumidityAlertSent) {
            // Độ ẩm giảm xuống bình thường -> reset flag
            highHumidityAlertSent = false;
        }
        
        // ===== CẢNH BÁO 4: Gió mạnh =====
        if (windSpeed > 40 && !strongWindAlertSent) {
            // Chưa gửi cảnh báo gió mạnh mà gió vượt 40 km/h -> gửi cảnh báo
            strongWindAlertSent = true;
            sendNotification("💨 CẢNH BÁO: Gió mạnh", 
                String.format(Locale.getDefault(), "Tốc độ gió: %.0f km/h (Nên cẩn thận khi ra ngoài)", windSpeed), 
                AlertLevel.WARNING);
        } else if (windSpeed <= 40 && strongWindAlertSent) {
            // Gió yếu lại bình thường -> reset flag
            strongWindAlertSent = false;
        }
        
        // ===== CẢNH BÁO 5: Dông bão & Mưa lớn =====
        if (weatherCode >= 95 && !stormAlertSent) {
            // Mã 95-99: Dông bão -> gửi cảnh báo EXTREME
            stormAlertSent = true;
            sendNotification("⛈️ CẢNH BÁO: Dông bão", 
                "Có dông bão đang xảy ra! Nên ở trong nhà & tránh hoạt động ngoài trời", 
                AlertLevel.EXTREME);
        } else if ((weatherCode >= 80 && weatherCode < 95) && !stormAlertSent) {
            // Mã 80-94: Mưa rào/Mưa lớn -> gửi cảnh báo WARNING
            stormAlertSent = true;
            sendNotification("🌧️ CẢNH BÁO: Mưa lớn", 
                "Mưa rào sắp xảy ra, hãy chuẩn bị ô và mặc áo mưa", 
                AlertLevel.WARNING);
        } else if (weatherCode < 80 && stormAlertSent) {
            // Thời tiết bình thường lại (mã < 80) -> reset flag
            stormAlertSent = false;
        }
    }
    
    /**
     * resetAllAlertFlags - Reset tất cả alert flags về false
     * 
     * Được gọi khi:
     * - Vị trí thay đổi (cần check cảnh báo ở vị trí mới)
     * - User chuyển đổi đơn vị (°C ↔ °F) để tránh logic lỗi
     */
    private void resetAllAlertFlags() {
        heatAlertSent = false;
        coldAlertSent = false;
        highHumidityAlertSent = false;
        strongWindAlertSent = false;
        stormAlertSent = false;
    }
    
    /**
     * AlertLevel - Enum định nghĩa mức độ cảnh báo
     * 
     * Mức độ từ thấp đến cao:
     * - WARNING: Cảnh báo nhẹ (Độ ẩm cao, Gió mạnh) - Màu vàng cam
     * - DANGER: Cảnh báo nguy hiểm (Nắng nóng, Rét) - Màu đỏ
     * - EXTREME: Cảnh báo nghiêm trọng (Dông bão) - Màu đỏ tối
     */
    private enum AlertLevel {
        WARNING,    // Vàng cam - Cảnh báo nhẹ
        DANGER,     // Đỏ - Cảnh báo nguy hiểm
        EXTREME     // Đỏ tối - Cảnh báo nghiêm trọng
    }

    /**
     * sendNotification - Gửi thông báo cảnh báo tới user
     * 
     * Thiết lập thông báo dựa trên mức độ:
     * - WARNING: Ưu tiên cao, Màu vàng cam
     * - DANGER: Ưu tiên cao, Màu đỏ
     * - EXTREME: Ưu tiên tối đa, Màu đỏ tối
     * 
     * Tất cả thông báo đều có:
     * - Âm thanh (notification sound)
     * - Rung (vibration pattern)
     * - Đèn LED (blink light)
     * - Category ALARM để hiển thị heads-up notification
     * 
     * @param title - Tiêu đề thông báo
     * @param message - Nội dung thông báo
     * @param level - Mức độ cảnh báo (WARNING, DANGER, EXTREME)
     */
    private void sendNotification(String title, String message, AlertLevel level) {
        // Thiết lập priority và color dựa trên alert level
        int priority = NotificationCompat.PRIORITY_HIGH;
        int color = 0xFFFF9800; // Mặc định: Màu cam (WARNING)
        
        switch (level) {
            case DANGER:
                // Nguy hiểm: Ưu tiên cao, Màu đỏ
                priority = NotificationCompat.PRIORITY_HIGH;
                color = 0xFFE53935; // Đỏ
                break;
            case EXTREME:
                // Nghiêm trọng: Ưu tiên tối đa, Màu đỏ tối
                priority = NotificationCompat.PRIORITY_MAX;
                color = 0xFFC62828; // Đỏ tối
                break;
            case WARNING:
            default:
                // Cảnh báo: Ưu tiên cao, Màu vàng cam nhạt
                priority = NotificationCompat.PRIORITY_HIGH;
                color = 0xFFFFB74D; // Vàng cam nhạt
        }
        
        // Lấy âm thanh thông báo mặc định của hệ thống
        Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        
        // Xây dựng notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Biểu tượng nhỏ
                .setColor(color) // Màu sắc theo mức độ cảnh báo
                .setContentTitle(title) // Tiêu đề
                .setContentText(message) // Nội dung ngắn
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message)) // Hiển thị nội dung đầy đủ
                .setPriority(priority) // Ưu tiên
                .setAutoCancel(true) // Tự động đóng khi user tap
                .setSound(soundUri) // Âm thanh
                .setVibrate(new long[]{0, 500, 250, 500, 200, 500}) // Pattern rung: 500ms-rung, 250ms-dừng, 500ms-rung, 200ms-dừng, 500ms-rung
                .setLights(color, 1000, 1000) // Đèn LED: màu, on 1s, off 1s
                .setCategory(NotificationCompat.CATEGORY_ALARM); // Loại ALARM để heads-up display trên lock screen

        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            // Dùng ordinal() của enum làm ID: WARNING=0, DANGER=1, EXTREME=2
            // Mỗi level có ID khác nhau để hiển thị riêng
            int notificationId = level.ordinal();
            manager.notify(notificationId, builder.build());
        }
    }
    
    /**
     * sendNotification (Overload) - Gửi thông báo với mức độ mặc định (WARNING)
     * Dùng để backward compatibility với code cũ
     * 
     * @param title - Tiêu đề thông báo
     * @param message - Nội dung thông báo
     */
    private void sendNotification(String title, String message) {
        sendNotification(title, message, AlertLevel.WARNING);
    }

    /**
     * createNotificationChannel - Tạo Notification Channel cho Android 8.0+ (API 26+)
     * 
     * Notification Channel là bắt buộc từ Android 8.0+
     * Cho phép user kiểm soát cách thức thông báo (âm thanh, rung, v.v.)
     * 
     * Cài đặt channel:
     * - IMPORTANCE_MAX: Mức độ quan trọng tối đa (hiển thị heads-up)
     * - Âm thanh: Notification ringtone
     * - Rung: Pattern 500-250-500-200-500 ms
     * - Đèn LED: Đỏ, blink 1s
     * - Bypass DND: Bỏ qua chế độ im lặng (Do Not Disturb)
     */
    private void createNotificationChannel() {
        // Chỉ cần tạo khi API >= 26 (Android 8.0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Tạo channel với ID, tên, và mức độ quan trọng
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Thông báo thời tiết", NotificationManager.IMPORTANCE_MAX);
            channel.setDescription("Thông báo cảnh báo thời tiết khẩn cấp");
            
            // Thiết lập âm thanh
            Uri soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            android.media.AudioAttributes audioAttributes = new android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ALARM) // Loại âm thanh: Alarm
                    .build();
            channel.setSound(soundUri, audioAttributes);
            
            // Thiết lập rung (vibration)
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 500, 250, 500, 200, 500});
            
            // Thiết lập đèn LED
            channel.setLightColor(0xFFE53935); // Màu đỏ
            channel.enableLights(true);
            
            // Cho phép bypass Do Not Disturb mode (Im lặng)
            channel.setBypassDnd(true);
            
            // Đăng ký channel với hệ thống
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    /**
     * onRequestPermissionsResult - Được gọi khi user phản hồi request quyền
     * 
     * Xử lý:
     * - Nếu user cấp quyền: getLastLocation() để lấy vị trí
     * - Nếu user từ chối: Dùng vị trí mặc định (Hà Nội) và lấy dữ liệu thời tiết
     * 
     * @param requestCode - ID request (phải match với PERMISSION_REQUEST_CODE)
     * @param permissions - Mảng tên quyền được yêu cầu
     * @param grantResults - Mảng kết quả (PERMISSION_GRANTED hoặc PERMISSION_DENIED)
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (hasLocationPermission()) {
                // User cấp quyền -> lấy vị trí
                getLastLocation();
            } else {
                // User từ chối quyền -> dùng vị trí mặc định
                Toast.makeText(this, "Không có quyền vị trí, đang dùng vị trí mặc định.", Toast.LENGTH_LONG).show();
                fetchWeatherData(currentLat, currentLon);
            }
        }
    }

    /**
     * onDestroy - Được gọi khi Activity bị destroy
     * 
     * Tinh dọn dẹp (cleanup):
     * - Dừng lắng nghe update vị trí (locationCallback)
     * - Tiết kiệm pin và các tài nguyên hệ thống
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dừng cập nhật vị trí để tiết kiệm pin
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }
}
