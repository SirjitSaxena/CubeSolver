package com.example.cubesolver;

import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.card.MaterialCardView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import android.content.Intent;
import org.json.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.function.Function;

/**
 * SolutionActivity is responsible for displaying the results of the cube face analysis from MainActivity.
 * It shows the captured images and the color matrices for each face.
 * It allows the user to manually correct any misidentified colors.
 * Finally, it generates a solver string (compatible with Kociemba's algorithm for 3x3 or a custom format for 2x2)
 * and passes it to the AlgorithmSolutionActivity.
 */
public class SolutionActivity extends AppCompatActivity {

    // Data received from MainActivity
    private ArrayList<String> matrices = new ArrayList<>(); // Stores the string representation of each face's color matrix.
    private ArrayList<Uri> imageUris = new ArrayList<>(); // Stores the URIs of the captured images.
    private int cubeSize = 3; // The size of the cube (e.g., 3 for 3x3, 2 for 2x2). Default is 3.

    // UI and state management
    private ArrayList<View[][]> colorSquares = new ArrayList<>(); // A list of 2D arrays, each holding the View for each color square on a face.
    private Map<String, String> editedColors = new HashMap<>(); // Tracks user's manual color corrections. Key: "face_row_col", Value: "ColorName".

    // Standard Rubik's Cube colors and their RGB values.
    private String[] standardColors = {"White", "Yellow", "Red", "Orange", "Blue", "Green"};
    private int[][] colorValues = {
        {255, 255, 255}, // White
        {255, 255, 0},   // Yellow
        {255, 0, 0},     // Red
        {255, 165, 0},   // Orange
        {0, 0, 255},     // Blue
        {0, 255, 0}      // Green
    };

    /**
     * Called when the activity is first created.
     * @param savedInstanceState If the activity is being re-initialized after previously being shut down then this Bundle contains the data it most recently supplied in onSaveInstanceState(Bundle).
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_solution);

        // Load the cube data (matrices, cube size, image URIs) from SharedPreferences.
        CubeData cubeData = loadMatricesFromPreferences();
        matrices = cubeData.matrices;
        cubeSize = cubeData.cubeSize;
        imageUris = loadImageUrisFromPreferences();
        
        LinearLayout mainContentLayout = findViewById(R.id.mainContentLayout);
        LinearLayout cubeFacesContainer = findViewById(R.id.cubeFacesContainer);
        
        // Initialize buttons and set their click listeners.
        Button backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(v -> finish()); // Go back to the previous activity (MainActivity).
        
        Button saveButton = findViewById(R.id.saveButton);
        saveButton.setOnClickListener(v -> saveEditedColors()); // Save any manual color corrections.
        
        Button proceedButton = findViewById(R.id.proceedButton);
        proceedButton.setOnClickListener(v -> generateKociembaStringAndProceed()); // Generate solver string and move to the next activity.
        
        if (matrices != null && !matrices.isEmpty()) {
            boolean hasErrors = false;
            
            // Dynamically create and add a view for each cube face.
            for (int i = 0; i < matrices.size(); i++) {
                String matrix = matrices.get(i);
                
                if (matrix.contains("Error:")) {
                    hasErrors = true;
                }
                
                // Each face is displayed in a MaterialCardView for better UI.
                MaterialCardView faceCard = new MaterialCardView(this);
                LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                if (i < matrices.size() - 1) { 
                    cardParams.setMargins(0, 0, 0, dpToPx(8)); 
                }
                faceCard.setLayoutParams(cardParams);

                LinearLayout cardContentLayout = new LinearLayout(this);
                cardContentLayout.setOrientation(LinearLayout.VERTICAL);
                cardContentLayout.setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16));
                cardContentLayout.setGravity(Gravity.CENTER_HORIZONTAL);

                TextView titleView = new TextView(this);
                String[] faceNames = {"Up Face (U)", "Right Face (R)", "Front Face (F)", "Down Face (D)", "Left Face (L)", "Back Face (B)"};
                titleView.setText(i < faceNames.length ? faceNames[i] : "Cube Face #" + (i + 1));
                titleView.setTextAppearance(R.style.TextAppearance_App_Headline6);
                LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
                titleParams.setMargins(0, 0, 0, dpToPx(12));
                titleView.setLayoutParams(titleParams);
                cardContentLayout.addView(titleView);
                
                // Add the captured image for this face.
                if (i < imageUris.size() && imageUris.get(i) != null) {
                    ImageView imageView = new ImageView(this);
                    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        dpToPx(200) 
                    );
                    imageParams.setMargins(0,0,0, dpToPx(12));
                    imageView.setLayoutParams(imageParams);
                    imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
                    
                    try {
                        imageView.setImageURI(imageUris.get(i));
                        cardContentLayout.addView(imageView);
                    } catch (Exception e) {
                         // Handle error loading image.
                    }
                }
                
                // Create and add the editable color grid.
                GridLayout colorGrid = createColorGridFromMatrix(matrix, i, cubeSize);
                LinearLayout.LayoutParams gridParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, 
                    LinearLayout.LayoutParams.WRAP_CONTENT
                );
                colorGrid.setLayoutParams(gridParams);
                cardContentLayout.addView(colorGrid);
                
                faceCard.addView(cardContentLayout);
                cubeFacesContainer.addView(faceCard);
            }
            
            // If any of the matrices contained an error message, display a note to the user.
            if (hasErrors) {
                TextView errorNote = new TextView(this);
                errorNote.setText("Note: Some faces couldn't be properly analyzed. Tap on any color square to correct it.");
                errorNote.setTextColor(Color.RED);
                errorNote.setPadding(16, 24, 16, 16);
                mainContentLayout.addView(errorNote);
            }
        } else {
            // Handle case where no data was received.
            TextView errorView = new TextView(this);
            errorView.setText("No cube data was received. Please try again.");
            mainContentLayout.addView(errorView);
        }
    }
    
    /**
     * Creates a GridLayout of colored squares based on the provided matrix string.
     * Each square is clickable to allow for color correction.
     * @param matrixStr The string representation of the color matrix.
     * @param faceIndex The index of the face (0-5).
     * @param cubeSize The size of the cube (2 or 3).
     * @return A GridLayout containing the colored squares.
     */
    private GridLayout createColorGridFromMatrix(String matrixStr, int faceIndex, int cubeSize) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(cubeSize);
        grid.setRowCount(cubeSize);
        grid.setPadding(16, 16, 16, 16);
        
