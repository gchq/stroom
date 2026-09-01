/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.api;

/**
 * Summarises tabular data that is too big to put in front of a model in one go.
 * <p>
 * The data is split into row-bounded batches, each batch is summarised in its own call, and the batch
 * summaries are merged into one answer. A batch that fails costs its own rows and nothing else, and the
 * answer says how much of the data it actually covers, so that a partial answer is not read as a whole one.
 * </p>
 */
public interface TableSummariser {

    /**
     * @return The summary, never null. Where nothing could be summarised - no data, or no batch produced
     * anything - the return says so rather than being empty.
     */
    String summarise(TableSummaryRequest request);
}
