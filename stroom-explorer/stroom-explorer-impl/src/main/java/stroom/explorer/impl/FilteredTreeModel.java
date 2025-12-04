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

import stroom.explorer.shared.ExplorerNode;
import stroom.explorer.shared.ExplorerNodeKey;
import stroom.util.shared.NullSafe;

public class FilteredTreeModel extends AbstractTreeModel<ExplorerNodeKey> {

    public FilteredTreeModel(final long id, final long creationTime) {
        super(id, creationTime);
    }

    @Override
    ExplorerNodeKey getNodeKey(final ExplorerNode node) {
        return NullSafe.get(node, ExplorerNode::getUniqueKey);
    }

    @Override
    public FilteredTreeModel clone() {
        final AbstractTreeModel<ExplorerNodeKey> clone = super.clone();
        return (FilteredTreeModel) clone;
    }
}
