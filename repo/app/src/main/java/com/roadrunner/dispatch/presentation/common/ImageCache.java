package com.roadrunner.dispatch.presentation.common;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import java.io.InputStream;

/**
 * Simple LRU bitmap cache capped at 20 MB (or 1/8 of the VM heap, whichever is smaller).
 *
 * <p>Use {@link #getInstance()} to obtain the process-wide singleton. Images can be
 * decoded at a sensible sample size via {@link #decodeSampled(InputStream, int, int)}
 * and then stored/retrieved with {@link #put(String, Bitmap)} / {@link #get(String)}.
 */
public class ImageCache {

    private static ImageCache INSTANCE;

    private final LruCache<String, Bitmap> cache;

    private ImageCache() {
        // Cap at 20 MB, or 1/8 of available VM heap — whichever is smaller.
        int maxSizeKb = Math.min(20 * 1024, (int) (Runtime.getRuntime().maxMemory() / 1024 / 8));
        cache = new LruCache<String, Bitmap>(maxSizeKb) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount() / 1024;
            }
        };
    }

    /** Returns the singleton instance, creating it on first call. */
    public static synchronized ImageCache getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ImageCache();
        }
        return INSTANCE;
    }

    /** Retrieve a cached bitmap by key, or {@code null} if not cached. */
    public Bitmap get(String key) {
        return cache.get(key);
    }

    /** Store a bitmap in the cache under the given key. */
    public void put(String key, Bitmap bitmap) {
        cache.put(key, bitmap);
    }

    /** Maximum raw file size (in bytes) we are willing to buffer for decode. */
    private static final int MAX_FILE_SIZE_BYTES = 10 * 1024 * 1024; // 10 MB

    /**
     * Decode a {@link Bitmap} from {@code is}, downsampling so that neither dimension
     * exceeds {@code reqWidth} x {@code reqHeight}.
     *
     * <p>Files larger than 10 MB are rejected to prevent OOM from buffering.
     * The stream is consumed fully; the caller is responsible for closing it.
     */
    public static Bitmap decodeSampled(InputStream is, int reqWidth, int reqHeight) {
        // Buffer stream with a hard size cap to prevent OOM on very large images.
        byte[] data;
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int read;
            int total = 0;
            while ((read = is.read(buf)) != -1) {
                total += read;
                if (total > MAX_FILE_SIZE_BYTES) {
                    return null; // Image too large — refuse to buffer
                }
                baos.write(buf, 0, read);
            }
            data = baos.toByteArray();
        } catch (java.io.IOException e) {
            return null;
        }

        // First pass: measure raw dimensions without allocating pixels.
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, options);

        // Compute sub-sampling factor.
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight);

        // Second pass: decode at reduced size.
        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    /**
     * Calculate the largest power-of-two {@code inSampleSize} that keeps the decoded
     * image at or above the requested dimensions.
     */
    private static int calculateInSampleSize(BitmapFactory.Options options, int reqW, int reqH) {
        int height = options.outHeight;
        int width = options.outWidth;
        int inSampleSize = 1;

        if (height > reqH || width > reqW) {
            int halfH = height / 2;
            int halfW = width / 2;
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2;
            }
        }

        return inSampleSize;
    }
}
