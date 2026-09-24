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

package stroom.data.store.impl;

import stroom.meta.shared.DataRetentionFields;

enum DataKey {
    DELETED_STREAM_ID("Deleted Stream Id", "Identifier of a stream that has been deleted."),
    STREAM_ID("Stream Id", "Unique identifier for this stream."),
    STATUS("Status", "Current processing status of this stream."),
    STATUS_MS("Status Ms", "Time the stream status last changed."),
    PARENT_STREAM_ID("Parent Stream Id", "Identifier of the parent stream this stream was derived from."),
    CREATED("Created", "Time this stream was created."),
    EFFECTIVE("Effective", "Effective time assigned to this stream."),
    STREAM_TYPE("Stream Type", "Type of data stored in this stream."),
    FEED("Feed", "Feed associated with this stream."),
    DATA_ENCODING("Data Encoding", "Character encoding used for the stream data."),
    VOLUME_GROUP("Volume Group", "Storage volume group containing this stream."),
    VOLUME_TYPE("Volume Type", "Storage volume type containing this stream."),
    READ_ONLY_DATA("Read-Only Data", "Whether the stream data is read-only."),
    PROCESSOR("Processor", "Identifier of the processor that created this stream."),
    PROCESSOR_PIPELINE("Processor Pipeline", "Pipeline used by the processor that created this stream."),
    PROCESSOR_FILTER_ID("Processor Filter Id", "Identifier of the processor filter that created this stream."),
    PROCESSOR_TASK_ID("Processor Task Id", "Identifier of the processor task that created this stream."),
    RETENTION_AGE(DataRetentionFields.RETENTION_AGE, "Retention age applied to this stream."),
    RETENTION_UNTIL(DataRetentionFields.RETENTION_UNTIL, "Time until which this stream will be retained."),
    RETENTION_RULE(DataRetentionFields.RETENTION_RULE, "Retention rule applied to this stream.");

    private final String displayName;
    private final String helpText;

    DataKey(final String displayName, final String helpText) {
        this.displayName = displayName;
        this.helpText = helpText;
    }

    String getDisplayName() {
        return displayName;
    }

    String getHelpText() {
        return helpText;
    }
}
