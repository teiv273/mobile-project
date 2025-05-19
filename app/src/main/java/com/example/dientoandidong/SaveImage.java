package com.example.dientoandidong;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SaveImage {
    private static final String TAG = "SaveImage";
    private final Context context;
    private final ImageButton btnSaveImage;
    private final LinearLayout saveOptionsContainer;
    private final Button btnSaveAsNew;
    private final Button btnSaveReplacement;
    private Uri originalImageUri;
    private BitmapProvider bitmapProvider;

    // Interface to get bitmap from MainActivity
    public interface BitmapProvider {
        Bitmap getCurrentBitmap();
    }

    public SaveImage(Context context, ImageButton btnSaveImage, LinearLayout saveOptionsContainer,
                     Button btnSaveAsNew, Button btnSaveReplacement) {
        this.context = context;
        this.btnSaveImage = btnSaveImage;
        this.saveOptionsContainer = saveOptionsContainer;
        this.btnSaveAsNew = btnSaveAsNew;
        this.btnSaveReplacement = btnSaveReplacement;

        setupListeners();
    }

    public void setBitmapProvider(BitmapProvider provider) {
        this.bitmapProvider = provider;
    }

    public void setOriginalImageUri(Uri uri) {
        this.originalImageUri = uri;

        // Update visibility of the replacement button based on whether we have a valid URI
        if (btnSaveReplacement != null) {
            btnSaveReplacement.setVisibility(uri != null ? View.VISIBLE : View.GONE);
        }
    }

    private void setupListeners() {
        btnSaveImage.setOnClickListener(v -> {
            // Toggle visibility of save options container
            if (saveOptionsContainer.getVisibility() == View.VISIBLE) {
                saveOptionsContainer.setVisibility(View.GONE);
            } else {
                saveOptionsContainer.setVisibility(View.VISIBLE);
            }
        });

        btnSaveAsNew.setOnClickListener(v -> {
            saveImageAsNew();
            saveOptionsContainer.setVisibility(View.GONE);
        });

        btnSaveReplacement.setOnClickListener(v -> {
            saveImageAsReplacement();
            saveOptionsContainer.setVisibility(View.GONE);
        });
    }

    public void saveImageAsNew() {
        Bitmap currentBitmap = getCurrentImageBitmap();
        if (currentBitmap == null) {
            Toast.makeText(context, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        String fileName = "IMG_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date()) + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImageOnAndroid10AndAbove(currentBitmap, fileName);
        } else {
            saveImageLegacy(currentBitmap, fileName);
        }
    }

    public void saveImageAsReplacement() {
        if (originalImageUri == null) {
            Toast.makeText(context, "Original image URI not available", Toast.LENGTH_SHORT).show();
            return;
        }

        Bitmap currentBitmap = getCurrentImageBitmap();
        if (currentBitmap == null) {
            Toast.makeText(context, "No image to save", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            // Different approach for different URI types
            if (isContentUri(originalImageUri)) {
                replaceContentUri(originalImageUri, currentBitmap);
            } else if (isFileUri(originalImageUri)) {
                replaceFileUri(originalImageUri, currentBitmap);
            } else {
                // Try the original approach as fallback
                fallbackReplace(originalImageUri, currentBitmap);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error replacing image: " + e.getMessage(), e);
            Toast.makeText(context, "Failed to replace image. Saving as new instead.", Toast.LENGTH_SHORT).show();
            // Fallback to saving as new
            saveImageAsNew();
        }
    }

    private boolean isContentUri(Uri uri) {
        return "content".equals(uri.getScheme());
    }

    private boolean isFileUri(Uri uri) {
        return "file".equals(uri.getScheme());
    }

    private void replaceContentUri(Uri uri, Bitmap bitmap) throws IOException {

        Log.d(TAG, "Attempting to replace image at URI: " + uri);

        ContentResolver resolver = context.getContentResolver();

        OutputStream outputStream = null;
        try {
            outputStream = resolver.openOutputStream(uri, "rwt"); // "rwt" mode: read, write, and truncate
            if (outputStream == null) {
                Log.e(TAG, "OutputStream is null for URI: " + uri);
                throw new IOException("Failed to open output stream for URI: " + uri);
            }

            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            if (!success) {
                throw new IOException("Failed to compress bitmap");
            }

            Toast.makeText(context, "Image replaced successfully", Toast.LENGTH_SHORT).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream: " + e.getMessage(), e);
                }
            }
        }
    }

    private void replaceFileUri(Uri uri, Bitmap bitmap) throws IOException {
        String path = uri.getPath();
        if (path == null) {
            throw new IOException("Invalid file path");
        }

        File file = new File(path);
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            Toast.makeText(context, "Image replaced successfully", Toast.LENGTH_SHORT).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing file output stream: " + e.getMessage(), e);
                }
            }
        }
    }

    private void fallbackReplace(Uri uri, Bitmap bitmap) throws IOException {
        ContentResolver resolver = context.getContentResolver();

        OutputStream outputStream = null;
        try {
            outputStream = resolver.openOutputStream(uri);
            if (outputStream == null) {
                throw new IOException("Failed to open output stream");
            }

            boolean success = bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
            if (!success) {
                throw new IOException("Failed to compress bitmap");
            }

            Toast.makeText(context, "Image replaced successfully", Toast.LENGTH_SHORT).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream: " + e.getMessage(), e);
                }
            }
        }
    }

    public Bitmap getCurrentImageBitmap() {
        if (bitmapProvider != null) {
            return bitmapProvider.getCurrentBitmap();
        }
        return null;
    }

    private void saveImageOnAndroid10AndAbove(Bitmap bitmap, String fileName) {
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
        values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
        values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);

        ContentResolver resolver = context.getContentResolver();
        Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        if (imageUri != null) {
            OutputStream outputStream = null;
            try {
                outputStream = resolver.openOutputStream(imageUri);
                if (outputStream != null) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    Toast.makeText(context, "Image saved successfully", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Log.e(TAG, "Error saving image: " + e.getMessage(), e);
                Toast.makeText(context, "Failed to save image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            } finally {
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        Log.e(TAG, "Error closing output stream: " + e.getMessage(), e);
                    }
                }
            }
        } else {
            Toast.makeText(context, "Failed to create new image file", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveImageLegacy(Bitmap bitmap, String fileName) {
        File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        if (!directory.exists()) {
            boolean created = directory.mkdirs();
            if (!created) {
                Toast.makeText(context, "Failed to create directory", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        File imageFile = new File(directory, fileName);
        FileOutputStream outputStream = null;

        try {
            outputStream = new FileOutputStream(imageFile);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

            // Add the image to the gallery
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DATA, imageFile.getAbsolutePath());
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            Toast.makeText(context, "Image saved successfully", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            Log.e(TAG, "Error saving image: " + e.getMessage(), e);
            Toast.makeText(context, "Failed to save image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error closing output stream: " + e.getMessage(), e);
                }
            }
        }
    }
}