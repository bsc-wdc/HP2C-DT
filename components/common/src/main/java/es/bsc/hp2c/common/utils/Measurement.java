package es.bsc.hp2c.common.utils;

import java.time.Instant;

public class Measurement<T> {
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
