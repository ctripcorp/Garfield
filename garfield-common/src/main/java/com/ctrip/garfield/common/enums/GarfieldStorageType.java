package com.ctrip.garfield.common.enums;

/**
 * Built-in storage medium types provided by Garfield.
 *
 * <p>Users may extend the set of supported media by implementing {@link StorageType}
 * with their own enum.
 *
 * @author Trip.com Group
 */
public enum GarfieldStorageType implements StorageType {
    REDIS, MYSQL, POSTGRESQL, HBASE, MONGODB, ELASTICSEARCH,
    KAFKA, RABBITMQ, ROCKETMQ, HTTP, GRPC
}
