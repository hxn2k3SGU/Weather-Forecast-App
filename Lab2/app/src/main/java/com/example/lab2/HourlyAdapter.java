package com.example.lab2;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.lab2.databinding.ItemHourlyForecastBinding;
import java.util.List;
import java.util.Locale;

public class HourlyAdapter extends RecyclerView.Adapter<HourlyAdapter.ViewHolder> {
    private List<WeatherResponse.HourlyItem> hourlyList;

    public HourlyAdapter(List<WeatherResponse.HourlyItem> hourlyList) {
        this.hourlyList = hourlyList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemHourlyForecastBinding binding = ItemHourlyForecastBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WeatherResponse.HourlyItem hourly = hourlyList.get(position);
        holder.binding.tvTemp.setText(Math.round(hourly.temp) + "°");
        
        // Open-Meteo time format: 2023-10-27T13:00 -> Extract 13:00
        if (hourly.time.length() >= 16) {
            holder.binding.tvHour.setText(hourly.time.substring(11, 16));
        }

        String iconCode = WeatherResponse.getWeatherIcon(hourly.code);
        String iconUrl = "https://openweathermap.org/img/wn/" + iconCode + "@2x.png";
        Glide.with(holder.itemView.getContext()).load(iconUrl).into(holder.binding.ivWeatherIcon);
    }

    @Override
    public int getItemCount() {
        return hourlyList != null ? hourlyList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemHourlyForecastBinding binding;
        public ViewHolder(ItemHourlyForecastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
