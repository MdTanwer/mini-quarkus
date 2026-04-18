package com.tanwir.it;

import com.tanwir.config.MiniConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigTest {

    @Test
    void testMiniConfigBasicFunctionality() {
        MiniConfig config = MiniConfig.getInstance();
        
        // Test basic property reading (default profile is dev, so we get dev profile value)
        String message = config.getValue("greeting.message");
        assertEquals("Hello from dev profile!", message);
        
        // Test integer property reading
        Integer count = config.getIntegerValue("greeting.count");
        assertEquals(Integer.valueOf(5), count);
        
        // Test boolean property reading
        Boolean enabled = config.getBooleanValue("greeting.enabled");
        assertEquals(Boolean.TRUE, enabled);
        
        // Test default values
        String missing = config.getValue("missing.property", "default");
        assertEquals("default", missing);
        
        Integer missingInt = config.getIntegerValue("missing.int", 42);
        assertEquals(Integer.valueOf(42), missingInt);
    }
    
    @Test
    void testGreetingServiceConfigInjection() {
        GreetingService service = new GreetingService();
        GreetingServiceConfigLoader loader = new GreetingServiceConfigLoader();
        
        // Inject configuration
        loader.inject(service);
        
        // Verify injected values (default profile is dev, so we get dev profile value)
        assertEquals("Hello from dev profile!", service.message);
        assertEquals(5, service.count);
        assertTrue(service.enabled);
        
        // Test the message method uses injected values
        String result = service.message();
        assertEquals("Hello from dev profile! (count: 5)", result);
    }
    
    @Test
    void testProfileSupport() {
        MiniConfig config = MiniConfig.getInstance();
        
        // Test with default profile (dev)
        String devMessage = config.getValue("greeting.message");
        System.out.println("Default profile message: " + devMessage);
        assertEquals("Hello from dev profile!", devMessage);
        
        // Change to prod profile
        config.setProfile("prod");
        String prodMessage = config.getValue("greeting.message");
        System.out.println("Prod profile message: " + prodMessage);
        assertEquals("Hello from production!", prodMessage);
        
        // Change to test profile
        config.setProfile("test");
        String testMessage = config.getValue("greeting.message");
        System.out.println("Test profile message: " + testMessage);
        assertEquals("Hello from test environment!", testMessage);
    }
}
