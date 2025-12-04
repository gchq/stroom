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

/*
 * [The "BSD license"]
 * Copyright (c) 2011, abego Software GmbH, Germany (http://www.abego.org)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 * 3. Neither the name of the abego Software GmbH nor the names of its
 *    contributors may be used to endorse or promote products derived from this
 *    software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package stroom.widget.htree.client.treelayout.util;

import stroom.widget.htree.client.treelayout.TreeForTreeLayout;
import stroom.widget.htree.client.treelayout.internal.util.IterableUtil;
import stroom.widget.htree.client.treelayout.internal.util.ListUtil;

import java.util.List;

/**
 * Provides an easy way to implement the {@link TreeForTreeLayout} interface by
 * defining just two simple methods and a constructor.
 * <p>
 * To use this class the underlying tree must provide the children as a list
 * (see {@link #getChildrenList(Object)} and give direct access to the parent of
 * a node (see {@link #getParent(Object)}).
 * <p>
 * <p>
 * See also {@link DefaultTreeForTreeLayout}.
 */
public abstract class AbstractTreeForTreeLayout<T_TREE_NODE> implements TreeForTreeLayout<T_TREE_NODE> {

    private final T_TREE_NODE root;

    public AbstractTreeForTreeLayout(final T_TREE_NODE root) {
        this.root = root;
    }

    /**
     * Returns the parent of a node, if it has one.
     * <p>
     * Time Complexity: O(1)
     *
     * @return [nullable] the parent of the node, or null when the node is a
     * root.
     */
    public abstract T_TREE_NODE getParent(T_TREE_NODE node);

    /**
     * Return the children of a node as a {@link List}.
     * <p>
     * Time Complexity: O(1)
     * <p>
     * Also the access to an item of the list must have time complexity O(1).
     * <p>
     * A client must not modify the returned list.
     *
     * @return the children of the given node. When node is a leaf the list is
     * empty.
     */
    public abstract List<T_TREE_NODE> getChildrenList(T_TREE_NODE node);

    @Override
    public T_TREE_NODE getRoot() {
        return root;
    }

    @Override
    public boolean isLeaf(final T_TREE_NODE node) {
        return getChildrenList(node).isEmpty();
    }

    @Override
    public boolean isChildOfParent(final T_TREE_NODE node, final T_TREE_NODE parentNode) {
        return getParent(node) == parentNode;
    }

    @Override
    public List<T_TREE_NODE> getChildren(final T_TREE_NODE node) {
        return getChildrenList(node);
    }

    @Override
    public Iterable<T_TREE_NODE> getChildrenReverse(final T_TREE_NODE node) {
        return IterableUtil.createReverseIterable(getChildrenList(node));
    }

    @Override
    public T_TREE_NODE getFirstChild(final T_TREE_NODE parentNode) {
        return getChildrenList(parentNode).get(0);
    }

    @Override
    public T_TREE_NODE getLastChild(final T_TREE_NODE parentNode) {
        return ListUtil.getLast(getChildrenList(parentNode));
    }
}
