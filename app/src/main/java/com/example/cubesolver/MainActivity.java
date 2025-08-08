package com.example.cubesolver;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.Toast;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.content.FileProvider;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import android.Manifest;
import android.content.pm.PackageManager;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.widget.ImageView;
import android.view.View;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.database.Cursor;
import androidx.documentfile.provider.DocumentFile;
import android.widget.ProgressBar;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.commons.io.IOUtils;
import org.apache.commons.codec.binary.Base64;
import android.graphics.Color;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MainActivity is the main entry point of the application.
 * It handles the user interaction for capturing or selecting 6 images of a Rubik's cube,
 * displaying them, and then sending them to an external API (Gemini) for analysis.
 * The result of the analysis (the color matrices of each face) is then passed to the SolutionActivity.
 */
public class MainActivity extends AppCompatActivity {

    // Constants for request codes used in startActivityForResult
    private static final int REQUEST_IMAGE_CAPTURE = 1; // For camera intent
    private static final int REQUEST_CAMERA_PERMISSION = 2; // For camera permission request
    private static final int REQUEST_GALLERY_IMAGE = 3; // For gallery intent

    // API Key for the Gemini API. IMPORTANT: This should be stored securely, not hardcoded.
    private static final String GEMINI_API_KEY = "AIzaSyBsEWMMMOn7wMMIw-9AFesV_9nlyU7FXkk"; // Replace with your actual API key

    // List to store the URIs of the captured or selected images.
    private List<Uri> imageUris = new ArrayList<>();
    // Counter to keep track of the current photo being taken or retaken.
    private int photoCount = 0;

    // UI elements
    private ImageView[] imageViews; // Array to hold the 6 ImageViews for displaying the cube faces.
    private Button[] retakeButtons; // Array to hold the 6 retake buttons.
    private Button solutionButton; // Button to trigger the image processing.

    // ExecutorService to run network operations on a background thread.
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    // Instance of the ImageUtils helper class for image-related operations.
    private ImageUtils imageUtils;

    // Variables to manage the user's choice of photo source (camera or gallery).
    private int photoSourceChoice = SOURCE_NONE;
    private static final int SOURCE_NONE = -1;
    private static final int SOURCE_CAMERA = 0;
    private static final int SOURCE_GALLERY = 1;

    // Flag to manage the camera retake flow to differentiate it from the initial photo capture sequence.
    private boolean isRetakeCameraFlow = false;

