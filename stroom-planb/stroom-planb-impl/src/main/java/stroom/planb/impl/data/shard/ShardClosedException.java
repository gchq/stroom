/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.data.shard;

/**
 * Thrown when an operation is attempted on a shard whose environment has been closed/evicted.
 * {@link ShardManager} catches this and retries with a freshly (re)created shard — so a reader that
 * grabbed a shard instance just as it was being idle-evicted transparently re-resolves a live one.
 */
public class ShardClosedException extends RuntimeException {

    public ShardClosedException() {
        super("Shard has been closed");
    }

    public ShardClosedException(final String message) {
        super(message);
    }
}
