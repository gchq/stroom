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

package stroom.index.impl;

import stroom.index.shared.IndexShard;
import stroom.query.api.datasource.IndexField;
import stroom.query.language.functions.ValuesConsumer;
import stroom.query.language.functions.ref.ErrorConsumer;
import stroom.task.api.TaskContext;

import java.util.Set;
import java.util.concurrent.atomic.LongAdder;

public interface LuceneShardSearcher {

    void searchShard(final TaskContext taskContext,
                     final IndexShard indexShard,
                     final IndexField[] storedFields,
                     final Set<String> fieldsToLoad,
                     final LongAdder hitCount,
                     final int shardNumber,
                     final int shardTotal,
                     final ValuesConsumer valuesConsumer,
                     final ErrorConsumer errorConsumer);
}
