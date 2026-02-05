package com.wynnventory.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

public class InfoCache {
    private static boolean debugMode = true;

    public static final Map<String, PriceEntry> priceCache = new HashMap<>();
    private static final Path cacheFile = Paths.get("wynnventory_price_cache.json");
    public static final long FIVE_DAYS_MS = 5L * 24 * 60 * 60 * 1000;
    public static final long FIVE_MINS_MS = 300000;
    private static long lastSaveTime = 0;

    private static final Set<String> pendingFetches =
            ConcurrentHashMap.newKeySet();

    public static class PriceEntry {
        public double price;
        public long lastFetchTime; // Unix timestamp in milliseconds

        public PriceEntry(double price, long lastFetchTime) {
            this.price = price;
            this.lastFetchTime = lastFetchTime;
        }
    }

    // Load cache from disk
    public static void loadCache() {
        if (Files.exists(cacheFile)) {
            try {
                String json = Files.readString(cacheFile);
                Gson gson = new Gson();
                Type type = new TypeToken<Map<String, PriceEntry>>(){}.getType();
                Map<String, PriceEntry> loadedCache = gson.fromJson(json, type);
                if (loadedCache != null) {
                    priceCache.putAll(loadedCache);
                }
                if (debugMode) System.out.println("Loaded " + priceCache.size() + " cached prices.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // Save cache to disk
    public static void saveCache() {
        try {
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(priceCache);
            Files.writeString(cacheFile, json);
            if (debugMode) System.out.println("Saved " + priceCache.size() + " cached prices.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static final ExecutorService PRICE_EXECUTOR =
            Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "Wynnventory-Price-Fetcher");
                t.setDaemon(true);
                return t;
            });

    public static Double getCachedPriceOrRequest(
            String key,
            Supplier<Double> fetcher
    ) {
        long now = System.currentTimeMillis();
        PriceEntry entry = priceCache.get(key);

        // Fresh cached value → return immediately
        if (entry != null && now - entry.lastFetchTime <= FIVE_MINS_MS) {
            return entry.price;
        }

        // Already fetching → return what we have (or null)
        if (pendingFetches.contains(key)) {
            return entry != null ? entry.price : null;
        }

        // Schedule async fetch
        pendingFetches.add(key);
        PRICE_EXECUTOR.submit(() -> {
            try {
                double price = fetcher.get();
                priceCache.put(key, new PriceEntry(price, System.currentTimeMillis()));
                maybeSave();
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                pendingFetches.remove(key);
            }
        });

        // Return stale or null value immediately
        return entry != null ? entry.price : null;
    }

    private static synchronized void maybeSave() {
        long now = System.currentTimeMillis();
        if (now - lastSaveTime >= FIVE_MINS_MS) {
            lastSaveTime = now;
            saveCache();

            if (debugMode) {
                System.out.println("Auto-saved price cache.");
            }
        }
    }

}
