package com.tanwir.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mini configuration system that loads application.properties.
 * Similar to SmallRye Config but simplified.
 */
public class MiniConfig {
    
    private static final MiniConfig INSTANCE = new MiniConfig();
    private final Properties properties = new Properties();
    private final Properties profileProperties = new Properties();
    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();
    private String currentProfile = "dev"; // Default profile
    
    private MiniConfig() {
        loadConfiguration();
    }
    
    public static MiniConfig getInstance() {
        return INSTANCE;
    }
    
    /**
     * Get a configuration value as a String.
     */
    public String getValue(String key) {
        return getValue(key, null);
    }
    
    /**
     * Get a configuration value as a String with default.
     */
    public String getValue(String key, String defaultValue) {
        return cache.computeIfAbsent(key, k -> {
            // Check profile-specific property first (stored with base key in profileProperties)
            String value = profileProperties.getProperty(key);
            
            // Fall back to base property
            if (value == null) {
                value = properties.getProperty(key);
            }
            
            // Fall back to default
            return value != null ? value : defaultValue;
        });
    }
    
    /**
     * Get a configuration value as an Integer.
     */
    public Integer getIntegerValue(String key) {
        return getIntegerValue(key, null);
    }
    
    /**
     * Get a configuration value as an Integer with default.
     */
    public Integer getIntegerValue(String key, Integer defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
    
    /**
     * Get a configuration value as a Boolean.
     */
    public Boolean getBooleanValue(String key) {
        return getBooleanValue(key, null);
    }
    
    /**
     * Get a configuration value as a Boolean with default.
     */
    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        String value = getValue(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Set the active profile.
     */
    public void setProfile(String profile) {
        this.currentProfile = profile;
        cache.clear(); // Clear cache to reload with new profile
        loadProfileProperties(); // Reload profile-specific properties
    }
    
    /**
     * Get the current active profile.
     */
    public String getProfile() {
        return currentProfile;
    }
    
    private void loadConfiguration() {
        try (InputStream baseStream = getClass().getClassLoader().getResourceAsStream("application.properties")) {
            if (baseStream != null) {
                properties.load(baseStream);
            }
        } catch (IOException e) {
            // application.properties not found, that's okay
        }
        
        loadProfileProperties();
    }
    
    private void loadProfileProperties() {
        // Clear existing profile properties
        profileProperties.clear();
        
        // Load profile-specific properties from separate file
        try (InputStream profileStream = getClass().getClassLoader()
                .getResourceAsStream("application-" + currentProfile + ".properties")) {
            if (profileStream != null) {
                profileProperties.load(profileStream);
            }
        } catch (IOException e) {
            // Profile-specific file not found, that's okay
        }
        
        // Also load profile-specific properties from main file with %profile. prefix
        for (String propertyName : properties.stringPropertyNames()) {
            if (propertyName.startsWith("%" + currentProfile + ".")) {
                String baseKey = propertyName.substring(currentProfile.length() + 2);
                profileProperties.setProperty(baseKey, properties.getProperty(propertyName));
            }
        }
    }
}
