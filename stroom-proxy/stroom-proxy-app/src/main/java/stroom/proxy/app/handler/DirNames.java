/*
 * Copyright 2016-2025 Crown Copyright
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
     * Where we queue zips that need to be split by feed.
     */
    public static final String SPLIT_ZIP_QUEUE = "02_split_zip_input_queue";

    /**
     * This is the location for splitting source data where zip files contain multiple feeds.
     */
    public static final String SPLIT_ZIP = "03_split_zip_splits";

//    /**
//     * This is the final receive location for received zip data as we might have split the zip data.
//     */
//    public static final String RECEIVED_ZIP = "03_received_zip";

    /**
     * Where we queue data prior to the pre aggregation process picking it up.
     */
    public static final String PRE_AGGREGATE_INPUT_QUEUE = "20_pre_aggregate_input_queue";

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
     * Where we queue data ready for aggregation.
     */
    public static final String AGGREGATE_INPUT_QUEUE = "30_aggregate_input_queue";

    /**
     * Where we form the new aggregate zip files from the collection of parts provided by the pre aggregation process.
     * This is a temporary location where zips are formed prior to transfer to the forwarding input queue.
     */
    public static final String AGGREGATES = "31_aggregates";

    /**
     * Where we queue data ready for forwarding.
     */
    public static final String FORWARDING_INPUT_QUEUE = "40_forwarding_input_queue";

    /**
     * Where we perform forwarding.
     */
    public static final String FORWARDING = "50_forwarding";
}
