package com.fhm.credditservertest;

import static spark.Spark.*;
import com.google.gson.*;

import javax.servlet.MultipartConfigElement;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

public class Server {

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + File.separator + "uploads";
    private static final Gson gson = new Gson();
    private static Process ngrokProcess;

    /*private static String GetTunnelURL() {
        try {
            URL url = new URL("http://localhost:4040/api/tunnels");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            // Look for the public URL in the JSON response
            String json = response.toString();
            int start = json.indexOf("public_url\":\"") + 13;
            int end = json.indexOf("\"", start);
            String publicUrl = json.substring(start, end);

            if(!publicUrl.startsWith("http"))
                return null;

            System.out.println("Ngrok Public URL: " + publicUrl);
            return publicUrl;

        } catch (Exception e) {
            //System.out.println("Could not fetch ngrok URL: " + e.getMessage());
            return null;
        }
    }*/

    public static void main(String[] args) throws Exception {
        String tunnelURL = "https://semineutral-antony-unwronged.ngrok-free.dev";

        Database db = new Database(
                "jdbc:mysql://localhost:3306/creddit_db",
                "root",
                "Yu-Gi-Oh!"
        );
        /*
        try {
            ResultSet resultSet = db.GetAny("SELECT url FROM hosturl LIMIT 1");
            if(resultSet.next()) {
                System.out.println("There is already an active server!");
                System.exit(0);
            }
        }
        catch(Exception ex) {
            ex.printStackTrace();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                db.Execute("TRUNCATE hosturl");
                db.CloseConnection();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }
        }));
        */

        try {
            ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/k", "start", "tunnel.bat");
            builder.redirectErrorStream(true);
            ngrokProcess = builder.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if(ngrokProcess.isAlive()) {
                    try {
                        Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe /T");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }));
            Thread.sleep(100);
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }

        port(7878); // HTTP port
        Files.createDirectories(Paths.get(UPLOAD_DIR));
        //staticFiles.externalLocation(UPLOAD_DIR);
        System.out.println("Serving uploaded files from: " + UPLOAD_DIR);

        /*try {
            for (int i = 0; i < 100; i++) {
                tunnelURL = GetTunnelURL();
                if (tunnelURL != null)
                    break;
                Thread.sleep(200);
            }
            db.Execute(String.format("INSERT INTO hosturl (url) VALUES ('%s')", tunnelURL));
        }
        catch (Exception ex) {
            ex.printStackTrace();
        }*/

        new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(7 * 60 * 60 * 1000 + 55 * 60 * 1000);
                    System.out.println("Restarting ngrok tunnel...");

                    // Kill old tunnel
                    if (ngrokProcess != null && ngrokProcess.isAlive()) {
                        Runtime.getRuntime().exec("taskkill /F /IM ngrok.exe /T");
                        Thread.sleep(3000); // Wait a bit to ensure shutdown
                    }

                    // Start new tunnel
                    ProcessBuilder builder = new ProcessBuilder("cmd.exe", "/k", "start", "tunnel.bat");
                    builder.redirectErrorStream(true);
                    ngrokProcess = builder.start();

                    /*String newURL = null;
                    for (int i = 0; i < 100; i++) {
                        newURL = GetTunnelURL();
                        if (newURL != null)
                            break;
                        Thread.sleep(200);
                    }

                    if (newURL != null) {
                        System.out.println("New Ngrok URL: " + newURL);
                        db.Execute("TRUNCATE hosturl");
                        db.Execute("INSERT INTO hosturl (url) VALUES ('" + newURL + "')");
                    } else {
                        System.out.println("Failed to get new ngrok URL!");
                    }*/

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }).start();

        // Enable CORS (for future frontend use)
        before((req, res) -> {
            res.header("Access-Control-Allow-Origin", "*");
            res.header("Access-Control-Allow-Methods", "GET,POST,DELETE,PUT,OPTIONS");
            res.header("Access-Control-Allow-Headers", "Content-Type");
        });

        get("/ping", (req, res) -> {
            res.type("text/plain");
            return "yes";
        });

        // Route: Create new post
        post("/posts", (req, res) -> {
            try {
                Post post = gson.fromJson(req.body(), Post.class);
                post.mediaUrl = post.mediaUrl.substring(post.mediaUrl.lastIndexOf('/') + 1);
                db.insertPost(post);
                res.type("application/json");
                return gson.toJson(Map.of("status", "ok"));
            } catch (Exception e) {
                e.printStackTrace(); // server log
                res.status(500);
                return gson.toJson(Map.of("status", "error", "message", e.getMessage()));
            }
        });

        // Route: Get all posts
        get("/posts", (req, res) -> {
            List<Post> posts = db.getAllPosts();
            res.type("application/json");
            return gson.toJson(posts);
        });

        // Route: File upload
        final String finalTunnelURL = tunnelURL;
        post("/upload", (req, res) -> {
            req.attribute("org.eclipse.jetty.multipartConfig", new MultipartConfigElement("/tmp"));
            Path tempFile = Files.createTempFile(Paths.get(UPLOAD_DIR), "", "");

            try (InputStream is = req.raw().getPart("file").getInputStream()) {
                Files.copy(is, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }

            String original = req.raw().getPart("file").getSubmittedFileName();
            String extension = original.substring(original.lastIndexOf('.'));
            String newName = UUID.randomUUID() + extension;

            Path finalPath = Paths.get(UPLOAD_DIR, newName);
            Files.move(tempFile, finalPath, StandardCopyOption.REPLACE_EXISTING);

            String fileUrl =  finalTunnelURL + "/uploads/" + newName;
            System.out.println("Uploaded\t" + fileUrl);

            res.type("application/json");
            return gson.toJson(Map.of("url", fileUrl));
        });

        head("/uploads/:filename", (req, res) -> {
            String filename = req.params(":filename");
            File file = new File(UPLOAD_DIR, filename);

            if (!file.exists() || file.isDirectory()) {
                halt(404, "File not found");
                return null;
            }

            res.status(200);
            res.header("Accept-Ranges", "bytes");
            res.header("Content-Length", String.valueOf(file.length()));
            res.header("Content-Type", Files.probeContentType(file.toPath()));
            return "";
        });

        // Serve files with range support
        get("/uploads/:filename", (req, res) -> {
            String filename = req.params(":filename");
            File file = new File(UPLOAD_DIR, filename);

            if (!file.exists() || file.isDirectory()) {
                halt(404, "File not found");
                return null;
            }

            String range = req.headers("Range");
            long fileLength = file.length();
            long start = 0, end = fileLength - 1;

            if (range != null && range.startsWith("bytes=")) {
                String[] parts = range.substring(6).split("-");
                try {
                    start = Long.parseLong(parts[0]);
                    if (parts.length > 1 && !parts[1].isEmpty()) {
                        end = Long.parseLong(parts[1]);
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (end >= fileLength) end = fileLength - 1;
            long contentLength = end - start + 1;

            res.status(range == null ? 200 : 206);
            res.header("Content-Type", Files.probeContentType(file.toPath()));
            res.header("Accept-Ranges", "bytes");
            if (range != null)
                res.header("Content-Range", "bytes " + start + "-" + end + "/" + fileLength);
            res.header("Content-Length", String.valueOf(contentLength));

            try (RandomAccessFile raf = new RandomAccessFile(file, "r");
                 OutputStream os = res.raw().getOutputStream()) {
                raf.seek(start);
                byte[] buffer = new byte[8192];
                long bytesRemaining = contentLength;

                while (bytesRemaining > 0) {
                    int bytesToRead = (int) Math.min(buffer.length, bytesRemaining);
                    int bytesRead = raf.read(buffer, 0, bytesToRead);
                    if (bytesRead == -1) break;
                    os.write(buffer, 0, bytesRead);
                    bytesRemaining -= bytesRead;
                }

                os.flush();
            }

            // Prevent Spark from writing extra headers/content
            halt();
            return null;
        });

        System.out.println("Server running on port 7878...");
    }
}
