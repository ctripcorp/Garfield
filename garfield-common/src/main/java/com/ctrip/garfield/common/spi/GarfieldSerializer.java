package com.ctrip.garfield.common.spi;

import java.util.List;

/**
 * Project-wide serialization SPI. Centered on two representations — byte array and string —
 * and designed for JSON-style encodings. Other encodings (e.g., Protobuf, Thrift) must
 * define their own string representation convention (e.g., Base64).
 *
 * <p>Usage points:
 * <ul>
 *   <li>The Transfer layer converts business objects to the byte[] / String representation
 *       stored in engine wrappers.
 *   <li>Compensation messages are symmetrically (de)serialized on both the channel and
 *       consumer sides.
 *   <li>During compensation consumption, {@code CompensationMessage.requestData} is
 *       restored to a list of business DataUnit objects.
 * </ul>
 *
 * <p>All failures must be wrapped and thrown as
 * {@link com.ctrip.garfield.common.exception.SerializationException}.
 *
 * @author Trip.com Group
 */
public interface GarfieldSerializer {

    /** Serializes an object to a byte array. */
    byte[] serialize(Object obj);

    /** Deserializes a byte array to a single object. */
    <T> T deserialize(byte[] data, Class<T> clazz);

    /** Deserializes a byte array to a list. Convenience method to avoid exposing library-specific TypeReference. */
    <T> List<T> deserializeList(byte[] data, Class<T> elementClazz);

    /** Serializes an object to a string. */
    String serializeToString(Object obj);

    /** Deserializes a string to a single object. */
    <T> T deserialize(String data, Class<T> clazz);

    /** Deserializes a string to a list. */
    <T> List<T> deserializeList(String data, Class<T> elementClazz);
}
