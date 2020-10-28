/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.GroupKey;
import stroom.dashboard.expression.v1.Val;
import stroom.mapreduce.v2.BlockingPairQueue;
import stroom.mapreduce.v2.PairQueue;
import stroom.mapreduce.v2.UnsafePairQueue;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.TableSettings;

import java.util.List;
import java.util.Map;

public class TableCoprocessor implements Coprocessor {
    private final PairQueue<GroupKey, Item> queue;
    private final ItemMapper mapper;

    private final CompiledDepths compiledDepths;

    public TableCoprocessor(final TableCoprocessorSettings settings,
                            final FieldIndexMap fieldIndexMap,
                            final Map<String, String> paramMap) {
        final TableSettings tableSettings = settings.getTableSettings();

        final List<Field> fields = tableSettings.getFields();
        compiledDepths = new CompiledDepths(fields, tableSettings.showDetail());
        final CompiledFields compiledFields = new CompiledFields(fields, fieldIndexMap, paramMap);

        queue = new BlockingPairQueue<>(settings.getQueueCapacity());
        mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth());
    }

    public TableCoprocessor(final PairQueue<GroupKey, Item> queue, final CompiledFields compiledFields, final CompiledDepths compiledDepths) {
        this.queue = queue;
        this.compiledDepths = compiledDepths;
        mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth());
    }

    @Override
    public void receive(final Val[] values) {
        mapper.collect(null, values);
    }

    @Override
    public Payload createPayload() {
        final UnsafePairQueue<GroupKey, Item> outputQueue = new UnsafePairQueue<>();

        // Create a partitioner to perform result reduction if needed.
        final ItemPartitioner partitioner = new ItemPartitioner(compiledDepths.getDepths(),
                compiledDepths.getMaxDepth());
        partitioner.setOutputCollector(outputQueue);

        // Partition the data prior to forwarding to the target node.
        partitioner.read(queue);

        // Perform partitioning.
        partitioner.partition();

        // Don't create a payload if the queue is empty.
        if (outputQueue.size() == 0) {
            return null;
        }

        return new TablePayload(outputQueue);
    }
}