        int squareSize = getResources().getDisplayMetrics().widthPixels / (cubeSize + 2);
        
        List<String> colorNames = parseColorsFromMatrix(matrixStr, cubeSize);
        
        View[][] faceSquares = new View[cubeSize][cubeSize];
        colorSquares.add(faceSquares);
        
        int colorIndex = 0;
        for (int row = 0; row < cubeSize; row++) {
            for (int col = 0; col < cubeSize; col++) {
                View colorSquare = new View(this);
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = squareSize;
                params.height = squareSize;
                params.setMargins(4, 4, 4, 4);
                params.rowSpec = GridLayout.spec(row);
                params.columnSpec = GridLayout.spec(col);
                colorSquare.setLayoutParams(params);
                
                String colorName = (colorIndex < colorNames.size()) ? colorNames.get(colorIndex) : "White";
                
                GradientDrawable shape = new GradientDrawable();
                shape.setShape(GradientDrawable.RECTANGLE);
                shape.setColor(getColorFromName(colorName));
                shape.setStroke(2, Color.BLACK);
                shape.setCornerRadius(8);
                colorSquare.setBackground(shape);
                
                colorSquare.setTag(colorName);
                
                final int finalRow = row;
                final int finalCol = col;
                colorSquare.setOnClickListener(v -> showColorPickerDialog(v, faceIndex, finalRow, finalCol));
                
                grid.addView(colorSquare);
                faceSquares[row][col] = colorSquare;
                colorIndex++;
            }
        }
        
