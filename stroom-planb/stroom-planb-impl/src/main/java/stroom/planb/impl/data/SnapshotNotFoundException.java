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

package stroom.planb.impl.data;

/**
 * Thrown when a snapshot has been requested from a node that stores the shard but that node has no snapshot to
 * give. This is an expected, and usually transient, condition, e.g. the snapshot creation job has not yet run for
 * a newly written shard, so it is reported separately from genuine snapshot failures.
 */
public class SnapshotNotFoundException extends RuntimeException {

    public SnapshotNotFoundException(final String message) {
        super(message);
    }
}
