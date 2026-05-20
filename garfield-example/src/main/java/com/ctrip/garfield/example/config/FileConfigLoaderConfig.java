package com.ctrip.garfield.example.config;

import com.ctrip.garfield.common.config.ConfigLoader;
import com.ctrip.garfield.common.config.StorageTypeRegistry;
import com.ctrip.garfield.spring.GarfieldProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;

/**
 * Example-only Spring configuration that wires {@link FileConfigLoader} as the
 * {@link com.ctrip.garfield.common.config.ConfigLoader} implementation.
 * To use a different config source (e.g., Apollo or any config center), replace
 * this class with your own {@code @Bean} definition.
 *
 * @author Trip.com Group
 */
@Configuration(proxyBeanMethods = false)
public class FileConfigLoaderConfig {

    @Bean
    public ConfigLoader configLoader(GarfieldProperties properties,
                                     StorageTypeRegistry registry,
                                     ResourceLoader resourceLoader) throws IOException {
        String path = properties.getConfigPath();
        if (path.startsWith("classpath:")) {
            Resource resource = resourceLoader.getResource(path);
            path = resource.getFile().getAbsolutePath();
        }
        return new FileConfigLoader(path, registry);
    }
}
