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

import stroom.dashboard.expression.v1.GroupKey;
import stroom.mapreduce.v2.UnsafePairQueue;

import java.util.Objects;

public class TablePayload implements Payload {
    private static final long serialVersionUID = 5271438218782010968L;

    private UnsafePairQueue<GroupKey, Item> queue;

    public TablePayload() {
    }

    public TablePayload(final UnsafePairQueue<GroupKey, Item> queue) {
        this.queue = queue;
    }

    public UnsafePairQueue<GroupKey, Item> getQueue() {
        return queue;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final TablePayload that = (TablePayload) o;
        return Objects.equals(queue, that.queue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(queue);
    }
}
