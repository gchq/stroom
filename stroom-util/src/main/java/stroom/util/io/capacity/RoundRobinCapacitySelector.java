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

package stroom.util.io.capacity;

import stroom.util.concurrent.AtomicLoopedItemSequence;
import stroom.util.logging.LogUtil;
import stroom.util.shared.HasCapacity;

import java.util.List;

public class RoundRobinCapacitySelector extends AbstractSelector {

    public static final String NAME = "RoundRobin";

    private final AtomicLoopedItemSequence atomicLoopedIntegerSequence = AtomicLoopedItemSequence.create();

    public RoundRobinCapacitySelector() {
        // No fall back needed
        super(null);
    }

    @Override
    public <T extends HasCapacity> T doSelect(final List<T> filteredList) {
        return atomicLoopedIntegerSequence.getNextItem(filteredList)
                .orElseThrow(() ->
                        new RuntimeException(LogUtil.message(
                                "Should never be null for a non-empty list. List size: {}",
                                filteredList.size())));
    }

    @Override
    public String getName() {
        return NAME;
    }
}
