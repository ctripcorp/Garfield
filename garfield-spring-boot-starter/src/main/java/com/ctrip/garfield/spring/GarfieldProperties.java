package com.ctrip.garfield.spring;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "garfield")
public class GarfieldProperties {
    private String configPath = "classpath:garfield-config.json";
    private Follower follower = new Follower();

    @Data
    public static class Follower {
        private long timeoutMs = 5000;
    }
}
