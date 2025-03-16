package com.example.zoteroepubcovers;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

// Import the new library
import com.mertakdut.Reader;
import com.mertakdut.exception.OutOfPagesException;
import com.mertakdut.exception.ReadingException;

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
                
                // Using the new library to extract cover
                Reader reader = new Reader();
                reader.setIsIncludingTextContent(false);  // No need for text content
                reader.setFullContent(epubFilePath); 
                
                // The cover image is typically at the beginning of the EPUB
                byte[] coverData = reader.getCoverImage();
                
                if (coverData != null && coverData.length > 0) {
                    FileOutputStream output = new FileOutputStream(coverFile);
                    output.write(coverData);
                    output.close();
                    
                    callback.onCoverExtracted(coverFile.getAbsolutePath());
                } else {
                    callback.onError("No cover image found in EPUB");
                }
            } catch (IOException | ReadingException e) {
                Log.e(TAG, "Error extracting cover", e);
                callback.onError("Failed to extract cover: " + e.getMessage());
            }
        });
    }
}