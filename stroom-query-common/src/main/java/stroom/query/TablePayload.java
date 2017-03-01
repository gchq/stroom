/*
 * Copyright 2016 Crown Copyright
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

package stroom.query;

import stroom.mapreduce.UnsafePairQueue;

public class TablePayload implements Payload {
    private static final long serialVersionUID = 5271438218782010968L;

    private UnsafePairQueue<Key, Item> queue;

    public TablePayload() {
    }

    public TablePayload(final UnsafePairQueue<Key, Item> queue) {
        this.queue = queue;
    }

    public UnsafePairQueue<Key, Item> getQueue() {
        return queue;
    }
}