    /**
     * Called when the activity is first created.
     * This is where you should do all of your normal static set up: create views, bind data to lists, etc.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Enable edge-to-edge display for a more immersive UI.
        EdgeToEdge.enable(this);
        // Set the content view to the layout defined in activity_main.xml.
        setContentView(R.layout.activity_main);
        // Apply window insets to handle system bars (status bar, navigation bar).
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Initialize the ImageUtils helper class.
        imageUtils = new ImageUtils(this);

        // Initialize the arrays of ImageViews and retake buttons by finding them in the layout.
        imageViews = new ImageView[]{
            findViewById(R.id.imageView1),
            findViewById(R.id.imageView2),
            findViewById(R.id.imageView3),
            findViewById(R.id.imageView4),
            findViewById(R.id.imageView5),
            findViewById(R.id.imageView6)
        };

        retakeButtons = new Button[]{
            findViewById(R.id.retakeButton1),
            findViewById(R.id.retakeButton2),
            findViewById(R.id.retakeButton3),
            findViewById(R.id.retakeButton4),
            findViewById(R.id.retakeButton5),
            findViewById(R.id.retakeButton6)
        };

        // Initially, hide all ImageViews and retake buttons until photos are taken.
        for (ImageView imageView : imageViews) {
            if (imageView != null) {
                imageView.setVisibility(View.INVISIBLE);
            }
        }
        
        for (Button retakeButton : retakeButtons) {
            if (retakeButton != null) {
                retakeButton.setVisibility(View.INVISIBLE);
            }
        }

        // Set OnClickListeners for each retake button.
        for (int i = 0; i < retakeButtons.length; i++) {
            final int index = i;
            retakeButtons[i].setOnClickListener(v -> retakePhoto(index));
        }
        
        // Initialize the solution button, hide it initially, and set its OnClickListener.
        solutionButton = findViewById(R.id.solutionButton);
        solutionButton.setVisibility(View.GONE);
        solutionButton.setOnClickListener(v -> processCubeImages());

        // Set the OnClickListener for the main "Add Photo" button.
        Button button = findViewById(R.id.button);
        button.setOnClickListener(v -> {
            if (imageUris.size() < 6) {
                photoCount = imageUris.size(); // Set photoCount for the new photo index.

                if (photoSourceChoice == SOURCE_NONE) {
                    // For the first photo, show a dialog to choose between camera and gallery.
                    android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
                    builder.setTitle("Add photo #" + (photoCount + 1));
                    builder.setMessage("Choose how to add this photo");
                    
                    builder.setPositiveButton("Camera", (dialog, which) -> {
                        photoSourceChoice = SOURCE_CAMERA;
                        // Check for camera permission before launching the camera.
                        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                        } else {
                            dispatchTakePictureIntent();
                        }
                    });
                    
                    builder.setNegativeButton("Gallery", (dialog, which) -> {
                        photoSourceChoice = SOURCE_GALLERY;
                        openGallery();
                    });
                    
                    builder.setCancelable(true);
                    builder.show();
                } else {
                    // For subsequent photos, use the previously chosen source.
                    if (photoSourceChoice == SOURCE_CAMERA) {
                         if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                             ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
                         } else {
                            dispatchTakePictureIntent();
                         }
                    } else if (photoSourceChoice == SOURCE_GALLERY) {
                        openGallery();
                    }
                }
            }
        });
    }

    /**
     * Opens the device's gallery for the user to select an image.
     */
    private void openGallery() {
        if (photoCount < 6) {
            String faceInstruction = getFaceInstruction(photoCount);
            String toastMessage;

            boolean isNewPhoto = (photoCount == imageUris.size());

            if (isNewPhoto) {
                toastMessage = "Select photo " + (photoCount + 1) + " of 6 for " + faceInstruction;
            } else {
                toastMessage = "Select replacement for " + faceInstruction + " (Photo #" + (photoCount + 1) + ")";
            }
            
            Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
            
            // Create an intent to open the gallery.
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            intent.setType("image/*");
            startActivityForResult(intent, REQUEST_GALLERY_IMAGE);
        }
    }

