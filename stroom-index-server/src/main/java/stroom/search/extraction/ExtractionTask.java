/*
 * Copyright 2017 Crown Copyright
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

package stroom.search.extraction;

import stroom.dashboard.expression.v1.FieldIndexMap;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.query.api.v2.DocRef;

public class ExtractionTask {
    private final long streamId;
    private final long[] eventIds;
    private final DocRef pipelineRef;
    private final FieldIndexMap fieldIndexes;
    private final ResultReceiver resultReceiver;
    private final ErrorReceiver errorReceiver;

    ExtractionTask(final long streamId,
                   final long[] eventIds,
                   final DocRef pipelineRef,
                   final FieldIndexMap fieldIndexes,
                   final ResultReceiver resultReceiver,
                   final ErrorReceiver errorReceiver) {
        this.streamId = streamId;
        this.eventIds = eventIds;
        this.pipelineRef = pipelineRef;
        this.fieldIndexes = fieldIndexes;
        this.resultReceiver = resultReceiver;
        this.errorReceiver = errorReceiver;
    }

    public long getStreamId() {
        return streamId;
    }

    public long[] getEventIds() {
        return eventIds;
    }

    public DocRef getPipelineRef() {
        return pipelineRef;
    }

    public FieldIndexMap getFieldIndexes() {
        return fieldIndexes;
    }

    public ResultReceiver getResultReceiver() {
        return resultReceiver;
    }

    public ErrorReceiver getErrorReceiver() {
        return errorReceiver;
    }

    public interface ResultReceiver {
        void receive(String[] values);
    }
}
