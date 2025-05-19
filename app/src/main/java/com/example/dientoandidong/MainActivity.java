package com.example.dientoandidong;

import android.Manifest;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final int SELECT_IMAGE = 1;
    private static final int PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "MainActivity";


    private ImageView imageView;
    private LinearLayout bottomSheet;
    private SeekBar seekBarIntensity, seekBarBrightness, seekBarContrast, seekBarSaturation;
    private TextView sliderTitle;

    private Bitmap originalBitmap, filteredBitmap, adjustedBitmap;
    private Mat originalMat;
    private Mat displayMat;


    private double filterIntensity = 1.0;

    private int selectedFilter = 0;

    private RecyclerView filterRecycler;
    private FilterAdapter filterAdapter;

    static {
        if (OpenCVLoader.initDebug()) {
            Log.i(TAG, "OpenCV loaded successfully");
        } else {
            Log.e(TAG, "OpenCV initialization failed!");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            permissions = new String[]{Manifest.permission.READ_MEDIA_IMAGES};
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};
        } else {
            permissions = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        }

        List<String> permsToRequest = new ArrayList<>();
        for (String perm : permissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                permsToRequest.add(perm);
            }
        }
        // Quyền WRITE_EXTERNAL_STORAGE chỉ cần cho Android < Q khi lưu không qua MediaStore
        // Vì đang dùng MediaStore cho Q+ và cả <Q (với FileOutputStream), nên WRITE vẫn cần cho <Q
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                if(!permsToRequest.contains(Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                    permsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
                }
            }
        }


        if (!permsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(this, permsToRequest.toArray(new String[0]), PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (!allGranted) {
                Toast.makeText(this, "Cần cấp quyền để ứng dụng hoạt động!", Toast.LENGTH_LONG).show();
            }
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
                if (originalBitmap == null) {
                    Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
                    return;
                }
                filteredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
                adjustedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

                imageView.setImageBitmap(originalBitmap);

                if (originalMat != null) {
                    originalMat.release();
                }
                originalMat = new Mat();
                Utils.bitmapToMat(originalBitmap, originalMat);
                Imgproc.cvtColor(originalMat, originalMat, Imgproc.COLOR_RGBA2BGR);


                selectedFilter = 0;
                filterIntensity = 1.0;
                bottomSheet.setVisibility(View.GONE);
                resetSliders();

            } catch (IOException e) {
                Log.e(TAG, "Lỗi tải ảnh: " + e.getMessage());
                Toast.makeText(this, "Lỗi tải ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private List<FilterItem> getFilterList() {
        List<FilterItem> list = new ArrayList<>();
        list.add(new FilterItem(0, "None"));
        list.add(new FilterItem(1, "Grayscale"));
        list.add(new FilterItem(2, "Sepia"));
        list.add(new FilterItem(3, "Invert"));
        list.add(new FilterItem(4, "Swirl"));
        list.add(new FilterItem(5, "Watercolor"));
        list.add(new FilterItem(6, "Noise"));
        return list;
    }

    private void onFilterSelected(FilterItem filter) {
        selectedFilter = filter.id;
        filterIntensity = 1.0;
        seekBarIntensity.setProgress(100);

        if (originalBitmap == null) {
            Toast.makeText(this, "Vui lòng chọn ảnh trước", Toast.LENGTH_SHORT).show();
            return;
        }

        if (selectedFilter == 0) {
            bottomSheet.setVisibility(View.GONE);
            imageView.setImageBitmap(originalBitmap);
            filteredBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            adjustedBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            resetSliders();
        } else {
            bottomSheet.setVisibility(View.VISIBLE);
            sliderTitle.setText(filter.name + " - Intensity");
            resetSliders();
            filterIntensity = 1.0;
            seekBarIntensity.setProgress(100);
            applyAllAdjustments();
        }
    }

    private final SeekBar.OnSeekBarChangeListener sliderChangeListener = new SeekBar.OnSeekBarChangeListener() {
        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (fromUser && originalMat != null) {
                if (seekBar.getId() == R.id.seekBarIntensity) {
                    filterIntensity = progress / 100.0;
                }
                applyAllAdjustments();
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
        }
    };

    private void applyAllAdjustments() {
        if (originalMat == null || originalBitmap == null) return;

        Mat tempMat = applyOpenCVFilter();

        if (displayMat != null) displayMat.release();
        displayMat = new Mat();
        Imgproc.cvtColor(tempMat, displayMat, Imgproc.COLOR_BGR2RGBA); // Chuyển về RGBA để hiển thị
        // Giải phóng tempMat sau khi đã sử dụng xong để chuyển đổi
        if (tempMat != originalMat && tempMat.getNativeObjAddr() != originalMat.getNativeObjAddr() && tempMat.getNativeObjAddr() != displayMat.getNativeObjAddr()) {
            tempMat.release(); // Chỉ giải phóng nếu nó là một Mat riêng biệt được tạo ra trong applyOpenCVFilter
        }


        filteredBitmap = Bitmap.createBitmap(displayMat.cols(), displayMat.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(displayMat, filteredBitmap);


        float brightness = (seekBarBrightness.getProgress() - 100) * 2.55f;
        float contrast = seekBarContrast.getProgress() / 100f;
        float saturation = seekBarSaturation.getProgress() / 100f;

        adjustedBitmap = applyBrightnessContrastSaturation(filteredBitmap, brightness, contrast, saturation);
        imageView.setImageBitmap(adjustedBitmap);
    }


    private Mat applyOpenCVFilter() {
        if (originalMat == null || originalMat.empty()) {
            Log.e(TAG, "originalMat is null or empty in applyOpenCVFilter");
            // Trả về một Mat rỗng hoặc clone của originalMat (nếu nó không null nhưng rỗng)
            // để tránh NullPointerException sau này.
            // Tuy nhiên, tốt nhất là đảm bảo originalMat luôn hợp lệ.
            // Nếu không có ảnh, không nên gọi hàm này.
            if(originalMat != null) return originalMat.clone();
            return new Mat();
        }


        Mat processedMat;
        Mat currentSrcMat = originalMat.clone(); // Luôn làm việc trên bản sao

        switch (selectedFilter) {
            case 1:
                processedMat = FilterProcessor.applyGrayscale(currentSrcMat, filterIntensity);
                break;
            case 2:
                processedMat = FilterProcessor.applySepia(currentSrcMat, filterIntensity);
                break;
            case 3:
                processedMat = FilterProcessor.applyInvert(currentSrcMat, filterIntensity);
                break;
            case 4:
                processedMat = FilterProcessor.applySwirl(currentSrcMat, filterIntensity);
                break;
            case 5:
                processedMat = FilterProcessor.applyWatercolor(currentSrcMat, filterIntensity);
                break;
            case 6:
                processedMat = FilterProcessor.applyNoise(currentSrcMat, filterIntensity);
                break;
            default:
                processedMat = currentSrcMat.clone(); // Không áp dụng filter, trả về bản sao
                break;
        }
        currentSrcMat.release(); // Giải phóng bản sao đã dùng để xử lý
        return processedMat; // Mat này sẽ được release trong applyAllAdjustments sau khi chuyển sang Bitmap
    }


    private Bitmap applyBrightnessContrastSaturation(Bitmap bmp, float brightness, float contrast, float saturation) {
        ColorMatrix cm = new ColorMatrix();
        float scaleContrast = contrast;
        float translateContrast = (-0.5f * scaleContrast + 0.5f) * 255f;
        ColorMatrix contrastMatrix = new ColorMatrix(new float[]{
                scaleContrast, 0, 0, 0, translateContrast,
                0, scaleContrast, 0, 0, translateContrast,
                0, 0, scaleContrast, 0, translateContrast,
                0, 0, 0, 1, 0
        });
        ColorMatrix brightnessMatrix = new ColorMatrix(new float[]{
                1, 0, 0, 0, brightness,
                0, 1, 0, 0, brightness,
                0, 0, 1, 0, brightness,
                0, 0, 0, 1, 0
        });
        ColorMatrix saturationMatrix = new ColorMatrix();
        saturationMatrix.setSaturation(saturation);

        cm.postConcat(contrastMatrix);
        cm.postConcat(brightnessMatrix);
        cm.postConcat(saturationMatrix);

        Bitmap ret = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), bmp.getConfig());
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

        OutputStream fos = null;
        String imageFileName = "FilteredImage_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
        Uri imageUri = null;

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, imageFileName);
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "YourAppFolder"); // Thay YourAppFolder bằng tên thư mục của bạn

                imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri == null) {
                    throw new IOException("Failed to create new MediaStore record.");
                }
                fos = getContentResolver().openOutputStream(imageUri);
            } else {
                String imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + File.separator + "YourAppFolder"; // Thay YourAppFolder
                File directory = new File(imagesDir);
                if (!directory.exists()) {
                    if(!directory.mkdirs()){
                        Log.e(TAG, "Failed to create directory for saving image.");
                        Toast.makeText(this, "Không thể tạo thư mục lưu ảnh", Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
                File file = new File(imagesDir, imageFileName);
                imageUri = Uri.fromFile(file); // Để thông báo cho MediaScanner biết
                fos = new FileOutputStream(file);
            }

            if (fos != null) {
                adjustedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                Toast.makeText(this, "Đã lưu ảnh: " + imageFileName, Toast.LENGTH_LONG).show();

                // Thông báo cho MediaScanner để ảnh xuất hiện trong thư viện (cho Android < Q)
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q && imageUri != null) {
                    Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                    mediaScanIntent.setData(imageUri);
                    sendBroadcast(mediaScanIntent);
                }
            } else {
                Toast.makeText(this, "Lưu ảnh thất bại: OutputStream is null", Toast.LENGTH_SHORT).show();
            }

        } catch (IOException e) {
            Log.e(TAG, "Lưu ảnh thất bại: " + e.getMessage());
            Toast.makeText(this, "Lưu ảnh thất bại: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing FileOutputStream: " + e.getMessage());
                }
            }
        }
    }


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

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Button btn = new Button(parent.getContext());
            RecyclerView.LayoutParams params = new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
            params.setMarginEnd(16); // Khoảng cách giữa các button
            btn.setLayoutParams(params);
            btn.setAllCaps(false);
            btn.setTextSize(14f);
            btn.setTextColor(Color.WHITE);
            // Sử dụng màu nền đơn giản, cùng màu với RecyclerView hoặc một màu tương phản nhẹ
            btn.setBackgroundColor(Color.parseColor("#3F51B5")); // Màu nền của RecyclerView
            // btn.setBackgroundColor(Color.parseColor("#303F9F")); // Một màu tối hơn một chút
            btn.setPadding(32, 0, 32, 0); // Tăng padding cho dễ nhấn và đẹp hơn
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (originalMat != null) {
            originalMat.release();
            originalMat = null;
        }
        if (displayMat != null) {
            displayMat.release();
            displayMat = null;
        }
    }
}