/*
 * Copyright 2024 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app.handler;

public final class DirNames {

    private DirNames() {
        // Constants.
    }

    /**
     * This is the temporary receive location for receiving non zip data.
     */
    public static final String RECEIVING_SIMPLE = "01_receiving_simple";

    /**
     * This is the temporary receive location for receiving zip data.
     */
    public static final String RECEIVING_ZIP = "01_receiving_zip";

    /**
     * Where we are collecting multiple data items ready to be aggregated.
     */
    public static final String PRE_AGGREGATES = "21_pre_aggregates";

    /**
     * Some data might be split to form better sized aggregates. This processing is performed here.
     */
    public static final String PRE_AGGREGATE_SPLITTING = "22_splitting";

    /**
     * Once splitting has completed move the split data to this location.
     */
    public static final String PRE_AGGREGATE_SPLIT_OUTPUT = "23_split_output";

    /**
     * Where we form the new aggregate zip files from the collection of parts provided by the pre aggregation process.
     * This is a temporary location where zips are formed prior to transfer to the forwarding input queue.
     */
    public static final String AGGREGATES = "31_aggregates";

    /**
     * Where we perform forwarding.
     */
    public static final String FORWARDING = "50_forwarding";
}
