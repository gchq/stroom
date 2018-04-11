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

package stroom.volume;

import stroom.node.shared.Volume;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoundRobinVolumeSelector implements VolumeSelector {
    public static final String NAME = "RoundRobin";

    private static final AtomicInteger roundRobinPosition = new AtomicInteger();

    @Override
    public Volume select(final List<Volume> list) {
        if (list.size() == 0) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }

        final int pos = roundRobinPosition.incrementAndGet();

        // Ensure the position is limited.
        if (pos > 1000000) {
            synchronized (roundRobinPosition) {
                if (roundRobinPosition.get() > 10000) {
                    roundRobinPosition.addAndGet(-10000);
                }
            }
        }

        final int index = pos % list.size();
        return list.get(index);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
