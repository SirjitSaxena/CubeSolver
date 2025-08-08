package com.example.cubesolver;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import android.graphics.Color;
import android.content.Context;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONException;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.Arrays;
import android.widget.LinearLayout;
import android.graphics.drawable.GradientDrawable;

/**
 * AlgorithmSolutionActivity is the final screen of the application.
 * It receives the solver string from SolutionActivity, sends it to a solver API,
 * and then displays the solution steps to the user in an interactive stepper format.
 * It also shows the initial state of the cube as a 2D net for reference.
 */
public class AlgorithmSolutionActivity extends AppCompatActivity {

    private static final String TAG = "AlgorithmSolution";
    // The endpoint for the solver API. This service takes a cube state string and returns the solution.
    private static final String KOCIEMBA_API_ENDPOINT = "https://kociemba.onrender.com/solve";
    
    // Data from previous activity
    private String solverString; // The 54-char (3x3) or 24-char (2x2) string representing the cube state.
    private Map<Character, String> letterToColorNameMap = new HashMap<>(); // Maps face letters (U,R,F..) to color names for 3x3.
    private Map<String, List<List<String>>> cubeMatrices = new HashMap<>(); // The detailed color matrix for each face.
    private int cubeSize = 3; // The size of the cube (2 or 3).

    // UI Elements
    private TextView currentMoveText; // Displays the current solution move and its description.
    private ProgressBar progressBar; // Shown while the solution is being calculated.
    private GridLayout upFaceGrid, leftFaceGrid, frontFaceGrid, rightFaceGrid, backFaceGrid, downFaceGrid; // Grids for the 2D cube net.
    private Button nextButton, previousButton; // Buttons for navigating the solution steps.
    private TextView stepIndicatorText; // Shows the current step number (e.g., "Step 1 of 20").
    private LinearLayout stepperControlsLayout; // The layout containing the stepper controls.

    // State for the solution stepper
    private List<String> solutionMovesList; // The list of solution moves received from the API.
    private int currentMoveIndex = -1; // The index of the current move being displayed.

