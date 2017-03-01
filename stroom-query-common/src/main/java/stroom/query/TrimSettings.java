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

import java.util.List;

public class TrimSettings {
    private final Integer[] trimSizes;
    private final int defaultSize;

    public TrimSettings(final List<Integer> storeTrimSizes, final List<Integer> defaultStoreTrimSizes) {
        trimSizes = getStoreTrimSizes(storeTrimSizes, defaultStoreTrimSizes);

        if (trimSizes.length > 0) {
            defaultSize = trimSizes[trimSizes.length - 1];
        } else {
            defaultSize = Integer.MAX_VALUE;
        }
    }

    private Integer[] getStoreTrimSizes(final List<Integer> storeTrimSizes, final List<Integer> defaultStoreTrimSizes) {
        int size = 0;
        if (storeTrimSizes != null) {
            size = storeTrimSizes.size();
        }
        if (defaultStoreTrimSizes != null && defaultStoreTrimSizes.size() > size) {
            size = defaultStoreTrimSizes.size();
        }

        final Integer[] array = new Integer[size];
        for (int i = 0; i < size; i++) {
            if (defaultStoreTrimSizes != null && i < defaultStoreTrimSizes.size()) {
                array[i] = defaultStoreTrimSizes.get(i);
            }

            if (storeTrimSizes != null && i < storeTrimSizes.size()) {
                if (array[i] == null || (storeTrimSizes.get(i) != null && array[i] < storeTrimSizes.get(i))) {
                    array[i] = storeTrimSizes.get(i);
                }
            }

            if (array[i] == null) {
                array[i] = Integer.MAX_VALUE;
            }
        }

        return array;
    }

    public int size(final int depth) {
        if (depth < trimSizes.length - 1) {
            return trimSizes[depth];
        }

        return defaultSize;
    }
}
