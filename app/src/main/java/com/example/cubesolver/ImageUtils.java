package com.example.cubesolver;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Log;
import androidx.core.content.FileProvider;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * ImageUtils is a helper class that provides utility methods for handling all image-related operations in the app.
 * This includes creating temporary image files, compressing images to reduce their size,
 * saving images from the gallery, converting images to Base64 for API transmission,
 * and cleaning up unused temporary files.
 * Encapsulating this logic in a separate class makes the Activities cleaner and more focused on UI and state management.
 */
public class ImageUtils {

    private static final String TAG = "ImageUtils";
    private Context context;

    /**
     * Constructor for ImageUtils.
     * @param context The context of the calling activity, required for file operations and content resolving.
     */
    public ImageUtils(Context context) {
        this.context = context;
    }

    /**
     * Creates a temporary image file in the app's external pictures directory.
     * The file name includes a timestamp to ensure uniqueness.
     * These files are private to the app and will be deleted if the app is uninstalled.
     * @return A File object representing the newly created temporary file.
     * @throws IOException If the file could not be created.
     */
    public File createImageFile() throws IOException {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        // Use getExternalFilesDir for app-specific files that are removed on uninstall.
        File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (storageDir == null) {
            throw new IOException("Could not get external pictures directory.");
        }
        return File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );
    }

    /**
     * Compresses an image by resizing it and reducing its quality.
     * This is crucial for reducing the size of the images before sending them to the API.
     * @param imageUri The URI of the image to be compressed.
     */
    public void compressAndSaveImage(Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return;
            Bitmap originalBitmap = BitmapFactory.decodeStream(inputStream);
            inputStream.close();

            if (originalBitmap == null) return;

            // Resize the bitmap to half its original dimensions.
            int width = originalBitmap.getWidth() / 2;
            int height = originalBitmap.getHeight() / 2;
            width = Math.max(1, width);
            height = Math.max(1, height);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(originalBitmap, width, height, true);

            // Compress the resized bitmap to a JPEG with 40% quality.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream);

            // Write the compressed data back to the original file URI.
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(imageUri, "w");
            if (pfd != null) {
                FileOutputStream fileOutputStream = new FileOutputStream(pfd.getFileDescriptor());
                fileOutputStream.write(outputStream.toByteArray());
                fileOutputStream.close();
                pfd.close();
            }

            // Recycle bitmaps to free up memory immediately.
            originalBitmap.recycle();
            resizedBitmap.recycle();

        } catch (Exception e) {
            Log.e(TAG, "Error compressing image: " + e.getMessage(), e);
        }
    }

    /**
     * Deletes all temporary image files created by the app that are no longer in use.
     * This is a cleanup operation to prevent the app from consuming unnecessary storage space.
     * @param activeUris A list of URIs for the images that are currently in use and should not be deleted.
     */
    public void deleteUnusedTemporaryFiles(List<Uri> activeUris) {
        try {
            File storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES);
            if (storageDir != null && storageDir.exists()) {
                File[] files = storageDir.listFiles();
                if (files != null) {
                    List<String> activeUriStrings = new ArrayList<>();
                     for (Uri uri : activeUris) {
                         activeUriStrings.add(uri.toString());
                     }

                    for (File file : files) {
                         Uri fileUri = FileProvider.getUriForFile(context, "com.example.cubesolver.fileprovider", file);
                         // Delete the file if it's not in the active list and is one of our temp files.
                         if (!activeUriStrings.contains(fileUri.toString()) && file.getName().startsWith("JPEG_")) {
                             if (file.delete()) {
                                 Log.d(TAG, "Deleted unused file: " + file.getAbsolutePath());
                             }
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error deleting unused temporary files", e);
        }
    }


    /**
     * Saves an image selected from the gallery to the app's private storage.
     * This is necessary because gallery URIs can be temporary. By creating a local copy,
     * the app ensures it has persistent access to the image.
     * @param sourceUri The content URI of the image selected from the gallery.
     * @return The content URI of the newly saved local copy of the image.
     */
    public Uri saveGalleryImage(Uri sourceUri) {
        try {
            File destFile = createImageFile();
            InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
            if (inputStream == null) return null;

            FileOutputStream outputStream = new FileOutputStream(destFile);
            // Use Apache Commons IO to easily copy the stream.
            IOUtils.copy(inputStream, outputStream);

            inputStream.close();
            outputStream.close();

            Uri savedUri = FileProvider.getUriForFile(context, "com.example.cubesolver.fileprovider", destFile);

            // Compress the newly saved image to reduce its size.
            compressAndSaveImage(savedUri);

            return savedUri;
        } catch (Exception e) {
            Log.e(TAG, "Error saving gallery image", e);
            return null;
        }
    }

    /**
     * Converts an image to a Base64 encoded string with aggressive compression.
     * This is used to prepare images for the combined API call where multiple images are sent at once.
     * @param imageUri The URI of the image to convert.
     * @return The Base64 encoded string representation of the image.
     */
    public String imageToBase64WithEnhancedCompression(Uri imageUri) {
        try {
            InputStream inputStream = context.getContentResolver().openInputStream(imageUri);
            if (inputStream == null) return null;

            // Decode the bitmap with a reduced sample size to save memory.
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 4;
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream, null, options);
            inputStream.close();

            if (bitmap == null) return null;

            // Resize the bitmap to a maximum size of 300x300.
            int maxSize = 300;
            float scale = Math.min(((float) maxSize) / bitmap.getWidth(), ((float) maxSize) / bitmap.getHeight());
            int newWidth = Math.round(bitmap.getWidth() * scale);
            int newHeight = Math.round(bitmap.getHeight() * scale);
            newWidth = Math.max(1, newWidth);
            newHeight = Math.max(1, newHeight);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            bitmap.recycle();

            // Compress to JPEG with 80% quality.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
            resizedBitmap.recycle();

            // Encode the byte array to a Base64 string.
            byte[] imageBytes = outputStream.toByteArray();
            String base64String = new String(Base64.encodeBase64(imageBytes));

            // If the string is still too large, apply even more compression.
            if (base64String.length() > 500000) {
                return compressBase64Further(imageBytes);
            }

            return base64String;
        } catch (Exception e) {
            Log.e(TAG, "Error converting image to base64 with enhanced compression", e);
            return null;
        }
    }

    /**
     * Applies a second, more aggressive round of compression if the initial compression was not sufficient.
     * @param imageBytes The byte array of the already compressed image.
     * @return A new, even smaller Base64 encoded string.
     */
    private String compressBase64Further(byte[] imageBytes) {
        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
            if (bitmap == null) return null;

            // Resize to an even smaller size.
            int maxSize = 200;
            float scale = Math.min(((float) maxSize) / bitmap.getWidth(), ((float) maxSize) / bitmap.getHeight());
            int newWidth = Math.round(bitmap.getWidth() * scale);
            int newHeight = Math.round(bitmap.getHeight() * scale);
            newWidth = Math.max(1, newWidth);
            newHeight = Math.max(1, newHeight);
            Bitmap resizedBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true);
            bitmap.recycle();

            // Compress with very low quality.
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 40, outputStream);
            resizedBitmap.recycle();

            byte[] compressedBytes = outputStream.toByteArray();
            return new String(Base64.encodeBase64(compressedBytes));
        } catch (Exception e) {
            Log.e(TAG, "Error applying further compression", e);
            return null;
        }
    }
}
