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

package stroom.explorer.impl;

public class ExplorerTreePath {

    private final Integer ancestor;
    private final Integer descendant;
    private final int depth;
    private int orderIndex;

    public ExplorerTreePath(final Integer ancestor, final Integer descendant, final int depth, final int orderIndex) {
        this.ancestor = ancestor;
        this.descendant = descendant;
        this.depth = depth;
        this.orderIndex = orderIndex;
    }

    public Integer getAncestor() {
        return ancestor;
    }

    public Integer getDescendant() {
        return descendant;
    }

    public int getDepth() {
        return depth;
    }

    public int getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(final int orderIndex) {
        this.orderIndex = orderIndex;
    }
}
