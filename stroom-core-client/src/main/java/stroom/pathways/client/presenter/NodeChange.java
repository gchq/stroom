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

package stroom.pathways.client.presenter;

import stroom.pathways.shared.otel.trace.NanoTime;

/**
 * How many traces have changed one node, and when it last changed, worked out from the stored changes.
 *
 * <p>Traces rather than changes: a trace that widened nine of a node's constraints taught it one
 * thing on one occasion, the same as a trace that widened one.
 *
 * <p>Neither is held on the node. The changes already say both, and holding them twice would let the
 * two differ.
 */
class NodeChange {

    private final long count;
    private final NanoTime lastUpdated;

    NodeChange(final long count, final NanoTime lastUpdated) {
        this.count = count;
        this.lastUpdated = lastUpdated;
    }

    /**
     * How many traces have changed this node since it was learnt.
     */
    long getCount() {
        return count;
    }

    NanoTime getLastUpdated() {
        return lastUpdated;
    }
}
