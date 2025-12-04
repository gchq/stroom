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

package stroom.data.client;

import stroom.pipeline.shared.SourceLocation;

import java.util.Objects;

public class DataPreviewKey {

    private final long metaId;

    public DataPreviewKey(final SourceLocation sourceLocation) {
        this.metaId = sourceLocation.getMetaId();
    }

    public long getMetaId() {
        return metaId;
    }

    @Override
    public boolean equals(final Object o) {

        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final DataPreviewKey that = (DataPreviewKey) o;
        return metaId == that.metaId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(metaId);
    }

    @Override
    public String toString() {
        return "DataPreviewKey{" +
                "metaId=" + metaId +
                '}';
    }
}

