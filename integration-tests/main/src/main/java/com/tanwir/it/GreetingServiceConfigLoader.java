package com.tanwir.it;

import com.tanwir.config.MiniConfig;

/**
 * Generated configuration loader for GreetingService
 */
public class GreetingServiceConfigLoader {
    
    private final MiniConfig config = MiniConfig.getInstance();
    
    public void inject(GreetingService target) {
        try {
            target.message = config.getValue("greeting.message", "Hello from mini-quarkus configuration!");
            target.count = config.getIntegerValue("greeting.count", 1);
            target.enabled = config.getBooleanValue("greeting.enabled", true);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject configuration for 'message'", e);
        }
    }
    
    public static GreetingService createWithConfig(GreetingService target) {
        GreetingServiceConfigLoader loader = new GreetingServiceConfigLoader();
        loader.inject(target);
        return target;
    }
}
