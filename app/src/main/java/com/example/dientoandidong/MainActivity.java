package com.example.dientoandidong;

import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.*;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.view.View;

import org.opencv.android.OpenCVLoader;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private static final int PICK_IMAGE_REQUEST = 1;
    private ImageView imageView;
    private Bitmap originalBitmap;
    private RadioGroup filterGroup;
    private LinearLayout vignetteControls;
    private LinearLayout pixelateOptionsLayout;
    private RadioGroup blockSizeGroup,blockShapeGroup;
    private SeekBar seekRadius, seekStrength;
    private TextView txtRadius, txtStrength;

    static {
        if (!OpenCVLoader.initDebug()) {
            System.loadLibrary("opencv_java4");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = findViewById(R.id.imageView);
        filterGroup = findViewById(R.id.filterGroup);
        ImageButton btnPickImage = findViewById(R.id.btnPickImage);

        // Liên kết các thành phần UI cho Vignette filter
        seekRadius = findViewById(R.id.seekRadius);
        seekStrength = findViewById(R.id.seekStrength);
        txtRadius = findViewById(R.id.txtRadius);
        txtStrength = findViewById(R.id.txtStrength);

        // Liên kết cho pixelate filter
        pixelateOptionsLayout = findViewById(R.id.pixelateOptionsLayout);
        blockSizeGroup = findViewById(R.id.blockSizeGroup);
        blockShapeGroup = findViewById(R.id.blockShapeGroup);

        // Mở thư viện khi nhấn nút
        btnPickImage.setOnClickListener(v -> openGallery());
        // Áp dụng bộ lọc khi người dùng chọn
        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            applySelectedFilter(checkedId);
        });

        // Thiết lập listener cho thay đổi kích thước khối pixelate
        if (blockSizeGroup != null) {
            blockSizeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                applyPixelateWithSelectedBlockSize();
            });
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        intent.setType("image/*");
        startActivityForResult(Intent.createChooser(intent, "Chọn ảnh"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri imageUri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                originalBitmap = bitmap;  // gán cho biến toàn cục để filter dùng
                imageView.setImageBitmap(bitmap); // hiển thị ảnh gốc lên ImageView
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void applySelectedFilter(int selectedId) {
        if (originalBitmap == null) return;

        try {
            // Hide all control layouts by default
            if (vignetteControls != null) {
                vignetteControls.setVisibility(View.GONE);
            }
            if (pixelateOptionsLayout != null) {
                pixelateOptionsLayout.setVisibility(View.GONE);
            }

            // Apply the appropriate filter based on selection
            if (selectedId == R.id.filterSketch) {
                Bitmap sketchBitmap = ImageFilters.applySketchFilter(originalBitmap);
                imageView.setImageBitmap(sketchBitmap);
            }
            else if (selectedId == R.id.filterEmboss) {
                Bitmap embossBitmap = ImageFilters.applyEmbossFilter(originalBitmap);
                imageView.setImageBitmap(embossBitmap);
            }
            else if (selectedId == R.id.filterVignette) {
                setupVignetteControls();
            }
            else if (selectedId == R.id.filterPixelate) {
                setupPixelateControls();
            }
        } catch (Exception e) {
            Log.e("FilterApp", "Error applying filter: " + e.getMessage(), e);
            // Optional: Show error message to user
            // Toast.makeText(this, "Error applying filter", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Set up the pixelate controls and listeners
     */
    private void setupPixelateControls() {
        // Show controls
        if (pixelateOptionsLayout != null) {
            pixelateOptionsLayout.setVisibility(View.VISIBLE);
        }

        // Check default radio button if none selected
        if (blockSizeGroup.getCheckedRadioButtonId() == -1) {
            // Set default block size
            RadioButton defaultBlockSize = findViewById(R.id.blockSize10);
            if (defaultBlockSize != null) {
                defaultBlockSize.setChecked(true);
            }
        }

        // Apply pixelate with current selection
        applyPixelateWithSelectedBlockSize();
    }

    /**
     * Apply pixelate filter with currently selected block size
     */
    private void applyPixelateWithSelectedBlockSize() {
        if (originalBitmap == null || blockSizeGroup == null) return;

        // Get selected block size
        int blockSize = 10;
        // Default
        int selectedId = blockSizeGroup.getCheckedRadioButtonId();

        if (selectedId == R.id.blockSize5) {
            blockSize = 5;
        } else if (selectedId == R.id.blockSize10) {
            blockSize = 10;
        } else if (selectedId == R.id.blockSize15) {
            blockSize = 15;
        }else if (selectedId == R.id.blockSize20) {
            blockSize = 20;
        } else if (selectedId == R.id.blockSize25) {
            blockSize = 25;
        }


        // --- Lấy hình dạng khối từ lựa chọn ---
        ImageFilters.BlockShape shape = ImageFilters.BlockShape.SQUARE; // Default
        int selectedShapeId = blockShapeGroup.getCheckedRadioButtonId();
        if (selectedShapeId == R.id.shapeSquare) {
            shape = ImageFilters.BlockShape.SQUARE;
        } else if (selectedShapeId == R.id.shapeTriangle) {
            shape = ImageFilters.BlockShape.TRIANGLE;
        } else if (selectedShapeId == R.id.shapeHexagon) {
            shape = ImageFilters.BlockShape.HEXAGON;
        }

        // Apply pixelate filter with square blocks
        Bitmap pixelatedBitmap = ImageFilters.applyPixelateFilter(originalBitmap, blockSize, shape);

        // Update image view
        if (pixelatedBitmap != null) {
            imageView.setImageBitmap(pixelatedBitmap);
        }
    }

    /**
     * Set up the vignette controls and listeners
     */
    private void setupVignetteControls() {
        // Initialize vignette controls if needed
        if (vignetteControls == null) {
            vignetteControls = findViewById(R.id.vignetteControls);
        }

        // Show controls
        vignetteControls.setVisibility(View.VISIBLE);

        // Apply initial vignette effect
        applyVignetteWithCurrentValues();

        // Set up radius seekbar listener
        if (seekRadius != null) {
            seekRadius.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateRadiusDisplay(progress / 100.0f);
                    if (fromUser) {
                        applyVignetteWithCurrentValues();
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }

        // Set up strength seekbar listener
        if (seekStrength != null) {
            seekStrength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    updateStrengthDisplay(progress / 100.0f);
                    if (fromUser) {
                        applyVignetteWithCurrentValues();
                    }
                }

                @Override public void onStartTrackingTouch(SeekBar seekBar) {}
                @Override public void onStopTrackingTouch(SeekBar seekBar) {}
            });
        }
    }

    /**
     * Update the radius display with formatted value
     */
    private void updateRadiusDisplay(float radius) {
        if (txtRadius != null) {
            txtRadius.setText("Radius: " + String.format("%.2f", radius));
        }
    }

    /**
     * Update the strength display with formatted value
     */
    private void updateStrengthDisplay(float strength) {
        if (txtStrength != null) {
            txtStrength.setText("Strength: " + String.format("%.2f", strength));
        }
    }

    /**
     * Apply vignette filter with current seekbar values
     */
    private void applyVignetteWithCurrentValues() {
        if (originalBitmap == null) return;

        // Get current values from seekbars
        float radius = (seekRadius != null) ? seekRadius.getProgress() / 100.0f : 0.5f;
        float strength = (seekStrength != null) ? seekStrength.getProgress() / 100.0f : 1.0f;

        // Apply vignette filter
        Bitmap filteredBitmap = ImageFilters.applyVignetteFilter(originalBitmap, radius, strength);

        // Update image view
        if (filteredBitmap != null) {
            imageView.setImageBitmap(filteredBitmap);
        }
    }
}