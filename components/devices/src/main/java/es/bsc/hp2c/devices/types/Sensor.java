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

package es.bsc.hp2c.devices.types;

/**
 * Class collecting information from the electrical network.
 *
 * @param <V> Type of value to return
 */
public interface Sensor<V> {

    /** 
     * Class containing the function to notify when a new value is sensed.
     */
    public static interface SensorProcessing {
        /**
         * 
         * @param values 
         */
        public void sensed(float... values);
    }
    
    
    /**
     * Return a set of Processing Functions to execute upon sensing.
     * 
     * @return a set of Processing Functions to execute upon sensing
     */
    public SensorProcessing[] getProcessors();
    
        
    public V getCurrentValue();
}
