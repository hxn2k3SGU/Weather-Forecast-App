package com.example.lab2;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.lab2.databinding.ItemDailyForecastBinding;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * DailyAdapter - RecyclerView Adapter de hien thi du bao thoi tiet hang ngay
 * 
 * Hien thi:
 * - 7 ngay du bao
 * - Cua moi ngay: ten ngay (Monday, Tuesday, ...), min/max temp, do am, gio, icon
 * - Cu cuon doc
 * 
 * Dung SimpleDateFormat de dinh dang ngay tu "2023-10-27" sang "Monday", "Tuesday", ...
 * Dung Glide de tai icon thoi tiet tu OpenWeatherMap CDN
 */
public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.ViewHolder> {
    private List<WeatherResponse.DailyItem> dailyList; // Danh sach 7 ngay du bao
    private boolean isCelsius; // true = C, false = F

    /**
     * Constructor - Khoi tao adapter
     * @param dailyList Danh sach DailyItem
     * @param isCelsius Kich hoat don vi Celsius hay Fahrenheit
     */
    public DailyAdapter(List<WeatherResponse.DailyItem> dailyList, boolean isCelsius) {
        this.dailyList = dailyList;
        this.isCelsius = isCelsius;
    }

    /**
     * onCreateViewHolder - Tao view moi cho item
     */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDailyForecastBinding binding = ItemDailyForecastBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    /**
     * onBindViewHolder - Cap nhat du lieu vao view tai vi tri position
     */
    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WeatherResponse.DailyItem daily = dailyList.get(position);
        
        String tempUnit = isCelsius ? "°C" : "°F";
        // Hien thi nhiet do max va min (lam tron + don vi)
        holder.binding.tvTempMax.setText(Math.round(daily.max) + tempUnit.substring(0, 1));
        holder.binding.tvTempMin.setText(Math.round(daily.min) + tempUnit.substring(0, 1));
        // Hien thi do am va toc do gio
        holder.binding.tvHumidity.setText(String.format(Locale.getDefault(), "%d%%", daily.humidityMax));
        holder.binding.tvWind.setText(String.format(Locale.getDefault(), "%.0f km/h", daily.windSpeedMax));
        
        // Dinh dang ngay: "2023-10-27" -> "Monday", "Tuesday", ...
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = inFormat.parse(daily.date);
            SimpleDateFormat outFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            if (date != null) holder.binding.tvDay.setText(outFormat.format(date));
        } catch (Exception e) {
            // Neu loi dinh dang, hien thi ngay goc
            holder.binding.tvDay.setText(daily.date);
        }

        // Lay icon tu OpenWeatherMap CDN dung Glide
        String iconCode = WeatherResponse.getWeatherIcon(daily.code);
        String iconUrl = "https://openweathermap.org/img/wn/" + iconCode + "@2x.png";
        Glide.with(holder.itemView.getContext()).load(iconUrl).into(holder.binding.ivWeatherIcon);
    }

    /**
     * getItemCount - Tra ve tong so item
     */
    @Override
    public int getItemCount() {
        return dailyList != null ? dailyList.size() : 0;
    }

    /**
     * ViewHolder - Luu tru reference cua cac view cho item
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemDailyForecastBinding binding;
        public ViewHolder(ItemDailyForecastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
