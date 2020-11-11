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

import stroom.docref.DocRef;
import stroom.query.api.v2.TableSettings;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class TableCoprocessorSettings implements CoprocessorSettings {
    private static final int DEFAULT_QUEUE_CAPACITY = 1000000;

    @JsonProperty
    private final CoprocessorKey coprocessorKey;
    @JsonProperty
    private final TableSettings tableSettings;

    private volatile int queueCapacity = DEFAULT_QUEUE_CAPACITY;

    @JsonCreator
    public TableCoprocessorSettings(@JsonProperty("coprocessorKey") final CoprocessorKey coprocessorKey,
                                    @JsonProperty("tableSettings") final TableSettings tableSettings) {
        this.coprocessorKey = coprocessorKey;
        this.tableSettings = tableSettings;
    }

    @Override
    public CoprocessorKey getCoprocessorKey() {
        return coprocessorKey;
    }

    public TableSettings getTableSettings() {
        return tableSettings;
    }

    @Override
    public boolean extractValues() {
        return tableSettings.extractValues();
    }

    @Override
    public DocRef getExtractionPipeline() {
        return tableSettings.getExtractionPipeline();
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public void setQueueCapacity(final int queueCapacity) {
        this.queueCapacity = queueCapacity;
    }
}
