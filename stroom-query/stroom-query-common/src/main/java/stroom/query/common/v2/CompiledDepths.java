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

package stroom.query.common.v2;

import java.util.Arrays;

public class CompiledDepths {

    private final int maxGroupDepth;
    private final int maxDepth;
    private final int[] groupSizeByDepth;
    private final boolean[][] groupIndicesByDepth;
    private final boolean[][] valueIndicesByDepth;

    CompiledDepths(final CompiledColumn[] compiledColumns,
                   final boolean showDetail) {
        int maxGroupDepth = -1;
        int maxDepth = -1;
        int length = 0;

        if (compiledColumns == null) {
            groupSizeByDepth = new int[length];
            groupIndicesByDepth = new boolean[length][];
            valueIndicesByDepth = new boolean[length][];
        } else {
            // Get the max group depth.
            for (final CompiledColumn column : compiledColumns) {
                maxGroupDepth = Math.max(maxGroupDepth, column.getGroupDepth());
            }

            // If we are showing details below grouped levels then add one to the max depth,
            // i.e. the max depth will be 1 greater then the maxGroupDepth.
            //
            // Likewise if there are no groups, i.e. the maxGroupDepth == -1,
            // then act as if show detail were true and ensure the maxDepth is 0.
            if (showDetail || maxGroupDepth < 0) {
                maxDepth = maxGroupDepth + 1;
            } else {
                final boolean requireChildren = Arrays
                        .stream(compiledColumns)
                        .anyMatch(CompiledColumn::requiresChildData);
                if (requireChildren) {
                    maxDepth = maxGroupDepth + 1;
                } else {
                    maxDepth = maxGroupDepth;
                }
            }

            length = maxDepth + 1;
            groupSizeByDepth = new int[length];
            groupIndicesByDepth = new boolean[length][];
            valueIndicesByDepth = new boolean[length][];

            for (int depth = 0; depth <= maxDepth; depth++) {
                final boolean[] valueIndices = new boolean[compiledColumns.length];
                final boolean[] groupIndices = new boolean[compiledColumns.length];

                for (int i = 0; i < compiledColumns.length; i++) {
                    final CompiledColumn column = compiledColumns[i];

                    // Add a flag for each field index included in this group depth.
                    if (column.getGroupDepth() == depth) {
                        groupIndices[i] = true;
                        groupSizeByDepth[depth] = groupSizeByDepth[depth] + 1;
                        valueIndices[i] = true;
                    } else if (column.getGroupDepth() != -1 && column.getGroupDepth() < depth) {
                        valueIndices[i] = true;
                    } else if (depth > maxGroupDepth) {
                        valueIndices[i] = true;
                    } else {
                        if (column.getGenerator() != null) {
                            if (column.hasAggregate()) {
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

    public int getMaxGroupDepth() {
        return maxGroupDepth;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    @Override
    public String toString() {
        return "CompiledDepths{" +
                "maxGroupDepth=" + maxGroupDepth +
                ", maxDepth=" + maxDepth +
                ", groupSizeByDepth=" + Arrays.toString(groupSizeByDepth) +
                ", groupIndicesByDepth=" + Arrays.deepToString(groupIndicesByDepth) +
                ", valueIndicesByDepth=" + Arrays.deepToString(valueIndicesByDepth) +
                '}';
    }
}
