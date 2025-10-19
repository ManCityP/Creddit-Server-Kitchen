module com.fhm.credditservertest {
    requires javafx.fxml;
    requires spark.core;
    requires com.google.gson;
    requires java.sql;
    requires javax.servlet.api;
    requires javafx.web;
    requires mysql.connector.java;
    requires javafx.media;


    opens com.fhm.credditservertest to javafx.fxml;
    exports com.fhm.credditservertest;
}