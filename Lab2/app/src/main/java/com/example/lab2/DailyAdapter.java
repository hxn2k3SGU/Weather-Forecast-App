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

public class DailyAdapter extends RecyclerView.Adapter<DailyAdapter.ViewHolder> {
    private List<WeatherResponse.DailyItem> dailyList;

    public DailyAdapter(List<WeatherResponse.DailyItem> dailyList) {
        this.dailyList = dailyList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDailyForecastBinding binding = ItemDailyForecastBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        WeatherResponse.DailyItem daily = dailyList.get(position);
        
        holder.binding.tvTempMax.setText(Math.round(daily.max) + "°");
        holder.binding.tvTempMin.setText(Math.round(daily.min) + "°");
        holder.binding.tvHumidity.setText(String.format(Locale.getDefault(), "%.0f%%", daily.precipitationProbabilityMax));
        
        // Format date from 2023-10-27 to Day Name
        try {
            SimpleDateFormat inFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            Date date = inFormat.parse(daily.date);
            SimpleDateFormat outFormat = new SimpleDateFormat("EEEE", Locale.getDefault());
            if (date != null) holder.binding.tvDay.setText(outFormat.format(date));
        } catch (Exception e) {
            holder.binding.tvDay.setText(daily.date);
        }

        String iconCode = WeatherResponse.getWeatherIcon(daily.code);
        String iconUrl = "https://openweathermap.org/img/wn/" + iconCode + "@2x.png";
        Glide.with(holder.itemView.getContext()).load(iconUrl).into(holder.binding.ivWeatherIcon);
    }

    @Override
    public int getItemCount() {
        return dailyList != null ? dailyList.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ItemDailyForecastBinding binding;
        public ViewHolder(ItemDailyForecastBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
