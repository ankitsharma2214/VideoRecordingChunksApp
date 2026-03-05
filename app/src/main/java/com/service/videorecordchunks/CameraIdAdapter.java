package com.service.videorecordchunks;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.service.videorecordchunks.databinding.ItemCameraIdBinding;
import java.util.List;

public class CameraIdAdapter extends RecyclerView.Adapter<CameraIdAdapter.CameraViewHolder> {

    private List<String> cameraIds;

    public CameraIdAdapter(List<String> cameraIds) {
        this.cameraIds = cameraIds;
    }

    @NonNull
    @Override
    public CameraViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemCameraIdBinding binding = ItemCameraIdBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false);
        return new CameraViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull CameraViewHolder holder, int position) {
        String cameraId = cameraIds.get(position);
        holder.binding.tvCameraId.setText(cameraId);
    }

    @Override
    public int getItemCount() {
        return cameraIds.size();
    }

    static class CameraViewHolder extends RecyclerView.ViewHolder {
        ItemCameraIdBinding binding;

        public CameraViewHolder(ItemCameraIdBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}