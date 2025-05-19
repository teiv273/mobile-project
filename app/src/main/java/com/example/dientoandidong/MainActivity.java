package com.example.dientoandidong;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.*;

import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.*;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    private static final int SELECT_IMAGE = 1;

    private ImageView imageView;
    private LinearLayout bottomSheet;
    private SeekBar seekBarIntensity, seekBarBrightness, seekBarContrast, seekBarSaturation;
    private TextView sliderTitle;

    private Bitmap originalBitmap, filteredBitmap, adjustedBitmap;
    private Mat originalMat;

    private double filterIntensity = 1.0;

    private int selectedFilter = 0; // 0:none, 1:grayscale, 2:sepia, 3:invert

    private RecyclerView filterRecycler;
    private FilterAdapter filterAdapter;

    static {
        if (!OpenCVLoader.initDebug()) {
            // Log lỗi OpenCV không load được
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Request quyền
        requestPermissions();

        imageView = findViewById(R.id.imageView);
        bottomSheet = findViewById(R.id.bottomSheet);
        seekBarIntensity = findViewById(R.id.seekBarIntensity);
        seekBarBrightness = findViewById(R.id.seekBarBrightness);
        seekBarContrast = findViewById(R.id.seekBarContrast);
        seekBarSaturation = findViewById(R.id.seekBarSaturation);
        sliderTitle = findViewById(R.id.sliderTitle);

        findViewById(R.id.btnLoadImage).setOnClickListener(v -> openGallery());
        findViewById(R.id.btnSaveImage).setOnClickListener(v -> saveImage());

        filterRecycler = findViewById(R.id.filterRecycler);
        filterRecycler.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        filterAdapter = new FilterAdapter(getFilterList(), this::onFilterSelected);
        filterRecycler.setAdapter(filterAdapter);

        // Các thanh trượt chỉnh sửa
        seekBarIntensity.setOnSeekBarChangeListener(sliderChangeListener);
        seekBarBrightness.setOnSeekBarChangeListener(sliderChangeListener);
        seekBarContrast.setOnSeekBarChangeListener(sliderChangeListener);
        seekBarSaturation.setOnSeekBarChangeListener(sliderChangeListener);

        resetSliders();
    }

    private void resetSliders() {
        seekBarIntensity.setProgress(100);
        seekBarBrightness.setProgress(100);
        seekBarContrast.setProgress(100);
        seekBarSaturation.setProgress(100);
    }

    private void requestPermissions() {
        String[] perms = {Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        List<String> permsToRequest = new ArrayList<>();
        for (String perm : perms) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(perm);
            }
        }
        if (!permsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permsToRequest.toArray(new String[0]), 100);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(intent, SELECT_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK && data != null && requestCode == SELECT_IMAGE) {
            Uri uri = data.getData();
            try {
                originalBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                filteredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                adjustedBitmap = filteredBitmap.copy(Bitmap.Config.ARGB_8888, true);

                imageView.setImageBitmap(originalBitmap);

                // Chuyển Bitmap sang Mat để xử lý OpenCV
                originalMat = new Mat();
                Utils.bitmapToMat(originalBitmap, originalMat);
                Imgproc.cvtColor(originalMat, originalMat, Imgproc.COLOR_RGBA2BGR);

                selectedFilter = 0;
                filterIntensity = 1.0;
                bottomSheet.setVisibility(View.GONE);

                resetSliders();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private List<FilterItem> getFilterList() {
        List<FilterItem> list = new ArrayList<>();
        list.add(new FilterItem(0, "None"));
        list.add(new FilterItem(1, "Grayscale"));
        list.add(new FilterItem(2, "Sepia"));
        list.add(new FilterItem(3, "Invert"));
        return list;
    }

    private void onFilterSelected(FilterItem filter) {
        selectedFilter = filter.id;
        filterIntensity = 1.0;
        resetSliders();

        if (selectedFilter == 0) {
            bottomSheet.setVisibility(View.GONE);
            imageView.setImageBitmap(originalBitmap);
            filteredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            adjustedBitmap = filteredBitmap.copy(Bitmap.Config.ARGB_8888, true);
        } else {
            bottomSheet.setVisibility(View.VISIBLE);
            sliderTitle.setText(filter.name + " - Intensity");
            applyFilters();
        }
    }

    private final SeekBar.OnSeekBarChangeListener sliderChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser) {
                if (seekBar == seekBarIntensity) {
                    filterIntensity = progress / 100.0;
                }
                applyFilters();
            }
        }

        @Override public void onStartTrackingTouch(SeekBar seekBar) { }
        @Override public void onStopTrackingTouch(SeekBar seekBar) { }
    };

    private void applyFilters() {
        if (originalMat == null || originalBitmap == null) return;

        // Áp filter OpenCV
        Mat filteredMat;
        switch (selectedFilter) {
            case 1:
                filteredMat = FilterProcessor.applyGrayscale(originalMat, filterIntensity);
                break;
            case 2:
                filteredMat = FilterProcessor.applySepia(originalMat, filterIntensity);
                break;
            case 3:
                filteredMat = FilterProcessor.applyInvert(originalMat, filterIntensity);
                break;
            default:
                filteredMat = originalMat.clone();
        }

        filteredBitmap = Bitmap.createBitmap(filteredMat.cols(), filteredMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(filteredMat, filteredBitmap);

        // Áp chỉnh sáng, tương phản, bão hòa
        float brightness = (seekBarBrightness.getProgress() - 100) * 255f / 100f; // -255 to +255
        float contrast = seekBarContrast.getProgress() / 100f; // 0..2 (default 1)
        float saturation = seekBarSaturation.getProgress() / 100f; // 0..2 (default 1)

        adjustedBitmap = applyBrightnessContrastSaturation(filteredBitmap, brightness, contrast, saturation);
        imageView.setImageBitmap(adjustedBitmap);
    }

    private Bitmap applyBrightnessContrastSaturation(Bitmap bmp, float brightness, float contrast, float saturation) {
        ColorMatrix cm = new ColorMatrix();

        // Contrast
        float scale = contrast;
        float translate = (-0.5f * scale + 0.5f) * 255f;
        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                scale, 0, 0, 0, translate,
                0, scale, 0, 0, translate,
                0, 0, scale, 0, translate,
                0, 0, 0, 1, 0
        });

        cm.postConcat(contrastMatrix);

        // Brightness
        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{
                1, 0, 0, 0, brightness,
                0, 1, 0, 0, brightness,
                0, 0, 1, 0, brightness,
                0, 0, 0, 1, 0
        });
        cm.postConcat(brightnessMatrix);

        // Saturation
        ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(saturation);
        cm.postConcat(saturationMatrix);

        Bitmap ret = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(ret);
        Paint paint = new Paint();
        paint.setColorFilter(new ColorMatrixColorFilter(cm));
        canvas.drawBitmap(bmp, 0, 0, paint);
        return ret;
    }

    private void saveImage() {
        if (adjustedBitmap == null) {
            Toast.makeText(this, "Không có ảnh để lưu", Toast.LENGTH_SHORT).show();
            return;
        }
        String savedURL = MediaStore.Images.Media.insertImage(getContentResolver(), adjustedBitmap, "FilteredImage", "Image edited by app");
        if (savedURL != null) {
            Toast.makeText(this, "Đã lưu ảnh", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Lưu ảnh thất bại", Toast.LENGTH_SHORT).show();
        }
    }

    // Adapter filter đơn giản
    static class FilterItem {
        int id;
        String name;

        FilterItem(int id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    interface OnFilterClick {
        void onClick(FilterItem item);
    }
    static class FilterAdapter extends RecyclerView.Adapter<FilterAdapter.ViewHolder> {
        List<FilterItem> filters;
        OnFilterClick listener;

        FilterAdapter(List<FilterItem> filters, OnFilterClick listener) {
            this.filters = filters;
            this.listener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            // Sử dụng LayoutInflater để tạo nút từ XML
            Button btn = new Button(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(250, ViewGroup.LayoutParams.MATCH_PARENT);
            btn.setLayoutParams(params);git fetch origin
            btn.setAllCaps(false);
            btn.setTextSize(14f);
            btn.setTextColor(Color.WHITE);
            btn.setBackgroundColor(Color.parseColor("#3F51B5")); // màu xanh
            btn.setPadding(10, 10, 10, 10);
            return new ViewHolder(btn);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            FilterItem item = filters.get(position);
            Button btn = (Button) holder.itemView;
            btn.setText(item.name);
            btn.setOnClickListener(v -> listener.onClick(item));
        }

        @Override
        public int getItemCount() {
            return filters.size();
        }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ViewHolder(View itemView) {
                super(itemView);
            }
        }
    }
}
