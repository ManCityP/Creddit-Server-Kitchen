package com.fhm.credditservertest;

import java.sql.*;
import java.util.*;

public class Database {
    private final Connection conn;

    public Database(String url, String user, String pass) throws SQLException {
        conn = DriverManager.getConnection(url, user, pass);
    }

    public void CloseConnection() {
        try {
            if (conn != null)
                conn.close();
            System.out.println("Successfully disconnected from database!");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public void insertPost(Post p) throws SQLException {
        String sql = "INSERT INTO posts (userid, title, content, media_url, media_type) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, p.userID);
            stmt.setString(2, p.title);
            stmt.setString(3, p.content);
            stmt.setString(4, p.mediaUrl);
            stmt.setString(5, p.mediaType);
            stmt.executeUpdate();
        }
    }

    public List<Post> getAllPosts() throws SQLException {
        List<Post> posts = new ArrayList<>();
        String sql = "SELECT * FROM posts ORDER BY id DESC";
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                Post p = new Post(rs.getInt("id"), rs.getInt("userid"), rs.getString("title"),
                        rs.getString("content"), rs.getString("media_url"), rs.getString("media_type"),
                        rs.getTimestamp("created"), rs.getTimestamp("edited"));
                posts.add(p);
            }
        }
        return posts;
    }

    public ResultSet GetAny(String query) throws Exception {
        Statement stmt = conn.createStatement();
        return stmt.executeQuery(query);
    }

    public void Execute(String sql) throws Exception {
        PreparedStatement insertStatement = conn.prepareStatement(sql);
        insertStatement.executeUpdate();
    }
}