package es.bsc.hp2c.common.utils;

import java.io.Serializable;
import java.time.Instant;

/**
 * Class collecting information from each measurement taken (timestamp and value).
 *
 * @param <T> Type of the values stored.
 */
public class Measurement<T> implements Serializable{
    Instant timestamp;
    T value;

    public Measurement(Instant timestamp, T value) {
        this.timestamp = timestamp;
        this.value = value;
    }

    @Override
    public String toString() {
        return "Measurement{" +
                "timestamp=" + timestamp +
                ", value=" + (value != null && value.getClass().isArray()
                ? java.util.Arrays.toString((Object[]) value)
                : value) +
                '}';
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public T getValue() {
        return value;
    }
}
