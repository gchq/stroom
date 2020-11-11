/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.query.api.v2.Field;

import java.util.Arrays;
import java.util.List;

class CompiledDepths {
    private final int maxGroupDepth;
    private final int maxDepth;
    private final int[][] fieldIndicesByDepth;

    CompiledDepths(final List<Field> fields, final boolean showDetail) {
        int maxGroupDepth = -1;
        int maxDepth = 0;

        if (fields == null) {
            fieldIndicesByDepth = new int[0][];
        } else {
            // Get the max group depth.
            for (final Field field : fields) {
                if (field.getGroup() != null) {
                    maxGroupDepth = Math.max(maxGroupDepth, field.getGroup());
                }
            }

            if (maxGroupDepth >= 0) {
                maxDepth = maxGroupDepth + 1;
            }
            if (showDetail || maxDepth == 0) {
                maxDepth++;
            }

            fieldIndicesByDepth = new int[maxDepth][];
            if (maxGroupDepth != -1) {
                for (int i = 0; i < fields.size(); i++) {
                    final Field field = fields.get(i);
                    // Create compiled field.
                    if (field.getGroup() != null) {
                        final int groupDepth = field.getGroup();
                        final int[] fieldIndexes = fieldIndicesByDepth[groupDepth];
                        if (fieldIndexes == null) {
                            fieldIndicesByDepth[groupDepth] = new int[]{i};
                        } else {
                            final int[] arr = new int[fieldIndexes.length + 1];
                            System.arraycopy(fieldIndexes, 0, arr, 0, fieldIndexes.length);
                            arr[fieldIndexes.length] = i;
                            fieldIndicesByDepth[groupDepth] = arr;
                        }
                    }
                }
            }
        }

        this.maxGroupDepth = maxGroupDepth;
        this.maxDepth = maxDepth;
    }

    public boolean hasGroup() {
        return maxGroupDepth != -1;
    }

    public int[][] getFieldIndicesByDepth() {
        return fieldIndicesByDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    public String toString() {
        return "CompiledDepths{" +
                "maxGroupDepth=" + maxGroupDepth +
                ", levels=" + maxDepth +
                ", fieldIndicesByDepth=" + Arrays.toString(fieldIndicesByDepth) +
                '}';
    }
}
