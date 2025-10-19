package com.fhm.credditservertest;

import com.google.gson.Gson;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.web.WebView;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import javafx.util.Duration;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.sql.ResultSet;
import java.util.Map;

public class HelloController {

    @FXML private Button attachMediaButton;
    @FXML private TextArea contentField;
    @FXML private TextField postidField;
    @FXML private Button receiveButton;
    @FXML private Button sendButton;
    @FXML private TextField titleField;
    @FXML private AnchorPane mediaPane;
    @FXML private MediaView mediaViewer;
    @FXML private Slider volumeSlider;
    @FXML private Slider progressSlider;
    @FXML private Label mediaTimeLabel;
    @FXML private ImageView imageViewer;
    @FXML private Label selectedFileLabel;

    private MediaPlayer mp;
    private boolean isSeeking = false;
    private BooleanProperty paused = new SimpleBooleanProperty(true);
    private boolean isAudio = false;

    private File selectedFile = null;
    public static String BASE_URL = "https://semineutral-antony-unwronged.ngrok-free.dev";
    private final Gson gson = new Gson();

    @FXML
    public void initialize() {
        imageViewer.setImage(null);
        imageViewer.setVisible(false);
        mediaViewer.setMediaPlayer(null);
        mediaPane.setVisible(false);
        mp = null;

        //UpdateBaseURL();

        mediaPane.setOnMouseClicked(e -> mediaPane.requestFocus());

        volumeSlider.setMin(0);
        volumeSlider.setMax(100);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mp != null)
                mp.setVolume(newVal.doubleValue()/100.0);
        });

        paused.addListener((obs, oldVal, newVal) -> {
            if(isAudio) {
                if (!newVal)
                    imageViewer.setImage(new Image(getClass().getResource("/defaults/audio_default.gif").toExternalForm()));
                else
                    imageViewer.setImage(new Image(getClass().getResource("/defaults/audio_default_static.png").toExternalForm()));
            }
        });
    }

    /*private void UpdateBaseURL() {
        try {
            Database db = new Database(
                    "jdbc:mysql://localhost:3306/creddit_db",
                    "root",
                    "Yu-Gi-Oh!"
            );
            ResultSet rs = db.GetAny("SELECT url FROM hosturl LIMIT 1");
            if(rs.next()) {
                BASE_URL = rs.getString("url");
            }
            else {
                System.out.println("No active Server!");
                System.exit(1);
            }
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }
    }*/

    private boolean isServerReachable(String baseUrl) {
        try {
            URL url = new URL(baseUrl + "/ping");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(2000);
            conn.setReadTimeout(2000);
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return "yes".equals(in.readLine());
                }
            }
        } catch (IOException e) {
            System.out.println("Server unreachable: " + e.getMessage());
        }
        return false;
    }

    @FXML
    void HandleKeyPress(KeyEvent event) {
        if(mp == null)  return;

        if (event.getCode() == KeyCode.RIGHT) {
            // Seek forward 5 seconds (but not past end)
            progressSlider.setValue(progressSlider.getValue() + 5);
            mp.seek(Duration.seconds(progressSlider.getValue()));
            UpdateMediaTimeLabel(Duration.seconds(progressSlider.getValue()), mp.getTotalDuration());
            event.consume();
        }
        else if (event.getCode() == KeyCode.LEFT) {
            // Seek backward 5 seconds (but not before start)
            progressSlider.setValue(progressSlider.getValue() - 5);
            mp.seek(Duration.seconds(progressSlider.getValue()));
            UpdateMediaTimeLabel(Duration.seconds(progressSlider.getValue()), mp.getTotalDuration());
            event.consume();
        }
        if (event.getCode() == KeyCode.UP) {
            volumeSlider.setValue(volumeSlider.getValue() + 5);
            event.consume();
        }
        else if (event.getCode() == KeyCode.DOWN) {
            // Seek backward 5 seconds (but not before start)
            volumeSlider.setValue(volumeSlider.getValue() - 5);
            event.consume();
        }
        else if(event.getCode() == KeyCode.SPACE) {
            if(mp.getStatus() == MediaPlayer.Status.PLAYING) {
                PauseMedia();
            }
            else {
                PlayMedia();
            }
            event.consume();
        }

        if(mp.getTotalDuration() != null) {
            progressSlider.setValue(mp.getCurrentTime().toSeconds());
            UpdateMediaTimeLabel(mp.getCurrentTime(), mp.getTotalDuration());
        }
    }

    @FXML
    void MediaClicked() {
        if(mp != null && mp.getStatus() == MediaPlayer.Status.PLAYING) {
            PauseMedia();
        }
        else {
            PlayMedia();
        }
    }

    @FXML
    void PauseMedia() { if(mp != null) mp.pause(); mediaPane.requestFocus(); paused.set(true);  }

    @FXML
    void PlayMedia() {  if(mp != null) mp.play(); mediaPane.requestFocus(); paused.set(false); }

    @FXML
    void AttachMedia(ActionEvent event) {
        Window window = ((javafx.scene.Node) event.getSource()).getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Open File");

        fileChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        selectedFile = fileChooser.showOpenDialog(window);

        if (selectedFile != null) {
            selectedFileLabel.setText("Selected file: " + selectedFile.getAbsolutePath());
        }
        else {
            selectedFileLabel.setText("No File Selected");
        }
    }

    private String uploadFile(File file) throws Exception {
        if(!isServerReachable(BASE_URL))
            return null;
        String boundary = "----Boundary" + System.currentTimeMillis();
        URL url = new URL(BASE_URL + "/upload");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoOutput(true);
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        try (DataOutputStream out = new DataOutputStream(conn.getOutputStream())) {
            out.writeBytes("--" + boundary + "\r\n");
            out.writeBytes("Content-Disposition: form-data; name=\"file\"; filename=\"" + file.getName() + "\"\r\n");
            out.writeBytes("Content-Type: " + Files.probeContentType(file.toPath()) + "\r\n\r\n");
            Files.copy(file.toPath(), out);
            out.writeBytes("\r\n--" + boundary + "--\r\n");
        }

        BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = in.readLine()) != null) response.append(line);
        in.close();
        return response.toString();
    }

    @FXML
    void SendPost(ActionEvent event) {
        if(!isServerReachable(BASE_URL)) {
            new Alert(Alert.AlertType.ERROR, "Server unreachable! Check your connection and try again!").showAndWait();
            //UpdateBaseURL();
            return;
        }
        try {
            String title = titleField.getText();
            String content = contentField.getText();

            String mediaUrl = null;
            String mediaType = null;

            // If file selected, upload first
            if (selectedFile != null) {
                System.out.println("Uploading file: " + selectedFile.getName());
                String uploadResponse = uploadFile(selectedFile);
                if(uploadResponse == null) {
                    new Alert(Alert.AlertType.ERROR, "Server unreachable! Check your connection and try again!").showAndWait();
                    //UpdateBaseURL();
                    return;
                }
                Map<?, ?> json = gson.fromJson(uploadResponse, Map.class);
                mediaUrl = (String) json.get("url");

                // Detect media type
                String mime = Files.probeContentType(selectedFile.toPath());
                if (mime != null) {
                    if (mime.startsWith("image/")) mediaType = "image";
                    else if (mime.startsWith("video/")) mediaType = "video";
                    else if (mime.startsWith("audio/")) mediaType = "audio";
                    else if (mime.equals("application/pdf")) mediaType = "pdf";
                }
            }

            // Now send post JSON
            Post post = new Post(1, 115, title, content, mediaUrl, mediaType, null, null);
            String jsonBody = gson.toJson(post);

            URL url = new URL(BASE_URL + "/posts");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream os = conn.getOutputStream()) {
                os.write(jsonBody.getBytes());
            }

            if (conn.getResponseCode() == 200) {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "Post uploaded successfully!");
                alert.showAndWait();
                titleField.clear();
                contentField.clear();
                selectedFileLabel.setText("No file selected");
                selectedFile = null;
            } else {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Failed to send post!");
                alert.showAndWait();
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            new Alert(Alert.AlertType.ERROR, "Error: " + ex.getMessage()).showAndWait();
        }
    }

    private void UpdateMediaTimeLabel(Duration current, Duration total) {
        long currentSec = (long) current.toSeconds();
        long totalSec = (long) total.toSeconds();

        String currentStr = FormatTime(currentSec);
        String totalStr = FormatTime(totalSec);

        mediaTimeLabel.setText(currentStr + " / " + totalStr);
    }

    private String FormatTime(long totalSeconds) {
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        if (hours > 0)
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        else
            return String.format("%02d:%02d", minutes, seconds);
    }

    private void handleSeek(MouseEvent event) {
        if (mp == null || mp.getTotalDuration() == null)
            return;

        if (event.getEventType() == MouseEvent.MOUSE_PRESSED) {
            isSeeking = true;
            if(!paused.get())
                mp.pause();
        } else if (event.getEventType() == MouseEvent.MOUSE_RELEASED) {
            mp.seek(Duration.seconds(progressSlider.getValue()));
            UpdateMediaTimeLabel(Duration.seconds(progressSlider.getValue()), mp.getTotalDuration());
            progressSlider.getParent().requestFocus();
            isSeeking = false;
            if(!paused.get())
                mp.play();
        }
        mediaPane.requestFocus();
    }

    @FXML
    void ReceivePost(ActionEvent event) {
        if(!isServerReachable(BASE_URL)) {
            new Alert(Alert.AlertType.ERROR, "Server unreachable! Check your connection and try again!").showAndWait();
            //UpdateBaseURL();
            return;
        }
        try {
            String id = postidField.getText();
            if(id.equals("0")) {
                contentField.setText(BASE_URL + "/posts");
                return;
            }
            URL url = new URL(BASE_URL + "/posts");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            reader.close();

            Post[] posts = gson.fromJson(sb.toString(), Post[].class);
            Post post = null;
            for (Post p : posts) {
                if (String.valueOf(p.id).equals(id)) {
                    post = p;
                    break;
                }
            }

            if (post == null) {
                titleField.setText("Post not found.");
                return;
            }

            titleField.setText(post.title);
            contentField.setText(post.content);

            isAudio = false;
            paused.set(true);
            imageViewer.setImage(null);
            imageViewer.setVisible(false);
            mediaViewer.setMediaPlayer(null);
            mediaPane.setVisible(false);
            if(mp != null)
                mp.dispose();
            mp = null;
            // Handle media display
            if (post.mediaUrl != null && !post.mediaUrl.isEmpty()) {
                String media = BASE_URL + "/uploads/" + post.mediaUrl;
                System.out.println(media);

                if(post.mediaType.equals("image") || media.endsWith(".gif")) {
                    Image image = new Image(media, true);
                    imageViewer.setImage(image);
                    imageViewer.setVisible(true);
                }
                else if(post.mediaType.equals("video") || post.mediaType.equals("audio") || media.endsWith(".mp3") || media.endsWith(".wav")) {
                    Media mediaObj = new Media(media);
                    mp = new MediaPlayer(mediaObj);
                    progressSlider.setValue(0);
                    UpdateMediaTimeLabel(Duration.seconds(0), Duration.seconds(0));
                    mp.setOnReady(() -> {
                        progressSlider.setMin(0);
                        progressSlider.setMax(mp.getTotalDuration().toSeconds());
                        UpdateMediaTimeLabel(mp.getCurrentTime(), mp.getTotalDuration());
                    });
                    mp.setVolume(volumeSlider.getValue()/100.0);
                    mp.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                        if (!isSeeking && mp.getTotalDuration() != null) {
                            Platform.runLater(() -> {
                                progressSlider.setValue(newTime.toSeconds());
                                UpdateMediaTimeLabel(newTime, mp.getTotalDuration());
                            });
                        }
                    });
                    progressSlider.setOnMousePressed(this::handleSeek);
                    progressSlider.setOnMouseReleased(this::handleSeek);
                    mp.setAutoPlay(false);
                    if (post.mediaType.equals("video") || media.endsWith(".mp4") || media.endsWith(".mov")) {
                        mediaViewer.setMediaPlayer(mp);
                    } else {
                        isAudio = true;
                        mediaViewer.setMediaPlayer(null); // prevent audio-only issues
                        imageViewer.setImage(new Image(getClass().getResource("/defaults/audio_default_static.png").toExternalForm()));
                        imageViewer.setVisible(true);
                    }
                    mediaPane.setVisible(true);
                }
                else {
                    contentField.setText(contentField.getText() + "\n\n" + media);
                }
            }

        } catch (Exception ex) {
            ex.printStackTrace();
            titleField.setText("Error: " + ex.getMessage());
        }
    }
}
