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
package es.bsc.hp2c.devices.opalrt;

import es.bsc.hp2c.devices.types.Sensor;
import es.bsc.hp2c.devices.types.Sensor.SensorProcessing;
import java.util.ArrayList;
import java.util.List;

/**
 * This class handles the interconnection with the OpalRT to read the values.
 */
public class OpalReader {

    private static final List<OpalSensor> sensors = new ArrayList<>();
    private static float[] values = new float[25];

    static {
        Thread t = new Thread() {
            public void run() {
                while (true) {
                    for (int i = 0; i < values.length; i++) {
                        values[i] = (float) Math.random();
                    }
                    synchronized (OpalReader.sensors) {
                        for (OpalSensor sensor : sensors) {
                            int idx = sensor.getIndex();
                            for (SensorProcessing sp : sensor.getProcessors()) {
                                sp.sensed(values[idx]);
                            }
                        }
                    }
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException ex) {
                        // Ignore
                    }
                }
            }
        };
        t.setName("OpalReader");
        t.start();

    }

    public static void registerDevice(OpalSensor sensor) {
        synchronized (OpalReader.sensors) {
            sensors.add(sensor);
        }
    }

    protected static interface OpalSensor<V> extends Sensor<V> {

        public int getIndex();
    }

}
