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


package stroom.index.impl;

import stroom.util.shared.HasPrimitiveValue;
import stroom.util.shared.PrimitiveValueConverter;

/// The state of a row that records a stream with documents in an index shard that are not yet known to be
/// committed.
public enum PendingStreamState implements HasPrimitiveValue {
    /// The task is adding the stream's documents to the shard.
    WRITING(0),
    /// The task has finished but the shard has not committed since.
    WRITTEN(1),
    /// The shard has committed the documents. The row is removed once all of the stream's rows are committed.
    COMMITTED(2),
    /// The documents are to be removed, but the task that was writing them may still be adding more, so
    /// not until it has stopped.
    ABANDONED(3),
    /// The documents are to be removed from the shard.
    DELETE(4),
    /// On the attempt row only: the task is to be run again once the stream's documents have been removed.
    RETRY_PENDING(5),
    /// On the attempt row only: the task has been put back to run again.
    RETRYING(6);

    public static final PrimitiveValueConverter<PendingStreamState> PRIMITIVE_VALUE_CONVERTER =
            PrimitiveValueConverter.create(PendingStreamState.class, PendingStreamState.values());

    private final byte primitiveValue;

    PendingStreamState(final int primitiveValue) {
        this.primitiveValue = (byte) primitiveValue;
    }

    @Override
    public byte getPrimitiveValue() {
        return primitiveValue;
    }
}
