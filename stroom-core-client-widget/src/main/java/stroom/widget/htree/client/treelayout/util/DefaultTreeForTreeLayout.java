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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static stroom.widget.htree.client.treelayout.internal.util.Contract.checkArg;

/**
 * Provides a generic implementation for the {@link TreeForTreeLayout}
 * interface, applicable to any type of tree node.
 * <p>
 * It allows you to create a tree "from scratch", without creating any new
 * class.
 * <p>
 * To create a tree you must provide the root of the tree (see
 * {@link #DefaultTreeForTreeLayout(Object)}. Then you can incrementally
 * construct the tree by adding children to the root or other nodes of the tree
 * (see {@link #addChild(Object, Object)} and
 * {@link #addChildren(Object, Object...)}).
 */
public class DefaultTreeForTreeLayout<TreeNode> extends AbstractTreeForTreeLayout<TreeNode> {
    private List<TreeNode> emptyList;

    private List<TreeNode> getEmptyList() {
        if (emptyList == null) {
            emptyList = new ArrayList<>();
        }
        return emptyList;
    }

    private final Map<TreeNode, List<TreeNode>> childrenMap = new HashMap<>();
    private final Map<TreeNode, TreeNode> parents = new HashMap<>();

    /**
     * Creates a new instance with a given node as the root
     *
     * @param root
     *            the node to be used as the root.
     */
    public DefaultTreeForTreeLayout(final TreeNode root) {
        super(root);
    }

    @Override
    public TreeNode getParent(final TreeNode node) {
        return parents.get(node);
    }

    @Override
    public List<TreeNode> getChildrenList(final TreeNode node) {
        final List<TreeNode> result = childrenMap.get(node);
        return result == null ? getEmptyList() : result;
    }

    /**
     * @param node
     * @return true iff the node is in the tree
     */
    public boolean hasNode(final TreeNode node) {
        return node == getRoot() || parents.containsKey(node);
    }

    /**
     * @param parentNode
     *            [hasNode(parentNode)]
     * @param node
     *            [!hasNode(node)]
     */
    public void addChild(final TreeNode parentNode, final TreeNode node) {
        checkArg(hasNode(parentNode), "parentNode is not in the tree");
        checkArg(!hasNode(node), "node is already in the tree");

        List<TreeNode> list = childrenMap.get(parentNode);
        if (list == null) {
            list = new ArrayList<>();
            childrenMap.put(parentNode, list);
        }
        list.add(node);
        parents.put(node, parentNode);
    }

    public void removeChild(final TreeNode node) {
        final TreeNode parent = parents.remove(node);
        if (parent != null) {
            final List<TreeNode> children = getChildrenList(parent);
            if (children != null) {
                children.remove(node);
            }
        }
    }
}
