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
    private LinearLayout pixelateControls, oilPaintControls;
    private RadioGroup blockSizeGroup, blockShapeGroup;
    private RadioGroup radiusGroup, intensityLevelsGroup;
    private Uri currentImageUri;
    private SaveImage saveImageHandler;
    private Bitmap currentFilteredBitmap;

    static {
        if (!OpenCVLoader.initDebug()) {
            System.loadLibrary("opencv_java4");
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize basic UI elements
        imageView = findViewById(R.id.imageView);
        filterGroup = findViewById(R.id.filterGroup);
        ImageButton btnPickImage = findViewById(R.id.btnPickImage);

        // Initialize pixelate controls
        pixelateControls = findViewById(R.id.pixelateControls);
        blockSizeGroup = findViewById(R.id.blockSizeGroup);
        blockShapeGroup = findViewById(R.id.blockShapeGroup);

        // Initialize oil paint controls
        oilPaintControls = findViewById(R.id.oilPaintControls);
        radiusGroup = findViewById(R.id.radiusGroup);
        intensityLevelsGroup = findViewById(R.id.intensityLevelsGroup);

        // Initialize save image controls
        ImageButton btnSaveImage = findViewById(R.id.btnSaveImage);
        LinearLayout saveOptionsContainer = findViewById(R.id.saveOptionsContainer);
        Button btnSaveAsNew = findViewById(R.id.btnSaveAsNew);
        Button btnSaveReplacement = findViewById(R.id.btnSaveReplacement);

        // Initialize SaveImage handler with our UI components
        saveImageHandler = new SaveImage(this, btnSaveImage, saveOptionsContainer,
                btnSaveAsNew, btnSaveReplacement);

        // Set the bitmap provider to get the current bitmap from MainActivity
        saveImageHandler.setBitmapProvider(new SaveImage.BitmapProvider() {
            @Override
            public Bitmap getCurrentBitmap() {
                // Return the current filtered bitmap if available, otherwise return the original bitmap
                return (currentFilteredBitmap != null) ? currentFilteredBitmap : originalBitmap;
            }
        });

        // Set default visibility
        if (pixelateControls != null) {
            pixelateControls.setVisibility(View.GONE);
        }
        if (oilPaintControls != null) {
            oilPaintControls.setVisibility(View.GONE);
        }
        if (saveOptionsContainer != null) {
            saveOptionsContainer.setVisibility(View.GONE);
        }

        // Set up listeners for pixelate block size and shape
        if (blockSizeGroup != null) {
            blockSizeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                applyPixelateWithSelectedBlockSize();
            });
        }
        if (blockShapeGroup != null) {
            blockShapeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                applyPixelateWithSelectedBlockSize();
            });
        }
        if (radiusGroup != null) {
            radiusGroup.setOnCheckedChangeListener((group, checkedId) -> {
                applyOilPaintWithSelectedParameters();
            });
        }
        if (intensityLevelsGroup != null) {
            intensityLevelsGroup.setOnCheckedChangeListener((group, checkedId) -> {
                applyOilPaintWithSelectedParameters();
            });
        }

        // Mở thư viện khi nhấn nút
        btnPickImage.setOnClickListener(v -> openGallery());

        // Áp dụng bộ lọc khi người dùng chọn
        filterGroup.setOnCheckedChangeListener((group, checkedId) -> {
            // First hide all control panels
            hideAllControlPanels();
            // Then apply the selected filter
            applySelectedFilter(checkedId);
        });
    }

    private void hideAllControlPanels() {
        if (pixelateControls != null) {
            pixelateControls.setVisibility(View.GONE);
        }
        if (oilPaintControls != null) {
            oilPaintControls.setVisibility(View.GONE);
        }
        // Also hide save options when switching filters
        LinearLayout saveOptionsContainer = findViewById(R.id.saveOptionsContainer);
        if (saveOptionsContainer != null) {
            saveOptionsContainer.setVisibility(View.GONE);
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
            currentImageUri = imageUri;

            // Update the SaveImage handler with the new URI
            saveImageHandler.setOriginalImageUri(imageUri);

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(this.getContentResolver(), imageUri);
                originalBitmap = bitmap;  // gán cho biến toàn cục để filter dùng
                currentFilteredBitmap = null; // Reset the filtered bitmap
                imageView.setImageBitmap(bitmap); // hiển thị ảnh gốc lên ImageView

                // Reset filter selection when new image is loaded
                filterGroup.clearCheck();
                hideAllControlPanels();
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(this, "Không thể tải ảnh", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void applySelectedFilter(int selectedId) {
        if (originalBitmap == null) {
            Toast.makeText(this, "Vui lòng chọn ảnh trước", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Apply the appropriate filter based on selection
            if (selectedId == R.id.filterSketch) {
                currentFilteredBitmap = ImageFilters.applySketchFilter(originalBitmap);
                imageView.setImageBitmap(currentFilteredBitmap);
            } else if (selectedId == R.id.filterEmboss) {
                currentFilteredBitmap = ImageFilters.applyEmbossFilter(originalBitmap);
                imageView.setImageBitmap(currentFilteredBitmap);
            } else if (selectedId == R.id.filterPixelate) {
                setupPixelateControls();
            } else if (selectedId == R.id.filterOilPaint) {
                setupOilPaintControls();
            }
        } catch (Exception e) {
            Log.e("FilterApp", "Error applying filter: " + e.getMessage(), e);
            Toast.makeText(this, "Lỗi khi áp dụng bộ lọc: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void setupPixelateControls() {
        // Show controls
        if (pixelateControls != null) {
            pixelateControls.setVisibility(View.VISIBLE);

            // Check if there's any selection already, if not set defaults
            if (blockSizeGroup.getCheckedRadioButtonId() == -1) {
                // Set default block size
                RadioButton defaultBlockSize = findViewById(R.id.blockSize10);
                if (defaultBlockSize != null) {
                    defaultBlockSize.setChecked(true);
                }
            }

            if (blockShapeGroup.getCheckedRadioButtonId() == -1) {
                // Set default block shape
                RadioButton defaultShape = findViewById(R.id.shapeSquare);
                if (defaultShape != null) {
                    defaultShape.setChecked(true);
                }
            }

            // Apply pixelate with current selections
            applyPixelateWithSelectedBlockSize();
        } else {
            Toast.makeText(this, "Không tìm thấy pixelate controls trong layout", Toast.LENGTH_SHORT).show();
            Log.e("FilterApp", "pixelateControls is null. Make sure it's defined in your layout.");
        }
    }

    private void applyPixelateWithSelectedBlockSize() {
        if (originalBitmap == null) return;
        if (blockSizeGroup == null || blockShapeGroup == null) {
            Log.e("FilterApp", "Block size or shape group is null");
            return;
        }

        // Get selected block size
        int blockSize = 10; // Default
        int selectedId = blockSizeGroup.getCheckedRadioButtonId();

        if (selectedId == R.id.blockSize5) {
            blockSize = 5;
        } else if (selectedId == R.id.blockSize10) {
            blockSize = 10;
        } else if (selectedId == R.id.blockSize15) {
            blockSize = 15;
        } else if (selectedId == R.id.blockSize20) {
            blockSize = 20;
        } else if (selectedId == R.id.blockSize25) {
            blockSize = 25;
        }

        // Get selected block shape
        ImageFilters.BlockShape shape = ImageFilters.BlockShape.SQUARE; // Default
        int selectedShapeId = blockShapeGroup.getCheckedRadioButtonId();
        if (selectedShapeId == R.id.shapeSquare) {
            shape = ImageFilters.BlockShape.SQUARE;
        } else if (selectedShapeId == R.id.shapeTriangle) {
            shape = ImageFilters.BlockShape.TRIANGLE;
        } else if (selectedShapeId == R.id.shapeHexagon) {
            shape = ImageFilters.BlockShape.HEXAGON;
        }

        // Apply pixelate filter
        currentFilteredBitmap = ImageFilters.applyPixelateFilter(originalBitmap, blockSize, shape);

        // Update image view
        if (currentFilteredBitmap != null) {
            imageView.setImageBitmap(currentFilteredBitmap);
        } else {
            Toast.makeText(this, "Failed to apply pixelate filter", Toast.LENGTH_SHORT).show();
        }
    }

    private void setupOilPaintControls() {
        // Show controls
        if (oilPaintControls != null) {
            oilPaintControls.setVisibility(View.VISIBLE);

            // Check if there's any selection already, if not set defaults
            if (radiusGroup.getCheckedRadioButtonId() == -1) {
                // Set default radius
                RadioButton defaultRadius = findViewById(R.id.radius5);
                if (defaultRadius != null) {
                    defaultRadius.setChecked(true);
                }
            }

            if (intensityLevelsGroup.getCheckedRadioButtonId() == -1) {
                // Set default intensity levels
                RadioButton defaultIntensity = findViewById(R.id.intensity20);
                if (defaultIntensity != null) {
                    defaultIntensity.setChecked(true);
                }
            }

            // Apply oil paint with current selections
            applyOilPaintWithSelectedParameters();
        } else {
            Toast.makeText(this, "Không tìm thấy oil paint controls trong layout", Toast.LENGTH_SHORT).show();
            Log.e("FilterApp", "oilPaintControls is null. Make sure it's defined in your layout.");
        }
    }

    private void applyOilPaintWithSelectedParameters() {
        if (originalBitmap == null) return;
        if (radiusGroup == null || intensityLevelsGroup == null) {
            Log.e("FilterApp", "Radius or intensity levels group is null");
            return;
        }

        // Get selected radius
        int radius = 5; // Default
        int selectedId = radiusGroup.getCheckedRadioButtonId();

        if (selectedId == R.id.radius3) {
            radius = 3;
        } else if (selectedId == R.id.radius5) {
            radius = 5;
        } else if (selectedId == R.id.radius7) {
            radius = 7;
        } else if (selectedId == R.id.radius10) {
            radius = 10;
        }

        // Get selected intensity levels
        int intensityLevels = 20; // Default
        int selectedIntensityId = intensityLevelsGroup.getCheckedRadioButtonId();
        if (selectedIntensityId == R.id.intensity10) {
            intensityLevels = 10;
        } else if (selectedIntensityId == R.id.intensity20) {
            intensityLevels = 20;
        } else if (selectedIntensityId == R.id.intensity30) {
            intensityLevels = 30;
        } else if (selectedIntensityId == R.id.intensity40) {
            intensityLevels = 40;
        }

        // Apply oil paint filter
        currentFilteredBitmap = ImageFilters.applyOilPaintFilter(originalBitmap, radius, intensityLevels);

        // Update image view
        if (currentFilteredBitmap != null) {
            imageView.setImageBitmap(currentFilteredBitmap);
        } else {
            Toast.makeText(this, "Failed to apply oil paint filter", Toast.LENGTH_SHORT).show();
        }
    }
}