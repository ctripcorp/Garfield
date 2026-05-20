package com.ctrip.garfield.example.model;

import com.ctrip.garfield.engine.wrapper.KvValueWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * KV wrapper carrying serialized order data as byte array.
 *
 * @author Trip.com Group
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderKvWrapper extends KvValueWrapper {
    private byte[] value;
}
