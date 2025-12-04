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

package stroom.widget.htree.client.treelayout;

import java.util.List;

/**
 * Represents a tree to be used by the {@link AbegoTreeLayout}.
 * <p>
 * The TreeForTreeLayout interface is designed to best match the implemented
 * layout algorithm and to ensure the algorithm's time complexity promises in
 * all possible cases. However in most situation a client must not deal with all
 * details of this interface and can directly use the
 * {@link stroom.widget.htree.client.treelayout.util.AbstractTreeForTreeLayout}
 * to implement this interface or even use the
 * {@link stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout}
 * class directly.
 * <p>
 * Also see <a href="package-summary.html">this overview</a>.
 */
public interface TreeForTreeLayout<T_TREE_NODE> {

    /**
     * Returns the the root of the tree.
     * <p>
     * Time Complexity: O(1)
     *
     * @return the root of the tree
     */
    T_TREE_NODE getRoot();

    /**
     * Tells if a node is a leaf in the tree.
     * <p>
     * Time Complexity: O(1)
     *
     * @param node
     * @return true iff node is a leaf in the tree, i.e. has no children.
     */
    boolean isLeaf(T_TREE_NODE node);

    /**
     * Tells if a node is a child of a given parentNode.
     * <p>
     * Time Complexity: O(1)
     *
     * @param node
     * @param parentNode
     * @return true iff the node is a child of the given parentNode
     */
    boolean isChildOfParent(T_TREE_NODE node, T_TREE_NODE parentNode);

    /**
     * Returns the children of a parent node.
     * <p>
     * Time Complexity: O(1)
     *
     * @param parentNode [!isLeaf(parentNode)]
     * @return the children of the given parentNode, from first to last
     */
    List<T_TREE_NODE> getChildren(T_TREE_NODE parentNode);

    /**
     * Returns the children of a parent node, in reverse order.
     * <p>
     * Time Complexity: O(1)
     *
     * @param parentNode [!isLeaf(parentNode)]
     * @return the children of given parentNode, from last to first
     */
    Iterable<T_TREE_NODE> getChildrenReverse(T_TREE_NODE parentNode);

    /**
     * Returns the first child of a parent node.
     * <p>
     * Time Complexity: O(1)
     *
     * @param parentNode [!isLeaf(parentNode)]
     * @return the first child of the parentNode
     */
    T_TREE_NODE getFirstChild(T_TREE_NODE parentNode);

    /**
     * Returns the last child of a parent node.
     * <p>
     * <p>
     * Time Complexity: O(1)
     *
     * @param parentNode [!isLeaf(parentNode)]
     * @return the last child of the parentNode
     */
    T_TREE_NODE getLastChild(T_TREE_NODE parentNode);
}
