package com.ctrip.garfield.common.spi.defaults;

import com.ctrip.garfield.common.exception.SerializationException;
import com.ctrip.garfield.common.spi.GarfieldSerializer;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.util.List;

/**
 * Jackson-based default implementation. The {@link ObjectMapper} is fully
 * encapsulated internally and not exposed to callers.
 *
 * @author Trip.com Group
 */
public class JacksonSerializer implements GarfieldSerializer {

    private final ObjectMapper mapper;

    public JacksonSerializer() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public byte[] serialize(Object obj) {
        try {
            return mapper.writeValueAsBytes(obj);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try {
            return mapper.readValue(data, clazz);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> List<T> deserializeList(byte[] data, Class<T> elementClazz) {
        try {
            var type = mapper.getTypeFactory().constructCollectionType(List.class, elementClazz);
            return mapper.readValue(data, type);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public String serializeToString(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> T deserialize(String data, Class<T> clazz) {
        try {
            return mapper.readValue(data, clazz);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }

    @Override
    public <T> List<T> deserializeList(String data, Class<T> elementClazz) {
        try {
            var type = mapper.getTypeFactory().constructCollectionType(List.class, elementClazz);
            return mapper.readValue(data, type);
        } catch (IOException e) {
            throw new SerializationException(e);
        }
    }
}
