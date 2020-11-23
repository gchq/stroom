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

import java.util.Arrays;

class CompiledDepths {
    private final int maxGroupDepth;
    private final int maxDepth;
    private final int[] groupSizeByDepth;
    private final boolean[][] groupIndicesByDepth;
    private final boolean[][] valueIndicesByDepth;

    CompiledDepths(final CompiledField[] compiledFields, final boolean showDetail) {
        int maxGroupDepth = -1;
        int maxDepth = 0;

        if (compiledFields == null) {
            groupSizeByDepth = new int[0];
            groupIndicesByDepth = new boolean[0][];
            valueIndicesByDepth = new boolean[0][];
        } else {
            // Get the max group depth.
            for (final CompiledField field : compiledFields) {
                maxGroupDepth = Math.max(maxGroupDepth, field.getGroupDepth());
            }

            if (maxGroupDepth >= 0) {
                maxDepth = maxGroupDepth + 1;
            }
            if (showDetail || maxDepth == 0) {
                maxDepth++;
            }

            groupSizeByDepth = new int[maxDepth];
            groupIndicesByDepth = new boolean[maxDepth][];
            valueIndicesByDepth = new boolean[maxDepth][];

            for (int depth = 0; depth < maxDepth; depth++) {
                final boolean[] valueIndices = new boolean[compiledFields.length];
                final boolean[] groupIndices = new boolean[compiledFields.length];

                for (int i = 0; i < compiledFields.length; i++) {
                    final CompiledField field = compiledFields[i];

                    // Add a flag for each field index included in this group depth.
                    if (field.getGroupDepth() == depth) {
                        groupIndices[i] = true;
                        groupSizeByDepth[depth] = groupSizeByDepth[depth] + 1;
                        valueIndices[i] = true;
                    } else if (field.getGroupDepth() != -1 && field.getGroupDepth() < depth) {
                        valueIndices[i] = true;
                    } else if (depth > maxGroupDepth) {
                        valueIndices[i] = true;
                    } else {
                        if (field.getExpression() != null) {
                            if (field.getExpression().hasAggregate()) {
                                valueIndices[i] = true;
                            }
                        }
                    }
                }

                valueIndicesByDepth[depth] = valueIndices;
                groupIndicesByDepth[depth] = groupIndices;
            }
        }

        this.maxGroupDepth = maxGroupDepth;
        this.maxDepth = maxDepth;
    }

    public boolean hasGroup() {
        return maxGroupDepth != -1;
    }

    public int[] getGroupSizeByDepth() {
        return groupSizeByDepth;
    }

    public boolean[][] getGroupIndicesByDepth() {
        return groupIndicesByDepth;
    }

    public boolean[][] getValueIndicesByDepth() {
        return valueIndicesByDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    public String toString() {
        return "CompiledDepths{" +
                "maxGroupDepth=" + maxGroupDepth +
                ", levels=" + maxDepth +
                ", fieldIndicesByDepth=" + Arrays.toString(groupIndicesByDepth) +
                '}';
    }
}
