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


import stroom.util.shared.HasCapacity;

import java.util.List;

public class RandomCapacitySelector extends AbstractSelector {

    public static final String NAME = "Random";

    public RandomCapacitySelector() {
        super(null);
    }

    @Override
    public <T extends HasCapacity> T doSelect(final List<T> filteredList) {
        final double random = Math.random();
        final int index = (int) (random * filteredList.size());
        return filteredList.get(index);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
