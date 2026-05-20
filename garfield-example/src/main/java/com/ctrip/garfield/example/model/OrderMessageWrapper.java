package com.ctrip.garfield.example.model;

import com.ctrip.garfield.engine.wrapper.MessageWrapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Example concrete MQ wrapper. All fields live in the base class; kept as
 * a subclass to demonstrate the extension point pattern.
 *
 * @author Trip.com Group
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class OrderMessageWrapper extends MessageWrapper {
}
