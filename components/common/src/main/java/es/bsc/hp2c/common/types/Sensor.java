/*
 *  Copyright 2002-2023 Barcelona Supercomputing Center (www.bsc.es)
 * 
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package es.bsc.hp2c.common.types;

import es.bsc.hp2c.common.funcs.Action;
import es.bsc.hp2c.common.utils.MeasurementWindow;

import java.time.Instant;

/**
 * Class collecting information from the electrical network.
 *
 * @param <R> Type of value to receive.
 * @param <V> Type of value to return.
 */
public interface Sensor<R, V> {

    /**
     * Receive a function and add it to the list of onRead functions.
     *
     * @param action Runnable that implements the function to handle.
     */
    public void addOnReadFunction(Action action, int interval, String label, boolean onRead);

    /**
     * Call the functions triggered by a read value in the sensor.
     */
    public void onRead();

    /**
     * Receive the raw sensed value and sets value attribute according to what
     * sensedValue(value) returns.
     *
     * @param values Type of value to return.
     */
    public void sensed(R values, Instant timestamp);

    /**
     * This method receives an encoded MeasurementWindow (from amqpManager), decodes it, and sets the appropriate
     * values for the involved sensors while constructing a new MeasurementWindow<Float[]>, which will be useful to
     * store the sensor values in the database.
     *
     * @param bWindow Encoded MeasurementWindow.
     * @return MeasurementWindow<Float[]>
     */
    public MeasurementWindow<Float[]> sensed(byte[] bWindow);

    /**
     * Get the value stored.
     *
     * @return Value stored.
     */
    public V getCurrentValues();

    /**
     * Get current values formatted as byte array to be sent a via messaging protocol.
     *
     * @return Byte array message with the set of current values
     */
    public byte[] encodeValuesSensor();

    /**
     * Get sensor window
     *
     * @return Measurement Window
     */
    public MeasurementWindow<V> getWindow();

    /**
     * Decode values from a message made of an array of bytes.
     *
     * @param messageBytes contains the values of the sensor.
     * @return The actual values in the corresponding data type.
     */
    public R decodeValuesSensor(byte[] messageBytes);
}
