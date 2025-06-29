package com.example.zotshelf;

import android.graphics.Bitmap;
import android.graphics.pdf.PdfRenderer;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.github.mertakdut.Reader;
import com.github.mertakdut.exception.ReadingException;

public class CoverExtractor {

```
private static final String TAG = "CoverExtractor";
private static final Executor executor = Executors.newCachedThreadPool();

public interface CoverCallback {
    void onCoverExtracted(String coverPath);
    void onError(String errorMessage);
}

/**
 * Extract cover from either EPUB or PDF file
 * @param filePath Path to the EPUB or PDF file
 * @param callback Callback to handle success/error
 */
public static void extractCover(String filePath, CoverCallback callback) {
    executor.execute(() -> {
        try {
            File file = new File(filePath);
            String fileName = file.getName().toLowerCase();
            
            if (fileName.endsWith(".epub")) {
                extractEpubCover(filePath, callback);
            } else if (fileName.endsWith(".pdf")) {
                extractPdfCover(filePath, callback);
            } else {
                callback.onError("Unsupported file type: " + fileName);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error extracting cover", e);
            callback.onError("Failed to extract cover: " + e.getMessage());
        }
    });
}

private static void extractEpubCover(String epubFilePath, CoverCallback callback) {
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
        reader.setMaxContentPerSection(1000);
        reader.setIsIncludingTextContent(false);
        reader.setFullContent(epubFilePath);

        try {
            byte[] coverData = reader.getCoverImage();
            
            if (coverData != null && coverData.length > 0) {
                FileOutputStream output = new FileOutputStream(coverFile);
                output.write(coverData);
                output.close();
                
                callback.onCoverExtracted(coverFile.getAbsolutePath());
            } else {
                callback.onError("No cover image found in EPUB metadata");
            }
        } catch (ReadingException e) {
            Log.e(TAG, "Error extracting EPUB cover", e);
            callback.onError("Failed to extract EPUB cover: " + e.getMessage());
        }
        
    } catch (Exception e) {
        Log.e(TAG, "Error extracting EPUB cover", e);
        callback.onError("Failed to extract EPUB cover: " + e.getMessage());
    }
}

private static void extractPdfCover(String pdfFilePath, CoverCallback callback) {
    try {
        // Create a unique file for the cover image
        File pdfFile = new File(pdfFilePath);
        File cacheDir = pdfFile.getParentFile();
        File coverDir = new File(cacheDir, "covers");
        
        if (!coverDir.exists()) {
            coverDir.mkdirs();
        }
        
        String coverFileName = pdfFile.getName().replace(".pdf", ".jpg");
        File coverFile = new File(coverDir, coverFileName);
        
        // If cover is already extracted, just return its path
        if (coverFile.exists()) {
            callback.onCoverExtracted(coverFile.getAbsolutePath());
            return;
        }
        
        // Use Android's PdfRenderer to get the first page
        ParcelFileDescriptor fileDescriptor = null;
        PdfRenderer pdfRenderer = null;
        PdfRenderer.Page page = null;
        
        try {
            fileDescriptor = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY);
            pdfRenderer = new PdfRenderer(fileDescriptor);
            
            if (pdfRenderer.getPageCount() > 0) {
                page = pdfRenderer.openPage(0); // Get first page
                
                // Calculate dimensions for the bitmap
                // Scale down to reasonable size while maintaining aspect ratio
                int maxDimension = 600; // Maximum width or height
                float scale = Math.min((float) maxDimension / page.getWidth(), 
                                     (float) maxDimension / page.getHeight());
                
                int bitmapWidth = Math.round(page.getWidth() * scale);
                int bitmapHeight = Math.round(page.getHeight() * scale);
                
                // Create bitmap and render page to it
                Bitmap bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888);
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                
                // Save bitmap to file
                FileOutputStream output = new FileOutputStream(coverFile);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output);
                output.close();
                
                // Clean up bitmap
                bitmap.recycle();
                
                callback.onCoverExtracted(coverFile.getAbsolutePath());
            } else {
                callback.onError("PDF has no pages");
            }
            
        } catch (IOException e) {
            Log.e(TAG, "Error rendering PDF page", e);
            callback.onError("Failed to render PDF page: " + e.getMessage());
        } catch (SecurityException e) {
            Log.e(TAG, "PDF is password protected or corrupted", e);
            callback.onError("PDF is password protected or corrupted");
        } finally {
            // Clean up resources
            if (page != null) {
                try {
                    page.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing PDF page", e);
                }
            }
            if (pdfRenderer != null) {
                try {
                    pdfRenderer.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing PDF renderer", e);
                }
            }
            if (fileDescriptor != null) {
                try {
                    fileDescriptor.close();
                } catch (Exception e) {
                    Log.e(TAG, "Error closing file descriptor", e);
                }
            }
        }
        
    } catch (Exception e) {
        Log.e(TAG, "Error extracting PDF cover", e);
        callback.onError("Failed to extract PDF cover: " + e.getMessage());
    }
}
```

}