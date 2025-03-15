package com.example.zoteroepubcovers;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import nl.siegmann.epublib.domain.Book;
import nl.siegmann.epublib.epub.EpubReader;

public class EpubCoverExtractor {

    private static final String TAG = "EpubCoverExtractor";
    private static final Executor executor = Executors.newCachedThreadPool();
    
    public interface CoverCallback {
        void onCoverExtracted(String coverPath);
        void onError(String errorMessage);
    }
    
    public static void extractCover(String epubFilePath, CoverCallback callback) {
        executor.execute(() -> {
            try {
                // Create a unique file for the cover image
                File epubFile = new File(epubFilePath);
                File cacheDir = epubFile.getParentFile();
                File coverDir = new File(cacheDir, "covers");
                
                if (!coverDir.exists()) {
                    coverDir.mkdirs();
                }
                
                String coverFileName = epubFile.getName().replace(".epub", ".jpg");
                File coverFile = new File(coverDir, coverFileName);
                
                // If cover is already extracted, just return its path
                if (coverFile.exists()) {
                    callback.onCoverExtracted(coverFile.getAbsolutePath());
                    return;
                }
                
                // Read the EPUB file
                EpubReader epubReader = new EpubReader();
                Book book = epubReader.readEpub(new java.io.FileInputStream(epubFilePath));
                
                // Extract the cover image
                if (book.getCoverImage() != null) {
                    InputStream coverData = book.getCoverImage().getInputStream();
                    FileOutputStream output = new FileOutputStream(coverFile);
                    
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    
                    while ((bytesRead = coverData.read(buffer)) != -1) {
                        output.write(buffer, 0, bytesRead);
                    }
                    
                    output.close();
                    coverData.close();
                    
                    callback.onCoverExtracted(coverFile.getAbsolutePath());
                } else {
                    callback.onError("No cover image found in EPUB");
                }
            } catch (IOException e) {
                Log.e(TAG, "Error extracting cover", e);
                callback.onError("Failed to extract cover: " + e.getMessage());
            }
        });
    }
}