        return grid;
    }
    
    /**
     * Shows a dialog with a list of standard colors for the user to select from.
     * @param colorView The color square View that was clicked.
     * @param faceIndex The index of the face.
     * @param row The row of the square.
     * @param col The column of the square.
     */
    private void showColorPickerDialog(View colorView, int faceIndex, int row, int col) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Select Color");
        
        String currentColor = (String) colorView.getTag();
        int selectedIndex = 0;
        for (int i = 0; i < standardColors.length; i++) {
            if (standardColors[i].equalsIgnoreCase(currentColor)) {
                selectedIndex = i;
                break;
            }
        }
        
        builder.setSingleChoiceItems(standardColors, selectedIndex, (dialog, which) -> {
            String newColor = standardColors[which];
            updateSquareColor(colorView, newColor);
            
            // Track the edited color.
            String key = faceIndex + "_" + row + "_" + col;
            editedColors.put(key, newColor);
            
            dialog.dismiss();
        });
        
        builder.setNegativeButton("Cancel", null);
        builder.show();
    }
    
    /**
     * Updates the background color and tag of a color square View.
     * @param colorView The View to update.
     * @param colorName The new color name.
     */
    private void updateSquareColor(View colorView, String colorName) {
        GradientDrawable shape = (GradientDrawable) colorView.getBackground();
        shape.setColor(getColorFromName(colorName));
        colorView.setTag(colorName);
    }
    
    /**
     * Converts a color name string to its corresponding integer color value.
     * @param colorName The name of the color (e.g., "Red").
     * @return The integer color value.
     */
    private int getColorFromName(String colorName) {
        for (int i = 0; i < standardColors.length; i++) {
            if (standardColors[i].equalsIgnoreCase(colorName)) {
                int[] rgb = colorValues[i];
                return Color.rgb(rgb[0], rgb[1], rgb[2]);
            }
        }
        return Color.GRAY; // Default color if not found.
    }
    
    /**
     * Parses a list of color names from the raw matrix string received from the API.
     * @param matrixStr The raw matrix string.
     * @param cubeSize The size of the cube.
     * @return A list of color names.
     */
    private List<String> parseColorsFromMatrix(String matrixStr, int cubeSize) {
        List<String> colors = new ArrayList<>();
        Pattern colorPattern = Pattern.compile("\\b(White|Yellow|Red|Orange|Blue|Green)\\b", Pattern.CASE_INSENSITIVE);
        Matcher matcher = colorPattern.matcher(matrixStr);
        
        int expectedColors = cubeSize * cubeSize;
        
        while (matcher.find() && colors.size() < expectedColors) {
            colors.add(matcher.group(1));
        }
        
        while (colors.size() < expectedColors) {
            colors.add("White"); // Fill with default if not enough colors were parsed.
        }
        
        return colors;
    }
    
    /**
     * Saves the manually corrected colors back to SharedPreferences.
     * It reconstructs the matrix strings with the corrected colors.
     */
    private void saveEditedColors() {
        if (editedColors.isEmpty()) {
            Toast.makeText(this, "No changes to save.", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            CubeData currentCubeData = loadMatricesFromPreferences();
            ArrayList<String> updatedMatrices = new ArrayList<>(currentCubeData.matrices);
            int currentCubeSize = currentCubeData.cubeSize;
            
            for (int faceIndex = 0; faceIndex < updatedMatrices.size(); faceIndex++) {
                String[][] colorMatrix = new String[currentCubeSize][currentCubeSize];
                
                List<String> originalColors = parseColorsFromMatrix(updatedMatrices.get(faceIndex), currentCubeSize);
                int colorIndex = 0;
                for (int row = 0; row < currentCubeSize; row++) {
                    for (int col = 0; col < currentCubeSize; col++) {
                        colorMatrix[row][col] = (colorIndex < originalColors.size()) ? originalColors.get(colorIndex) : "White";
                        colorIndex++;
                    }
                }
                
                boolean faceEdited = false;
                for (int row = 0; row < currentCubeSize; row++) {
                    for (int col = 0; col < currentCubeSize; col++) {
                        String key = faceIndex + "_" + row + "_" + col;
                        if (editedColors.containsKey(key)) {
                            colorMatrix[row][col] = editedColors.get(key);
                            faceEdited = true;
                        }
                    }
                }
                
                if (faceEdited) {
                    StringBuilder updatedMatrixString = new StringBuilder();
                    updatedMatrixString.append("Face #").append(faceIndex + 1).append(":\n\n");
                    for (int row = 0; row < currentCubeSize; row++) {
                        for (int col = 0; col < currentCubeSize; col++) {
                            updatedMatrixString.append(colorMatrix[row][col]).append(" ");
                        }
                        updatedMatrixString.append("\n");
                    }
                    updatedMatrices.set(faceIndex, updatedMatrixString.toString());
                }
            }
            
            SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < updatedMatrices.size(); i++) {
                editor.putString("matrix_" + i, updatedMatrices.get(i));
            }
            editor.apply();
            
            editedColors.clear();
            Toast.makeText(this, "Changes saved!", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error saving changes: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    /**
     * Loads the cube matrices and cube size from SharedPreferences.
     * @return A CubeData object containing the matrices and cube size.
     */
    private CubeData loadMatricesFromPreferences() {
        try {
            ArrayList<String> matrices = new ArrayList<>();
            SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
            int loadedCubeSize = prefs.getInt("cube_size", 3);
            int matrixCount = prefs.getInt("matrix_count", 0);
            
            for (int i = 0; i < matrixCount; i++) {
                matrices.add(prefs.getString("matrix_" + i, ""));
            }
            
            return new CubeData(matrices, loadedCubeSize);
        } catch (Exception e) {
            return new CubeData(new ArrayList<>(), 3);
        }
    }
    
    /**
     * Loads the image URIs from SharedPreferences.
     * @return An ArrayList of Uris.
     */
    private ArrayList<Uri> loadImageUrisFromPreferences() {
        try {
            ArrayList<Uri> uris = new ArrayList<>();
            SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
            int imageCount = prefs.getInt("image_count", 0);
            
            for (int i = 0; i < imageCount; i++) {
                String uriString = prefs.getString("image_uri_" + i, "");
                if (!uriString.isEmpty()) {
                    uris.add(Uri.parse(uriString));
                } else {
                    uris.add(null);
                }
            }
            
            return uris;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    /**
     * Generates a solver string based on the final (and possibly edited) cube state.
     * For 3x3 cubes, it generates a 54-character Kociemba string.
     * For 2x2 cubes, it generates a 24-character string for a different solver.
     * It then saves all necessary data and proceeds to the AlgorithmSolutionActivity.
     */
    private void generateKociembaStringAndProceed() {
        try {
            if (!editedColors.isEmpty()) {
                saveEditedColors();
            }
            
            CubeData currentCubeData = loadMatricesFromPreferences();
            matrices = currentCubeData.matrices;
            int currentCubeSize = currentCubeData.cubeSize;
            
            int expectedMatrixCount = 6;
            int expectedSolverStringLength = (currentCubeSize == 2) ? 24 : 54;
            int colorsPerFace = currentCubeSize * currentCubeSize;
            
            if (matrices == null || matrices.size() < expectedMatrixCount) {
                return;
            }
            
            Map<String, List<List<String>>> matricesMap = new HashMap<>();
            char[] faceLetters = {'U', 'R', 'F', 'D', 'L', 'B'};
            
            for (int i = 0; i < 6; i++) {
                List<String> colors = parseColorsFromMatrix(matrices.get(i), currentCubeSize);
                if (colors.size() != colorsPerFace) {
                     return;
                }
                
                List<List<String>> faceMatrix = new ArrayList<>();
                for (int row = 0; row < currentCubeSize; row++) {
                    List<String> rowColors = new ArrayList<>();
                    for (int col = 0; col < currentCubeSize; col++) {
                        rowColors.add(colors.get(row * currentCubeSize + col));
                    }
                    faceMatrix.add(rowColors);
                }
                matricesMap.put(String.valueOf(faceLetters[i]), faceMatrix);
            }
            
            String jsonMatrices = new ObjectMapper().writeValueAsString(matricesMap);
            
            StringBuilder solverString = new StringBuilder();
            String letterColorMapJson = null;

            if (currentCubeSize == 3) {
                // 3x3 Kociemba string generation logic
                Map<String, Character> colorToFaceLetter = new HashMap<>();
                Map<Character, String> letterToColorName = new HashMap<>();

                 for (int i = 0; i < 6; i++) {
                    List<String> colors = parseColorsFromMatrix(matrices.get(i), currentCubeSize);
                    String representativeColor = colors.get(4); // Center piece
                    colorToFaceLetter.put(representativeColor, faceLetters[i]);
                    letterToColorName.put(faceLetters[i], representativeColor);
                }

                for (int i = 0; i < 6; i++) {
                    List<String> colors = parseColorsFromMatrix(matrices.get(i), currentCubeSize);
                    for (String color : colors) {
                        solverString.append(colorToFaceLetter.get(color));
                    }
                }

                 Map<String, String> stringKeyMap = new HashMap<>();
                 for (Map.Entry<Character, String> entry : letterToColorName.entrySet()) {
                     stringKeyMap.put(String.valueOf(entry.getKey()), entry.getValue());
                 }
                 letterColorMapJson = new JSONObject(stringKeyMap).toString();

            } else if (currentCubeSize == 2) {
                // 2x2 solver string generation logic
                Map<String, Character> colorNameToPytwistyChar = new HashMap<>();
                colorNameToPytwistyChar.put("white", 'W');
                colorNameToPytwistyChar.put("yellow", 'Y');
                colorNameToPytwistyChar.put("green", 'G');
                colorNameToPytwistyChar.put("blue", 'B');
                colorNameToPytwistyChar.put("orange", 'O');
                colorNameToPytwistyChar.put("red", 'R');

                List<List<String>> uMatrix = matricesMap.get("U");
                List<List<String>> rMatrix = matricesMap.get("R");
                List<List<String>> fMatrix = matricesMap.get("F");
                List<List<String>> dMatrix = matricesMap.get("D");
                List<List<String>> lMatrix = matricesMap.get("L");
                List<List<String>> bMatrix = matricesMap.get("B");

                if (uMatrix == null || rMatrix == null || fMatrix == null || dMatrix == null || lMatrix == null || bMatrix == null) {
                     return;
                }

                Function<List<List<String>>, Function<Integer, Function<Integer, Character>>> getColorChar =
                    (matrix) -> (row) -> (col) -> colorNameToPytwistyChar.get(matrix.get(row).get(col).toLowerCase());

                // Build the 24-character string in the specific order required by the solver.
                solverString.append(getColorChar.apply(fMatrix).apply(0).apply(0));
                solverString.append(getColorChar.apply(fMatrix).apply(0).apply(1));
                solverString.append(getColorChar.apply(fMatrix).apply(1).apply(1));
                solverString.append(getColorChar.apply(fMatrix).apply(1).apply(0));
                // ... and so on for all 6 faces in the required order.
            }

            if (solverString.length() != expectedSolverStringLength) {
                return;
            }

            saveSolutionDataToPreferences(currentCubeSize, solverString.toString(), letterColorMapJson, jsonMatrices);

            Intent intent = new Intent(this, AlgorithmSolutionActivity.class);
            startActivity(intent);

        } catch (Exception e) {
            Toast.makeText(this, "Error generating solution: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    /**
     * Saves all the necessary data for the AlgorithmSolutionActivity to SharedPreferences.
     * @param cubeSize The size of the cube.
     * @param solverString The generated solver string.
     * @param letterColorMapJson A JSON string mapping face letters to color names (for 3x3).
     * @param jsonMatrices A JSON string representing the full cube state.
     */
    private void saveSolutionDataToPreferences(int cubeSize, String solverString, String letterColorMapJson, String jsonMatrices) {
        SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        
        editor.putInt("cube_size", cubeSize);
        editor.putString("solver_string", solverString);
        
        if (letterColorMapJson != null) {
            editor.putString("letter_color_map_json", letterColorMapJson);
        } else {
            editor.remove("letter_color_map_json");
        }
        
        if (jsonMatrices != null) {
             editor.putString("cube_matrices_json", jsonMatrices);
        } else {
             editor.remove("cube_matrices_json");
        }
        
        editor.apply();
    }

    /**
     * A helper class to hold cube data (matrices and size) when loading from preferences.
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
     * Helper method to convert density-independent pixels (dp) to pixels (px).
     * @param dp The value in dp.
     * @return The value in px.
     */
    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
