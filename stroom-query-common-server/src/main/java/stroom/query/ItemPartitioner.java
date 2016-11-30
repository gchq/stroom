/*
 * Copyright 2016 Crown Copyright
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

package stroom.query;

import stroom.mapreduce.OutputCollector;
import stroom.mapreduce.Reducer;
import stroom.mapreduce.SimplePartitioner;

public class ItemPartitioner extends SimplePartitioner<String, Item, String, Item> {
    private final ItemReducer itemReducer;
    private OutputCollector<String, Item> outputCollector;

    public ItemPartitioner(final int[] depths, final int maxDepth) {
        // Create a reusable reducer as it doesn't hold state.
        itemReducer = new ItemReducer(depths, maxDepth);
    }

    @Override
    protected Reducer<String, Item, String, Item> createReducer() {
        // Reuse the same reducer as there is no state.
        return itemReducer;
    }

    @Override
    protected void collect(final String key, final Item value) {
        // The standard collect method is overridden so that items with a null
        // key are passed straight to the output collector and will not undergo
        // partitioning and reduction as we don't want to group items with null
        // keys.
        if (key != null) {
            super.collect(key, value);
        } else {
            outputCollector.collect(key, value);
        }
    }

    @Override
    public void setOutputCollector(final OutputCollector<String, Item> outputCollector) {
        super.setOutputCollector(outputCollector);
        this.outputCollector = outputCollector;
    }
}
