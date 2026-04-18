package com.tanwir.it;

import com.tanwir.arc.Singleton;
import com.tanwir.config.ConfigProperty;

@Singleton
public final class GreetingService {

    @ConfigProperty(name = "greeting.message")
    public String message;

    @ConfigProperty(name = "greeting.count", defaultValue = "1")
    public int count;

    @ConfigProperty(name = "greeting.enabled", defaultValue = "true")
    public boolean enabled;

    public String message() {
        if (!enabled) {
            return "Greeting service is disabled";
        }
        return message + " (count: " + count + ")";
    }
}
