package com.example.lab2;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.lab2.databinding.ItemHourlyForecastBinding;
import java.util.List;
import java.util.Locale;

/**
 * HourlyAdapter - RecyclerView Adapter de hien thi du bao thoi tiet theo gio
 * 
 * Hien thi:
 * - 24 gio du bao (0:00 - 23:00)
 * - Cua moi gio: thoi gian, nhiet do, do am, icon thoi tiet
 * - Cu cuon ngang
 * 
 * Dung Glide de tai icon thoi tiet tu OpenWeatherMap CDN
 */
public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.ViewHolder> {
    private List<WeatherResponse.HourlyItem> hourlyList; // Danh sach 24 gio du bao
    private boolean isCelsius; // true = C, false = F

    /**
     * Constructor - Khoi tao adapter
     * @param hourlyList Danh sach HourlyItem
     * @param isCelsius Kich hoat don vi Celsius hay Fahrenheit
     */
    public HourlyAdapter(List<WeatherResponse.HourlyItem> hourlyList, boolean isCelsius) {
        this.hourlyList = hourlyList;
        this.isCelsius = isCelsius;
    }

    /**
     * onCreateViewHolder - Tao view moi cho item
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHourlyForecastBinding binding = ItemHourlyForecastBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * onBindViewHolder - Cap nhat du lieu vao view tai vi tri position
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WeatherResponse.HourlyItem hourly = hourlyList.get(position);
        String tempUnit = isCelsius ? "°C" : "°F";
        // Hien thi nhiet do (lam tron + don vi)
        holder.binding.tvTemp.setText(Math.round(hourly.temp) + tempUnit.substring(0, 1));
        // Hien thi do am
        holder.binding.tvHumidity.setText(hourly.humidity + "%");
        
        // Open-Meteo time format: 2023-10-27T13:00 -> Trich "13:00"
        if (hourly.time.length() >= 16) {
            holder.binding.tvHour.setText(hourly.time.substring(11, 16));
        }

        // Lay icon tu OpenWeatherMap CDN dung Glide
        String iconCode = WeatherResponse.getWeatherIcon(hourly.code);
        String iconUrl = "https://openweathermap.org/img/wn/" + iconCode + "@2x.png";
        Glide.with(holder.itemView.getContext()).load(iconUrl).into(holder.binding.ivWeatherIcon);
    }

    /**
     * getItemCount - Tra ve tong so item
     */
    @Override
    public int getItemCount() {
        return hourlyList != null ? hourlyList.size() : 0;
    }

    /**
     * ViewHolder - Luu tru reference cua cac view cho item
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemHourlyForecastBinding binding;
        public ViewHolder(ItemHourlyForecastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