    /**
     * Callback for the result from requesting permissions.
     * @param requestCode The request code passed in requestPermissions(android.app.Activity, String[], int).
     * @param permissions The requested permissions. Never null.
     * @param grantResults The grant results for the corresponding permissions which is either PERMISSION_GRANTED or PERMISSION_DENIED. Never null.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // If permission is granted, dispatch the take picture intent.
                dispatchTakePictureIntent();
            } else {
                Toast.makeText(this, "Camera permission is required to take photos", Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Creates an intent to launch the camera app and take a picture.
     * The image will be saved to a temporary file.
     */
    private void dispatchTakePictureIntent() {
        if (photoCount >= 6 && !this.isRetakeCameraFlow) { 
             return;
        }

        try {
            Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            // Set some extras to reduce the image size and quality, which helps with API upload size.
            takePictureIntent.putExtra(MediaStore.EXTRA_SIZE_LIMIT, 1024 * 1024); 
            takePictureIntent.putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 0); 
            
            if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
                File photoFile = null;
                try {
                    // Create a temporary file to store the image.
                    photoFile = imageUtils.createImageFile();
                } catch (IOException ex) {
                    Log.e("CubeSolver", "Error occurred while creating the file", ex);
                    Toast.makeText(this, "Error creating image file", Toast.LENGTH_SHORT).show();
                    if(isRetakeCameraFlow) isRetakeCameraFlow = false;
                    return; 
                }

                if (photoFile != null) {
                    // Get a content URI for the file using FileProvider.
                    Uri newPhotoURI = FileProvider.getUriForFile(this,
                            "com.example.cubesolver.fileprovider",
                            photoFile);
                    
                    String faceInstruction = getFaceInstruction(photoCount);
                    String toastMessage;

                    if (this.isRetakeCameraFlow) { 
                        toastMessage = "Retaking photo " + (photoCount + 1) + ": " + faceInstruction;
                        // If it's a retake, replace the URI at the specified index.
                        if (photoCount < imageUris.size()) {
                            imageUris.set(photoCount, newPhotoURI);
                        } else { 
                            this.isRetakeCameraFlow = false;
                            return;
                        }
                    } else {
                        toastMessage = "Taking photo " + (photoCount + 1) + " of 6: " + faceInstruction;
                        // If it's a new photo, add the URI to the list.
                        if (photoCount == imageUris.size() && imageUris.size() < 6) {
                           imageUris.add(newPhotoURI);
                        } else {
                            // Fallback logic in case of state mismatch.
                            if(photoCount < imageUris.size() && imageUris.size() < 6) {
                                imageUris.set(photoCount, newPhotoURI);
                            } else if (imageUris.size() < 6) {
                                imageUris.add(newPhotoURI);
                                photoCount = imageUris.size() -1;
                            } else {
                                return;
                            }
                        }
                    }
                    
                    Toast.makeText(this, toastMessage, Toast.LENGTH_SHORT).show();
                    // Pass the URI to the camera app, so it saves the image to our file.
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, newPhotoURI);
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
                }
            } else {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
                 if(isRetakeCameraFlow) isRetakeCameraFlow = false;
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error dispatching camera intent: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
            if(isRetakeCameraFlow) isRetakeCameraFlow = false;
        }
    }

    /**
     * Handles the retake photo action for a specific index.
     * Shows a dialog to choose between camera and gallery for the retake.
     * @param index The index of the photo to be retaken.
     */
    private void retakePhoto(int index) {
        photoCount = index; // Set the global photoCount to the index being retaken.
        
        android.app.AlertDialog.Builder builder = new android.app.AlertDialog.Builder(this);
        builder.setTitle("Replace photo #" + (index + 1) + " (" + getFaceInstruction(index) + ")");
        builder.setMessage("Choose how to replace this photo");
        
        builder.setPositiveButton("Camera", (dialog, which) -> {
            this.isRetakeCameraFlow = true; // Set the flag for camera retake flow.
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA_PERMISSION);
            } else {
                dispatchTakePictureIntent();
            }
        });
        
        builder.setNegativeButton("Gallery", (dialog, which) -> {
            openGallery(); 
        });
        
        builder.setCancelable(true);
        builder.show();
    }
    
    /**
     * This method is now deprecated in favor of the unified retakePhoto method.
     * It was previously used to handle camera retakes specifically.
     * @param retakeIndex The index of the photo to retake.
     */
    @Deprecated
    private void retakeWithCamera(int retakeIndex) {
        // This method's logic has been integrated into dispatchTakePictureIntent and retakePhoto.
    }
    
    /**
     * This method is now deprecated in favor of the unified retakePhoto method.
     * It was previously used to handle gallery retakes specifically.
     * @param retakeIndex The index of the photo to retake.
     */
    @Deprecated
    private void retakeWithGallery(int retakeIndex) {
        // This method's logic has been integrated into openGallery and retakePhoto.
    }

    /**
     * Called when an activity you launched exits, giving you the requestCode you started it with, the resultCode it returned, and any additional data from it.
     * @param requestCode The integer request code originally supplied to startActivityForResult(), allowing you to identify who this result came from.
     * @param resultCode The integer result code returned by the child activity through its setResult().
     * @param data An Intent, which can return result data to the caller (various data can be attached to Intent "extras").
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (resultCode == RESULT_OK) {
            try {
                if (requestCode == REQUEST_IMAGE_CAPTURE) { // Result from Camera
                    Uri currentPhotoUri = null;
                    if (photoCount < imageUris.size()) {
                        currentPhotoUri = imageUris.get(photoCount);
                    }

                    if (currentPhotoUri == null) {
                        if(isRetakeCameraFlow) isRetakeCameraFlow = false; 
                        return;
                    }
                                        
                    imageUtils.compressAndSaveImage(currentPhotoUri); 
                    displayPhoto(photoCount); 

                    boolean wasThisARetake = this.isRetakeCameraFlow;
                    if (this.isRetakeCameraFlow) {
                        this.isRetakeCameraFlow = false; // Reset the flag after use.
                    }

                    if (wasThisARetake) {
                        if (imageUris.size() >= 6) {
                            showAllPhotosComplete();
                        } else {
                            Toast.makeText(this, getFaceInstruction(photoCount) + " retaken.", Toast.LENGTH_SHORT).show();
                        }
                    } else { // New photo capture
                        // Auto-advance to the next photo if using the camera for the initial sequence.
                        if (imageUris.size() < 6) { 
                            photoCount = imageUris.size();
                            if (photoCount < 6) {
                                if (photoSourceChoice == SOURCE_CAMERA) {
                                    dispatchTakePictureIntent(); 
                                } else {
                                     Toast.makeText(this, "Select or take " + (6 - imageUris.size()) + " more photos.", Toast.LENGTH_LONG).show();
                                }
                            } else { 
                                showAllPhotosComplete();
                            }
                        } else { 
                            showAllPhotosComplete();
                        }
                    }

                } else if (requestCode == REQUEST_GALLERY_IMAGE && data != null) { // Result from Gallery
                    Uri selectedImageUri = data.getData();
                    if (selectedImageUri != null) {
                        Uri savedUri = imageUtils.saveGalleryImage(selectedImageUri);
                        
                        if (savedUri != null) {
                            boolean isAddingNew = (photoCount == imageUris.size() && imageUris.size() < 6);
                            
                            if (isAddingNew) {
                                imageUris.add(savedUri);
                                Toast.makeText(this, getFaceInstruction(photoCount) + " added from gallery.", Toast.LENGTH_SHORT).show();
                            } else { // Replacing an existing photo
                                if (photoCount < imageUris.size()) {
                                    imageUris.set(photoCount, savedUri);
                                    Toast.makeText(this, getFaceInstruction(photoCount) + " replaced from gallery.", Toast.LENGTH_SHORT).show();
                                } else {
                                     imageUris.add(savedUri);
                                }
                            }
                            
                            displayPhoto(photoCount);
                            
                            if (imageUris.size() >= 6) {
                                showAllPhotosComplete();
                            } else if (isAddingNew) {
                                 Toast.makeText(this, "Select or take " +
                                     (6 - imageUris.size()) + " more photos.", Toast.LENGTH_LONG).show();
                            }
                        }
                    }
                }
            } catch (Exception e) {
                Toast.makeText(this, "Error processing image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                e.printStackTrace();
                if(isRetakeCameraFlow) isRetakeCameraFlow = false; 
            }
        } else if (resultCode != RESULT_OK) { // Operation was cancelled
            Toast.makeText(this, "Photo operation cancelled.", Toast.LENGTH_SHORT).show();

            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                // If a new photo capture was cancelled, remove the placeholder URI that was added.
                if (!this.isRetakeCameraFlow && photoCount < imageUris.size() && photoCount == imageUris.size() - 1) {
                    try {
                        imageUris.remove(photoCount);
                    } catch (IndexOutOfBoundsException e) {
                        Log.e("CubeSolver", "Error removing URI placeholder during camera cancellation", e);
                    }
                }
                if (this.isRetakeCameraFlow) {
                    this.isRetakeCameraFlow = false;
                }
            }
        }
    }

    /**
     * Displays the photo at the given index in the corresponding ImageView.
     * @param index The index of the photo to display.
     */
    private void displayPhoto(int index) {
        try {
            Uri photoUri = imageUris.get(index);
            
            imageViews[index].setVisibility(View.VISIBLE);
            retakeButtons[index].setVisibility(View.VISIBLE);
            
            // Load the bitmap from the URI and set it to the ImageView.
            InputStream inputStream = getContentResolver().openInputStream(photoUri);
            Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
            if (bitmap != null) {
                imageViews[index].setImageBitmap(bitmap);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Error displaying photo " + (index + 1) + ": " + e.getMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    /**
     * Deletes any temporary image files that are not in the final list of imageUris.
     * This is a cleanup operation to save storage space.
     */
    private void deleteUnusedTemporaryFiles() {
        imageUtils.deleteUnusedTemporaryFiles(imageUris);
    }

    /**
     * This is the main method for processing the cube images.
     * It is called when the "Give Solution" button is clicked.
     * It sends all 6 images to the Gemini API for analysis and then handles the response.
     */
    private void processCubeImages() {
        if (imageUris.size() != 6) {
            Toast.makeText(this, "Need 6 photos to proceed", Toast.LENGTH_SHORT).show();
            return;
        }

        solutionButton.setEnabled(false);
        solutionButton.setText("Processing...");

        ProgressBar progressBar = findViewById(R.id.progressBar);
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        // Run the network operation on a background thread.
        executorService.execute(() -> {
            try {
                ArrayList<String> finalMatrices = null;
                int tempCubeSize = 3; // Default to 3x3

                if (imageUris.size() == 6) {
                    // Process all 6 faces together in a single API call.
                    String combinedResult = processAllFacesTogether();
                    
                    if (combinedResult != null && !combinedResult.startsWith("Error:")) {
                        // Parse the JSON response from the API.
                        CubeData cubeData = parseMultiFaceResponse(combinedResult);
                        
                        if (cubeData != null) {
                            finalMatrices = cubeData.matrices;
                            tempCubeSize = cubeData.cubeSize;
                        }
                    }
                }

                final ArrayList<String> matricesToSave = finalMatrices;
                final int cubeSizeToSave = tempCubeSize;

                // Update the UI on the main thread.
                runOnUiThread(() -> {
                    solutionButton.setEnabled(true);
                    solutionButton.setText("Give Solution");
                    if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    
                    if (matricesToSave != null && !matricesToSave.isEmpty()) {
                        // Save the matrices and cube size, then open the SolutionActivity.
                        saveMatricesAndOpenActivity(matricesToSave, cubeSizeToSave);
                    } else {
                        Toast.makeText(MainActivity.this, "Image processing failed. Please check logs.", Toast.LENGTH_LONG).show();
                    }
                });

            } catch (Exception e) {
                Log.e("CubeSolver", "Error in processing images", e);
                runOnUiThread(() -> {
                    solutionButton.setEnabled(true);
                    solutionButton.setText("Give Solution");
                     if (progressBar != null) {
                        progressBar.setVisibility(View.GONE);
                    }
                    Toast.makeText(MainActivity.this, "Error processing images", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    /**
     * Converts all 6 images to base64 strings and then calls the Gemini API.
     * @return The response from the Gemini API as a string.
     */
    private String processAllFacesTogether() {
        try {
            List<String> base64Images = new ArrayList<>();
            
            for (int i = 0; i < Math.min(imageUris.size(), 6); i++) {
                String base64Image = imageUtils.imageToBase64WithEnhancedCompression(imageUris.get(i));
                if (base64Image == null) {
                    return "Error: Failed to convert one or more images to base64 for combined analysis";
                }
                base64Images.add(base64Image);
            }
            
            if (base64Images.size() < 6) {
                return "Error: Not enough images for combined analysis. Need 6 but got " + base64Images.size();
            }
            
            runOnUiThread(() -> {
                Toast.makeText(MainActivity.this, "Sending all 6 faces for analysis...", Toast.LENGTH_SHORT).show();
            });
            
            return callGeminiAPIWithMultipleImages(base64Images);
        } catch (Exception e) {
            Log.e("CubeSolver", "Error in combined face processing", e);
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Makes the actual HTTP request to the Gemini API with multiple images.
     * @param base64Images A list of base64 encoded image strings.
     * @return The JSON response from the API as a string.
     */
    private String callGeminiAPIWithMultipleImages(List<String> base64Images) {
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + GEMINI_API_KEY);
            
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            
            // The prompt sent to the Gemini API, asking it to analyze the images and return the color matrices.
            String prompt = "I'm providing you with 6 images of a Rubik's cube, one for each face. " +
                           "This is either a standard 3x3 or a 2x2 Rubik's cube. " +
                           "Analyze these images as a complete set and identify whether it is a 2x2 or a 3x3 cube. " +
                           "Then, for each face, identify the color of each square. " +
                           "IMPORTANT: The standard colors on a Rubik's cube are White, Yellow, Red, Orange, Blue, and Green. " +
                           "CRUCIAL: Be consistent with color identification across all faces. The same color should be given the same name on all faces. " +
                           "Please structure your response in the following JSON format for consistency:\n\n" +
                           "{\n" +
                           "  \"cube_size\": \"2x2\" or \"3x3\",\n" +
                           "  \"faces\": [\n" +
                           "    {\n" +
                           "      \"face_number\": 1,\n" +
                           "      \"matrix\": [\n" +
                           "        // 2x2 matrix for 2x2 cube (e.g., [[\"Color1\", \"Color2\"], [\"Color3\", \"Color4\"]]), " +
                           "        // 3x3 matrix for 3x3 cube (e.g., [[\"Color1\", \"Color2\", \"Color3\"], ...])\n" +
                           "      ]\n" +
                           "    },\n" +
                           "    // Repeat for faces 2-6\n" +
                           "  ]\n" +
                           "}\n\n" +
                           "Remember that the center square of each face in a 3x3 cube indicates the target color for that face in the solved state. For a 2x2, the colors on the four stickers of a face are needed.";
            
            // Build the JSON request body.
            JSONObject requestJson = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();
            
            JSONObject textPart = new JSONObject();
            textPart.put("text", prompt);
            parts.put(textPart);
            
            for (int i = 0; i < base64Images.size(); i++) {
                JSONObject imagePart = new JSONObject();
                JSONObject inlineData = new JSONObject();
                inlineData.put("mime_type", "image/jpeg");
                inlineData.put("data", base64Images.get(i));
                imagePart.put("inline_data", inlineData);
                parts.put(imagePart);
            }
            
            content.put("parts", parts);
            contents.put(content);
            requestJson.put("contents", contents);
            
            String requestBody = requestJson.toString();
            
            // Send the request.
            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = requestBody.getBytes("utf-8");
                os.write(input, 0, input.length);
            }
            
            int responseCode = connection.getResponseCode();
            
            if (responseCode != 200) {
                StringBuilder errorResponse = new StringBuilder();
                try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(connection.getErrorStream(), "utf-8"))) {
                    String responseLine;
                    while ((responseLine = br.readLine()) != null) {
                        errorResponse.append(responseLine.trim());
                    }
                }
                return "Error: API returned status " + responseCode + ": " + errorResponse.toString();
            }
            
            // Read the response.
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), "utf-8"))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
            
            return response.toString();
        } catch (Exception e) {
            Log.e("CubeSolver", "Error in multi-face API call", e);
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * Parses the JSON response from the Gemini API to extract the color matrices and cube size.
     * @param jsonResponse The JSON response string from the API.
     * @return A CubeData object containing the list of matrices and the cube size.
     */
    private CubeData parseMultiFaceResponse(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);
            String text = "";
            String cubeSizeStr = "3x3";
            
            // Extract the text content from the JSON response.
            if (jsonObject.has("candidates")) {
                JSONArray candidates = jsonObject.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject firstCandidate = candidates.getJSONObject(0);
                    if (firstCandidate.has("content")) {
                        JSONObject content = firstCandidate.getJSONObject("content");
                        if (content.has("parts")) {
                            JSONArray parts = content.getJSONArray("parts");
                            if (parts.length() > 0) {
                                JSONObject firstPart = parts.getJSONObject(0);
                                if (firstPart.has("text")) {
                                    text = firstPart.getString("text");
                                } 
                            }
                        }
                    }
                }
            }
            
            // Parse the extracted text as JSON to get the structured data.
            JSONObject responseJson = null;
            try {
                int jsonStart = text.indexOf('{');
                int jsonEnd = text.lastIndexOf('}') + 1;
                
                if (jsonStart >= 0 && jsonEnd > jsonStart) {
                    String jsonText = text.substring(jsonStart, jsonEnd);
                     responseJson = new JSONObject(jsonText);

                    if (responseJson.has("cube_size")) {
                        cubeSizeStr = responseJson.getString("cube_size");
                    }
                    
                    if (responseJson.has("faces")) {
                        JSONArray faces = responseJson.getJSONArray("faces");

                        ArrayList<String> matrices = new ArrayList<>(6);
                         for (int i = 0; i < 6; i++) {
                            matrices.add("");
                        }
                        
                        for (int i = 0; i < faces.length(); i++) {
                            JSONObject face = faces.getJSONObject(i);
                            int faceNumber = face.getInt("face_number");
                            JSONArray matrixArray = face.getJSONArray("matrix");
                            
                            StringBuilder formattedMatrix = new StringBuilder();
                            formattedMatrix.append("Face #").append(faceNumber).append(":\n\n");
                            
                            for (int row = 0; row < matrixArray.length(); row++) {
                                JSONArray rowArray = matrixArray.getJSONArray(row);
                                for (int col = 0; col < rowArray.length(); col++) {
                                    formattedMatrix.append(rowArray.getString(col)).append(" ");
                                }
                                formattedMatrix.append("\n");
                            }
                            
                            int index = faceNumber - 1;
                            if (index >= 0 && index < 6) {
                                matrices.set(index, formattedMatrix.toString());
                            }
                        }

                         if (cubeSizeStr.equals("3x3") && faces.length() > 0 && faces.getJSONObject(0).has("matrix")) {
                              JSONArray firstMatrix = faces.getJSONObject(0).getJSONArray("matrix");
                              if (firstMatrix.length() > 0) {
                                  if (firstMatrix.length() == 2 && firstMatrix.getJSONArray(0).length() == 2) {
                                      cubeSizeStr = "2x2";
                                  } else if (firstMatrix.length() == 3 && firstMatrix.getJSONArray(0).length() == 3) {
                                      cubeSizeStr = "3x3";
                                  }
                              }
                         }
                        
                        boolean allFacesFound = true;
                        for (String matrix : matrices) {
                            if (matrix.isEmpty()) {
                                allFacesFound = false;
                                break;
                            }
                        }
                        
                        if (allFacesFound) {
                             return new CubeData(matrices, cubeSizeStr.equals("2x2") ? 2 : 3);
                        }
                    }
                }
            } catch (Exception e) {
                 // Fallback to text parsing if JSON parsing fails.
            }

            // Fallback text parsing logic.
            ArrayList<String> matrices = new ArrayList<>(6);
            for (int i = 0; i < 6; i++) {
                matrices.add("Face #" + (i + 1) + ":\n\nCould not parse matrix data.");
            }

            if (text != null && !text.isEmpty()) {
                 if (text.contains("2x2") || text.toLowerCase().contains("two by two")) {
                     cubeSizeStr = "2x2";
                 }
            
                String[] faceBlocks = text.split("Face #");
            
                for (int i = 1; i < faceBlocks.length; i++) {
                    String faceBlock = faceBlocks[i].trim();
                
                    int faceNumber = -1;
                     if (!faceBlock.isEmpty() && faceBlock.charAt(0) >= '1' && faceBlock.charAt(0) <= '6') {
                        faceNumber = Character.getNumericValue(faceBlock.charAt(0));
                    }
                
                    if (faceNumber < 1 || faceNumber > 6) {
                        continue;
                    }
                
                    int faceIndex = faceNumber - 1;
                
                    StringBuilder matrixText = new StringBuilder();
                    matrixText.append("Face #").append(faceNumber).append(":\n\n");
                    matrixText.append("Raw response fragment:\n").append(faceBlock).append("\n");
                
                     while (matrices.size() <= faceIndex) {
                          matrices.add("Face #" + (matrices.size() + 1) + ":\n\nCould not parse matrix data.");
                     }

                    matrices.set(faceIndex, matrixText.toString());
                }

                while (matrices.size() < 6) {
                    int faceNumber = matrices.size() + 1;
                     matrices.add("Face #" + faceNumber + ":\n\nCould not parse matrix data.");
                }
            }

             return new CubeData(matrices, cubeSizeStr.equals("2x2") ? 2 : 3);

        } catch (Exception e) {
            Log.e("CubeSolver", "Critical Error parsing multi-face response", e);
            return new CubeData(new ArrayList<>(), 3);
        }
    }
    
    /**
     * Saves the color matrices and cube size to SharedPreferences and opens the SolutionActivity.
     * @param matrices The list of color matrices for each face.
     * @param cubeSize The size of the cube (2 for 2x2, 3 for 3x3).
     */
    private void saveMatricesAndOpenActivity(ArrayList<String> matrices, int cubeSize) {
        try {
            android.content.SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
            android.content.SharedPreferences.Editor editor = prefs.edit();
            
            editor.clear();
            
            editor.putInt("matrix_count", matrices.size());
            for (int i = 0; i < matrices.size(); i++) {
                editor.putString("matrix_" + i, matrices.get(i));
            }

            editor.putInt("cube_size", cubeSize);
            
            editor.putInt("image_count", imageUris.size());
            for (int i = 0; i < imageUris.size(); i++) {
                editor.putString("image_uri_" + i, imageUris.get(i).toString());
            }
            
            editor.apply();
            
            Intent intent = new Intent(MainActivity.this, SolutionActivity.class);
            startActivity(intent);
            
        } catch (Exception e) {
            Log.e("CubeSolver", "Error saving data to SharedPreferences", e);
            Toast.makeText(this, "Error preparing data for next step", Toast.LENGTH_SHORT).show();
        }
    }

     /**
      * A helper class to hold the result of the API analysis, containing both the matrices and the cube size.
      */
     private static class CubeData {
         ArrayList<String> matrices;
         int cubeSize;

         CubeData(ArrayList<String> matrices, int cubeSize) {
             this.matrices = matrices;
             this.cubeSize = cubeSize;
        }
    }

    /**
     * Called when all 6 photos have been successfully added.
     * It updates the UI to hide the "Add Photo" button and show the "Give Solution" button.
     */
    private void showAllPhotosComplete() {
        Toast.makeText(this, "All 6 photos added. Click 'Give Solution'.", Toast.LENGTH_SHORT).show();
        
        Button button = findViewById(R.id.button);
        if (button != null) {
            button.setVisibility(View.GONE);
        }
        
        solutionButton.setVisibility(View.VISIBLE);
        
        deleteUnusedTemporaryFiles();
    }

    /**
     * A helper method to parse color names from a matrix string using regex.
     * @param matrixStr The string containing the matrix data.
     * @return A list of color names found in the string.
     */
    private List<String> parseColorsFromMatrixString(String matrixStr) {
         List<String> colors = new ArrayList<>();
         Pattern colorPattern = Pattern.compile("\\b(White|Yellow|Red|Orange|Blue|Green)\\b", Pattern.CASE_INSENSITIVE);
         Matcher matcher = colorPattern.matcher(matrixStr);

         while (matcher.find()) {
             colors.add(matcher.group(1));
         }
         return colors;
    }

    /**
     * Returns a human-readable instruction for which cube face to capture, based on the index.
     * The order is URFDLB (Up, Right, Front, Down, Left, Back).
     * @param faceIdx The index of the face (0-5).
     * @return A string with the instruction, e.g., "UP face".
     */
    private String getFaceInstruction(int faceIdx) {
        switch (faceIdx) {
            case 0: return "UP face";
            case 1: return "RIGHT face";
            case 2: return "FRONT face";
            case 3: return "DOWN face";
            case 4: return "LEFT face";
            case 5: return "BACK face";
            default: return "face " + (faceIdx + 1);
        }
    }
}
