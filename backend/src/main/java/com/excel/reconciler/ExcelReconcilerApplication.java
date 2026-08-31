package com.excel.reconciler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

@SpringBootApplication
@EnableAsync
public class ExcelReconcilerApplication {

    public static void main(String[] args) {
        loadDotEnv();
        SpringApplication.run(ExcelReconcilerApplication.class, args);
    }

    private static void loadDotEnv() {
        List<String> candidatePaths = List.of(".env", "../.env", "backend/.env");
        for (String path : candidatePaths) {
            File envFile = new File(path);
            if (envFile.exists() && envFile.isFile()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(envFile, StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) {
                            continue;
                        }
                        int eqIdx = line.indexOf('=');
                        String key = line.substring(0, eqIdx).trim();
                        String val = line.substring(eqIdx + 1).trim();
                        // Remove surrounding quotes if present
                        if ((val.startsWith("\"") && val.endsWith("\"")) || (val.startsWith("'") && val.endsWith("'"))) {
                            val = val.substring(1, val.length() - 1);
                        }
                        if (!key.isEmpty() && System.getProperty(key) == null && System.getenv(key) == null) {
                            System.setProperty(key, val);
                        }
                    }
                    System.out.println(">>> Loaded configuration from: " + envFile.getAbsolutePath());
                    break;
                } catch (Exception ignored) {
                }
            }
        }
    }
}
