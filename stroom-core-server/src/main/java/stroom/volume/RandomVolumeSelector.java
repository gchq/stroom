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

public class RandomVolumeSelector implements VolumeSelector {
    public static final String NAME = "Random";

    @Override
    public Volume select(final List<Volume> list) {
        if (list.size() == 0) {
            return null;
        }
        if (list.size() == 1) {
            return list.get(0);
        }

        final double random = Math.random();
        final int index = (int) (random * list.size());

        return list.get(index);
    }

    @Override
    public String getName() {
        return NAME;
    }
}
