package com.fhm.credditservertest;

import java.sql.Timestamp;

public class Post {
    public int id;
    public int userID;
    public String title;
    public String content;
    public String mediaUrl;   // URL to image/video/pdf
    public String mediaType;  // "image", "video", "pdf"
    public Timestamp created;
    public Timestamp edited;

    public Post(int id, int userID, String title, String content, String mediaUrl, String mediaType, Timestamp created, Timestamp edited) {
        this.id = id;
        this.userID = userID;
        this.title = title;
        this.content = content;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.created = created;
        this.edited = edited;
    }
}