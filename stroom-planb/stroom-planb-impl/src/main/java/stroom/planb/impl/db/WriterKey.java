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

package stroom.planb.impl.db;

import stroom.planb.shared.PlanBDocument;

import java.util.Objects;

/**
 * Composite key identifying a single open LMDB environment within a
 * {@link PlanBStreamWriter}: one (doc, shardIndex) pair.
 */
class WriterKey {

    final PlanBDocument doc;
    final int shardIndex;

    WriterKey(final PlanBDocument doc, final int shardIndex) {
        this.doc = doc;
        this.shardIndex = shardIndex;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final WriterKey that = (WriterKey) o;
        return shardIndex == that.shardIndex && Objects.equals(doc, that.doc);
    }

    @Override
    public int hashCode() {
        return Objects.hash(doc, shardIndex);
    }
}