    // ExecutorService to run network operations on a background thread.
    private ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_algorithm_solution);

        // Initialize all UI views from the layout file.
        initializeViews();
        
        // Load all necessary data (cube state, solver string, color mappings) from SharedPreferences.
        loadCubeData();
        loadSolverString();
        loadColorMapping();
        
        // Display the initial state of the cube as a 2D unfolded net.
        if (!cubeMatrices.isEmpty()) {
            displayCubeNet(cubeMatrices, cubeSize);
        } else {
            Log.e(TAG, "Cube matrices data is missing, cannot display cube net.");
            Toast.makeText(this, "Could not load cube state", Toast.LENGTH_SHORT).show();
        }
        
        // Set up the listeners for the "Next" and "Previous" buttons.
        setupStepperListeners();
        // Start the process of solving the cube by calling the API.
        solveCube();
    }

    /**
     * Initializes all the UI views used in this activity.
     */
    private void initializeViews() {
        currentMoveText = findViewById(R.id.currentMoveText);
        progressBar = findViewById(R.id.progressBar);
        Button backButton = findViewById(R.id.backButton);
        
        upFaceGrid = findViewById(R.id.upFaceGrid);
        leftFaceGrid = findViewById(R.id.leftFaceGrid);
        frontFaceGrid = findViewById(R.id.frontFaceGrid);
        rightFaceGrid = findViewById(R.id.rightFaceGrid);
        backFaceGrid = findViewById(R.id.backFaceGrid);
        downFaceGrid = findViewById(R.id.downFaceGrid);

        nextButton = findViewById(R.id.nextButton);
        previousButton = findViewById(R.id.previousButton);
        stepIndicatorText = findViewById(R.id.stepIndicatorText);
        stepperControlsLayout = findViewById(R.id.stepperControlsLayout);
        
        backButton.setOnClickListener(v -> finish());
    }

    /**
     * Sets up the OnClickListeners for the next and previous buttons of the solution stepper.
     */
    private void setupStepperListeners() {
        nextButton.setOnClickListener(v -> {
            if (solutionMovesList != null && currentMoveIndex < solutionMovesList.size() - 1) {
                currentMoveIndex++;
                updateStepUI();
            }
        });

        previousButton.setOnClickListener(v -> {
            if (solutionMovesList != null && currentMoveIndex > 0) {
                currentMoveIndex--;
                updateStepUI();
            }
        });
    }
    
    /**
     * Loads the cube size and the detailed color matrices from SharedPreferences.
     */
    private void loadCubeData() {
        SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
        cubeSize = prefs.getInt("cube_size", 3);
        String jsonMatrices = prefs.getString("cube_matrices_json", null);

        if (jsonMatrices != null) {
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                cubeMatrices = objectMapper.readValue(jsonMatrices, new TypeReference<Map<String, List<List<String>>>>() {});
            } catch (Exception e) {
                Log.e(TAG, "Error parsing cube matrices JSON", e);
                cubeMatrices.clear();
            }
        }
    }
    
    /**
     * Loads the solver string (e.g., "UUU...") from SharedPreferences.
     */
    private void loadSolverString() {
        SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
        solverString = prefs.getString("solver_string", "");
    }
    
    /**
     * Loads the mapping from face letters (U, R, F, etc.) to color names, used for 3x3 cubes.
     */
    private void loadColorMapping() {
        SharedPreferences prefs = getSharedPreferences("CubeSolverData", MODE_PRIVATE);
        String jsonMap = prefs.getString("letter_color_map_json", null);

        if (jsonMap != null) {
            try {
                letterToColorNameMap.clear();
                JSONObject jsonObject = new JSONObject(jsonMap);
                Iterator<String> keys = jsonObject.keys();

                while (keys.hasNext()) {
                    String keyString = keys.next();
                    if (keyString.length() == 1) {
                        letterToColorNameMap.put(keyString.charAt(0), jsonObject.getString(keyString));
                    }
                }
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing letter-color map JSON", e);
                letterToColorNameMap.clear();
            }
        }
    }

    /**
     * Displays the initial state of the cube as a 2D unfolded net.
     * @param matrices A map where the key is the face name (e.g., "U") and the value is the color matrix.
     * @param size The size of the cube (2 or 3).
     */
    private void displayCubeNet(Map<String, List<List<String>>> matrices, int size) {
        if (matrices == null || matrices.isEmpty()) {
             return;
        }

        Map<String, GridLayout> faceGridMap = new HashMap<>();
        faceGridMap.put("U", upFaceGrid);
        faceGridMap.put("R", rightFaceGrid);
        faceGridMap.put("F", frontFaceGrid);
        faceGridMap.put("D", downFaceGrid);
        faceGridMap.put("L", leftFaceGrid);
        faceGridMap.put("B", backFaceGrid);

        int gridWidth = getResources().getDimensionPixelSize(R.dimen.face_size);
        int stickerSize = gridWidth / size;

        for (Map.Entry<String, List<List<String>>> entry : matrices.entrySet()) {
            String faceName = entry.getKey();
            List<List<String>> faceMatrix = entry.getValue();
            GridLayout grid = faceGridMap.get(faceName);

            if (grid != null && faceMatrix != null && !faceMatrix.isEmpty()) {
                 if ((size == 3 && faceMatrix.size() == 3) || (size == 2 && faceMatrix.size() == 2)) {
                     populateFaceGrid(grid, faceMatrix, this, stickerSize, size);
                 } else {
                      grid.removeAllViews();
                 }
            } else if (grid != null) {
                 grid.removeAllViews();
            }
        }
    }

    /**
     * Populates a single GridLayout with colored squares (stickers) based on a color matrix.
     * @param grid The GridLayout to populate.
     * @param faceMatrix The 2D list of color names for the face.
     * @param context The application context.
     * @param stickerSize The size of each sticker in pixels.
     * @param size The size of the cube.
     */
    private void populateFaceGrid(GridLayout grid, List<List<String>> faceMatrix, Context context, int stickerSize, int size) {
        grid.removeAllViews();
        grid.setColumnCount(size);
        grid.setRowCount(size);

        for (int row = 0; row < size; row++) {
            for (int col = 0; col < size; col++) {
                 if (row < faceMatrix.size() && col < faceMatrix.get(row).size()) {
                    View sticker = new View(context);
                    String colorName = faceMatrix.get(row).get(col);
                    int stickerColor = getColorFromName(colorName);

                    GradientDrawable borderDrawable = new GradientDrawable();
                    borderDrawable.setColor(stickerColor);
                    int borderWidth = dpToPx(1);
                    int borderColor = (stickerColor == Color.WHITE || stickerColor == Color.YELLOW) ? Color.DKGRAY : Color.BLACK;
                    borderDrawable.setStroke(borderWidth, borderColor);
                    sticker.setBackground(borderDrawable);

                    GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                    params.width = stickerSize; 
                    params.height = stickerSize; 
                    sticker.setLayoutParams(params);

                    grid.addView(sticker);
                 }
            }
        }
    }

    /**
     * Converts a color name string to its corresponding integer color value.
     * @param colorName The name of the color (e.g., "Red").
     * @return The integer color value.
     */
    private int getColorFromName(String colorName) {
        if (colorName == null) return Color.GRAY;
        switch (colorName.toLowerCase()) {
            case "white": return Color.WHITE;
            case "yellow": return Color.YELLOW;
            case "red": return Color.RED;
            case "orange": return Color.rgb(255, 165, 0);
            case "blue": return Color.BLUE;
            case "green": return Color.GREEN;
            default: return Color.GRAY;
        }
    }
    
    /**
     * Initiates the cube solving process by calling the solver API in a background thread.
     */
    private void solveCube() {
         if (solverString == null || solverString.isEmpty()) {
             currentMoveText.setText("Cube data missing. Cannot solve.");
             progressBar.setVisibility(View.GONE);
             return;
         }

        int expectedSolverStringLength = (cubeSize == 2) ? 24 : 54;
        if (solverString.length() != expectedSolverStringLength) {
             currentMoveText.setText("Error: Invalid cube data for solver.");
             progressBar.setVisibility(View.GONE);
             return;
        }

        progressBar.setVisibility(View.VISIBLE);
        currentMoveText.setText("Calculating solution...");
        stepperControlsLayout.setVisibility(View.GONE);
        stepIndicatorText.setVisibility(View.GONE);

        executorService.execute(() -> {
            String solutionResult = "";
            boolean apiSuccess = false;

            try {
                String encodedCube = URLEncoder.encode(solverString, StandardCharsets.UTF_8.toString());
                String apiUrl = KOCIEMBA_API_ENDPOINT + "?cube=" + encodedCube;
                
                URL url = new URL(apiUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(30000);
                connection.setReadTimeout(30000);

                int responseCode = connection.getResponseCode();

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    StringBuilder response = new StringBuilder();
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line);
                        }
                    }

                    JSONObject jsonResponse = new JSONObject(response.toString());

                    if (jsonResponse.has("solution")) {
                        if (cubeSize == 2) {
                            JSONArray solutionArray = jsonResponse.getJSONArray("solution");
                            solutionResult = solutionArray.join(" ").replace("\"", "");
                        } else {
                             solutionResult = jsonResponse.getString("solution");
                        }
                        apiSuccess = true;
                    } else if (jsonResponse.has("error")) {
                        solutionResult = "Error from API: " + jsonResponse.getString("error");
                    } else {
                        solutionResult = "Unknown response format from API";
                    }
                } else {
                    solutionResult = "API Error: HTTP " + responseCode;
                }

            } catch (Exception e) {
                Log.e(TAG, "Error solving cube", e);
                solutionResult = "Error calculating solution: " + e.getMessage();
            }

            final String finalSolutionString = solutionResult;
            final boolean finalApiSuccess = apiSuccess;
            runOnUiThread(() -> {
                progressBar.setVisibility(View.GONE);
                if (finalApiSuccess && !finalSolutionString.trim().isEmpty() && !finalSolutionString.toLowerCase().contains("error")) {
                    String[] moves = finalSolutionString.trim().split("\\s+");
                    if (moves.length > 0 && !moves[0].isEmpty()) {
                        solutionMovesList = new ArrayList<>(Arrays.asList(moves));
                        currentMoveIndex = 0;
                        updateStepUI();
                        stepperControlsLayout.setVisibility(View.VISIBLE);
                        stepIndicatorText.setVisibility(View.VISIBLE);
                    } else {
                         currentMoveText.setText("Solution is empty. The cube might already be solved.");
                    }
                } else {
                    currentMoveText.setText(finalSolutionString);
                }
            });
        });
    }

    /**
     * Updates the UI for the current solution step.
     * It sets the text for the current move and its description, and updates the step indicator.
     * It also enables/disables the next/previous buttons as needed.
     */
    private void updateStepUI() {
        if (solutionMovesList == null || solutionMovesList.isEmpty()) {
            return;
        }

        String move = solutionMovesList.get(currentMoveIndex);
        String description = getMoveDescription(move);
        currentMoveText.setText(String.format("%s\n%s", move, description));
        stepIndicatorText.setText(String.format("Step %d of %d", currentMoveIndex + 1, solutionMovesList.size()));

        previousButton.setEnabled(currentMoveIndex > 0);
        nextButton.setEnabled(currentMoveIndex < solutionMovesList.size() - 1);
    }
    
    /**
     * Returns a human-readable description for a given move notation (e.g., "R" -> "Right face clockwise").
     * @param move The move notation string.
     * @return The description of the move.
     */
    private String getMoveDescription(String move) {
        if (move == null || move.isEmpty()) return "Invalid move";
        
        StringBuilder description = new StringBuilder();
        char face = move.charAt(0);
        switch (face) {
            case 'R': description.append("Right face"); break;
            case 'L': description.append("Left face"); break;
            case 'U': description.append("Up face"); break;
            case 'D': description.append("Down face"); break;
            case 'F': description.append("Front face"); break;
            case 'B': description.append("Back face"); break;
            default: return "Unknown move: " + move;
        }
        
        if (move.length() > 1) {
            char direction = move.charAt(1);
            switch (direction) {
                case '\'': description.append(" counter-clockwise"); break;
                case '2': description.append(" 180 degrees"); break;
            }
        } else {
            description.append(" clockwise");
        }
        
        return description.toString();
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Shut down the executor service to prevent memory leaks.
        executorService.shutdown();
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
