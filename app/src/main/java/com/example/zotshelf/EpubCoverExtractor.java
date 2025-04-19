package com.example.zotshelf;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.github.mertakdut.Reader;
import com.github.mertakdut.exception.ReadingException;

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
                
                // Use the Mertakdut EpubParser library to get the cover
                Reader reader = new Reader();
                reader.setMaxContentPerSection(1000); // Set max content per section to 1000 chars
                reader.setIsIncludingTextContent(false); // Don't need the text, just the images
                reader.setFullContent(epubFilePath); // Load the EPUB

                // Attempt to extract the cover image
                try {
                    // Get cover image bytes - try to get from metadata
                    byte[] coverData = reader.getCoverImage();
                    
                    if (coverData != null && coverData.length > 0) {
                        FileOutputStream output = new FileOutputStream(coverFile);
                        output.write(coverData);
                        output.close();
                        
                        callback.onCoverExtracted(coverFile.getAbsolutePath());
                    } else {
                        // If no cover in metadata, try to get the first image from the content
                        callback.onError("No cover image found in EPUB metadata");
                    }
                } catch (ReadingException e) {
                    // Only catch ReadingException here, not OutOfPagesException
                    Log.e(TAG, "Error extracting cover", e);
                    callback.onError("Failed to extract cover: " + e.getMessage());
                }
                
            } catch (Exception e) {
                Log.e(TAG, "Error extracting cover", e);
                callback.onError("Failed to extract cover: " + e.getMessage());
            }
        });
    }
}