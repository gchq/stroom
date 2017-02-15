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

public class TrimSettings {
    private final Integer[] trimSizes;
    private final int defaultSize;

    public TrimSettings(final Integer[] storeTrimSizes, final Integer[] defaultStoreTrimSizes) {
        trimSizes = getStoreTrimSizes(storeTrimSizes, defaultStoreTrimSizes);

        if (trimSizes.length > 0) {
            defaultSize = trimSizes[trimSizes.length - 1];
        } else {
            defaultSize = Integer.MAX_VALUE;
        }
    }

    private Integer[] getStoreTrimSizes(final Integer[] storeTrimSizes, final Integer[] defaultStoreTrimSizes) {
        int size = 0;
        if (storeTrimSizes != null) {
            size = storeTrimSizes.length;
        }
        if (defaultStoreTrimSizes != null && defaultStoreTrimSizes.length > size) {
            size = defaultStoreTrimSizes.length;
        }

        final Integer[] array = new Integer[size];
        for (int i = 0; i < size; i++) {
            if (defaultStoreTrimSizes != null && i < defaultStoreTrimSizes.length) {
                array[i] = defaultStoreTrimSizes[i];
            }

            if (storeTrimSizes != null && i < storeTrimSizes.length) {
                if (array[i] == null || (storeTrimSizes[i] != null && array[i] < storeTrimSizes[i])) {
                    array[i] = storeTrimSizes[i];
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
