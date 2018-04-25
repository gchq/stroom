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

package stroom.statistics.server.common.search;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.dashboard.expression.v1.Var;
import stroom.mapreduce.BlockingPairQueue;
import stroom.mapreduce.PairQueue;
import stroom.mapreduce.UnsafePairQueue;
import stroom.query.CompiledDepths;
import stroom.query.CompiledFields;
import stroom.query.Item;
import stroom.query.ItemMapper;
import stroom.query.ItemPartitioner;
import stroom.query.Payload;
import stroom.query.TableCoprocessorSettings;
import stroom.query.TablePayload;
import stroom.query.shared.Field;
import stroom.query.shared.TableSettings;
import stroom.util.shared.HasTerminate;

import java.util.List;
import java.util.Map;

public class StatStoreTableCoprocessor {
    private final Integer id;
    private final PairQueue<String, Item> queue;
    private final ItemMapper mapper;

    private final CompiledFields compiledFields;
    private final CompiledDepths compiledDepths;

    public StatStoreTableCoprocessor(final Integer id,
                                     final TableCoprocessorSettings settings,
                                     final FieldIndexMap fieldIndexMap,
                                     final HasTerminate monitor,
                                     final Map<String, String> paramMap) {

        this.id = id;
        final TableSettings tableSettings = settings.getTableSettings();

        final List<Field> fields = tableSettings.getFields();
        compiledDepths = new CompiledDepths(fields, tableSettings.showDetail());
        compiledFields = new CompiledFields(fields, fieldIndexMap, paramMap);

        queue = new BlockingPairQueue<>(monitor);
        mapper = new ItemMapper(queue, compiledFields, compiledDepths.getMaxDepth(), compiledDepths.getMaxGroupDepth());
    }

    public void receive(final Var[] values) {
        mapper.collect(null, values);
    }

    public Payload createPayload() {
        final UnsafePairQueue<String, Item> outputQueue = new UnsafePairQueue<>();

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

    Integer getId() {
        return id;
    }
}
