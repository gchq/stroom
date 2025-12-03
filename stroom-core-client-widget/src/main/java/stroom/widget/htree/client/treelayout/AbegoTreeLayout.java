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

import stroom.widget.htree.client.treelayout.Configuration.AlignmentInLevel;
import stroom.widget.htree.client.treelayout.Configuration.Location;
import stroom.widget.htree.client.treelayout.internal.util.Contract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Implements the actual tree layout algorithm.
 * <p>
 * The nodes with their final layout can be retrieved through
 * {@link #getNodeBounds()}.
 * <p>
 * See <a href="package-summary.html">this summary</a> to get an overview how to
 * use TreeLayout.
 */
@SuppressWarnings("checkstyle:all") // 3rd part code
public class AbegoTreeLayout<TreeNode> implements TreeLayout<TreeNode> {
    /*
     * Differences between this implementation and original algorithm
     * --------------------------------------------------------------
     *
     * For easier reference the same names (or at least similar names) as in the
     * paper of Buchheim, J&uuml;nger, and Leipert are used in this
     * implementation. However in the external interface "first" and "last" are
     * used instead of "left most" and "right most". The implementation also
     * supports tree layouts with the root at the left (or right) side. In that
     * case using "left most" would refer to the "top" child, i.e. using "first"
     * is less confusing.
     *
     * Also the y coordinate is not the level but directly refers the y
     * coordinate of a level, taking node's height and gapBetweenLevels into
     * account. When the root is at the left or right side the y coordinate
     * actually becomes an x coordinate.
     *
     * Instead of just using a constant "distance" to calculate the position to
     * the next node we refer to the "size" (width or height) of the node and a
     * "gapBetweenNodes".
     */

    private final NodeExtentProvider<TreeNode> nodeExtentProvider;
    private final Configuration<TreeNode> configuration;
    private TreeForTreeLayout<TreeNode> tree;
    private double boundsLeft = Double.MAX_VALUE;
    private double boundsRight = Double.MIN_VALUE;
    private double boundsTop = Double.MAX_VALUE;
    private double boundsBottom = Double.MIN_VALUE;

    private Map<TreeNode, Double> mod;
    private Map<TreeNode, TreeNode> thread;
    private Map<TreeNode, Double> prelim;
    private Map<TreeNode, Double> change;
    private Map<TreeNode, Double> shift;
    private Map<TreeNode, TreeNode> ancestor;
    private Map<TreeNode, Integer> number;
    private Map<TreeNode, Point> positions;

    private Map<TreeNode, Bounds> nodeBounds;
    private List<Double> sizeOfLevel = new ArrayList<>();

    /**
     * Creates a TreeLayout for a given tree.
     * <p>
     * In addition to the tree the {@link NodeExtentProvider} and the
     * {@link Configuration} must be given.
     */
    public AbegoTreeLayout(final NodeExtentProvider<TreeNode> nodeExtentProvider,
                           final Configuration<TreeNode> configuration) {
        this.nodeExtentProvider = nodeExtentProvider;
        this.configuration = configuration;
    }

    @Override
    public void layout() {
        boundsLeft = Double.MAX_VALUE;
        boundsRight = Double.MIN_VALUE;
        boundsTop = Double.MAX_VALUE;
        boundsBottom = Double.MIN_VALUE;

        mod = new HashMap<>();
        thread = new HashMap<>();
        prelim = new HashMap<>();
        change = new HashMap<>();
        shift = new HashMap<>();
        ancestor = new HashMap<>();
        number = new HashMap<>();
        positions = new HashMap<>();

        nodeBounds = null;
        sizeOfLevel = new ArrayList<>();

        // No need to explicitly set mod, thread and ancestor as their getters
        // are taking care of the initial values. This avoids a full tree walk
        // through and saves some memory as no entries are added for
        // "initial values".

        if (tree != null) {
            final TreeNode r = tree.getRoot();
            firstWalk(r, null);
            calcSizeOfLevels(r, 0);
            secondWalk(r, -getPrelim(r), 0, 0);
        }
    }

    /**
     * Returns the Tree the layout is created for.
     */
    @Override
    public TreeForTreeLayout<TreeNode> getTree() {
        return tree;
    }

    @Override
    public void setTree(final TreeForTreeLayout<TreeNode> tree) {
        this.tree = tree;
    }

    /**
     * Returns the {@link NodeExtentProvider} used by this
     * {@link AbegoTreeLayout}.
     */
    public NodeExtentProvider<TreeNode> getNodeExtentProvider() {
        return nodeExtentProvider;
    }

    private double getNodeHeight(final TreeNode node) {
        return nodeExtentProvider.getExtents(node).getHeight();
    }

    private double getNodeWidth(final TreeNode node) {
        return nodeExtentProvider.getExtents(node).getWidth();
    }

    private double getWidthOrHeightOfNode(final TreeNode treeNode, final boolean returnWidth) {
        return returnWidth
                ? getNodeWidth(treeNode)
                : getNodeHeight(treeNode);
    }

    /**
     * When the level changes in Y-axis (i.e. root location Top or Bottom) the
     * height of a node is its thickness, otherwise the node's width is its
     * thickness.
     * <p>
     * The thickness of a node is used when calculating the locations of the
     * levels.
     *
     * @param treeNode
     * @return
     */
    private double getNodeThickness(final TreeNode treeNode) {
        return getWidthOrHeightOfNode(treeNode, !isLevelChangeInYAxis());
    }

    /**
     * When the level changes in Y-axis (i.e. root location Top or Bottom) the
     * width of a node is its size, otherwise the node's height is its size.
     * <p>
     * The size of a node is used when calculating the distance between two
     * nodes.
     *
     * @param treeNode
     * @return
     */
    private double getNodeSize(final TreeNode treeNode) {
        return getWidthOrHeightOfNode(treeNode, isLevelChangeInYAxis());
    }

    /**
     * Returns the Configuration used by this {@link AbegoTreeLayout}.
     */
    public Configuration<TreeNode> getConfiguration() {
        return configuration;
    }

    private boolean isLevelChangeInYAxis() {
        final Location rootLocation = configuration.getRootLocation();
        return rootLocation == Location.Top || rootLocation == Location.Bottom;
    }

    private int getLevelChangeSign() {
        final Location rootLocation = configuration.getRootLocation();
        return rootLocation == Location.Bottom || rootLocation == Location.Right
                ? -1
                : 1;
    }

    private void updateBounds(final TreeNode node, final double centerX, final double centerY) {
        final Dimension dimension = nodeExtentProvider.getExtents(node);
        final double width = dimension.getWidth();
        final double height = dimension.getHeight();
        final double left = centerX - width / 2;
        final double right = centerX + width / 2;
        final double top = centerY - height / 2;
        final double bottom = centerY + height / 2;
        if (boundsLeft > left) {
            boundsLeft = left;
        }
        if (boundsRight < right) {
            boundsRight = right;
        }
        if (boundsTop > top) {
            boundsTop = top;
        }
        if (boundsBottom < bottom) {
            boundsBottom = bottom;
        }
    }

    @Override
    public Bounds getBounds() {
        return new Bounds(0, 0, boundsRight - boundsLeft, boundsBottom - boundsTop);
    }

    private void calcSizeOfLevels(final TreeNode node, final int level) {
        final double oldSize;
        if (sizeOfLevel.size() <= level) {
            sizeOfLevel.add(Double.valueOf(0));
            oldSize = 0;
        } else {
            oldSize = sizeOfLevel.get(level);
        }

        final double size = getNodeThickness(node);
        if (oldSize < size) {
            sizeOfLevel.set(level, size);
        }

        if (!tree.isLeaf(node)) {
            for (final TreeNode child : tree.getChildren(node)) {
                calcSizeOfLevels(child, level + 1);
            }
        }
    }

    /**
     * Returns the number of levels of the tree.
     *
     * @return [level > 0]
     */
    public int getLevelCount() {
        return sizeOfLevel.size();
    }

    /**
     * Returns the size of a level.
     * <p>
     * When the root is located at the top or bottom the size of a level is the
     * maximal height of the nodes of that level. When the root is located at
     * the left or right the size of a level is the maximal width of the nodes
     * of that level.
     *
     * @param level
     * @return the size of the level [level >= 0 && level < levelCount]
     */
    public double getSizeOfLevel(final int level) {
        Contract.checkArg(level >= 0, "level must be >= 0");
        Contract.checkArg(level < getLevelCount(), "level must be < levelCount");

        return sizeOfLevel.get(level);
    }

    // ------------------------------------------------------------------------
    // NormalizedPosition

    private double getMod(final TreeNode node) {
        final Double d = mod.get(node);
        return d != null
                ? d.doubleValue()
                : 0;
    }

    // ------------------------------------------------------------------------
    // The Algorithm

    private void setMod(final TreeNode node, final double d) {
        mod.put(node, d);
    }

    private TreeNode getThread(final TreeNode node) {
        final TreeNode n = thread.get(node);
        return n;
    }

    private void setThread(final TreeNode node, final TreeNode thread) {
        this.thread.put(node, thread);
    }

    private TreeNode getAncestor(final TreeNode node) {
        final TreeNode n = ancestor.get(node);
        return n != null
                ? n
                : node;
    }

    private void setAncestor(final TreeNode node, final TreeNode ancestor) {
        this.ancestor.put(node, ancestor);
    }

    private double getPrelim(final TreeNode node) {
        final Double d = prelim.get(node);
        return d != null
                ? d.doubleValue()
                : 0;
    }

    private void setPrelim(final TreeNode node, final double d) {
        prelim.put(node, d);
    }

    private double getChange(final TreeNode node) {
        final Double d = change.get(node);
        return d != null
                ? d.doubleValue()
                : 0;
    }

    private void setChange(final TreeNode node, final double d) {
        change.put(node, d);
    }

    private double getShift(final TreeNode node) {
        final Double d = shift.get(node);
        return d != null
                ? d.doubleValue()
                : 0;
    }

    private void setShift(final TreeNode node, final double d) {
        shift.put(node, d);
    }

    /**
     * The distance of two nodes is the distance of the centers of both noded.
     * <p>
     * I.e. the distance includes the gap between the nodes and half of the
     * sizes of the nodes.
     *
     * @param v
     * @param w
     * @return the distance between node v and w
     */
    private double getDistance(final TreeNode v, final TreeNode w) {
        final double sizeOfNodes = getNodeSize(v) + getNodeSize(w);

        final double distance = sizeOfNodes / 2 + configuration.getGapBetweenNodes(v, w);
        return distance;
    }

    private TreeNode nextLeft(final TreeNode v) {
        return tree.isLeaf(v)
                ? getThread(v)
                : tree.getFirstChild(v);
    }

    private TreeNode nextRight(final TreeNode v) {
        return tree.isLeaf(v)
                ? getThread(v)
                : tree.getLastChild(v);
    }

    /**
     * @param node       [tree.isChildOfParent(node, parentNode)]
     * @param parentNode parent of node
     * @return
     */
    private int getNumber(final TreeNode node, final TreeNode parentNode) {
        Integer n = number.get(node);
        if (n == null) {
            int i = 1;
            for (final TreeNode child : tree.getChildren(parentNode)) {
                number.put(child, i++);
            }
            n = number.get(node);
        }

        return n.intValue();
    }

    /**
     * @param vIMinus
     * @param v
     * @param parentOfV
     * @param defaultAncestor
     * @return the greatest distinct ancestor of vIMinus and its right neighbor v
     */
    private TreeNode ancestor(final TreeNode vIMinus, final TreeNode v, final TreeNode parentOfV,
                              final TreeNode defaultAncestor) {
        final TreeNode ancestor = getAncestor(vIMinus);

        // when the ancestor of vIMinus is a sibling of v (i.e. has the same
        // parent as v) it is also the greatest distinct ancestor vIMinus and
        // v. Otherwise it is the defaultAncestor
        return tree.isChildOfParent(ancestor, parentOfV)
                ? ancestor
                : defaultAncestor;
    }

    private void moveSubtree(final TreeNode wMinus, final TreeNode wPlus, final TreeNode parent, final double shift) {
        final int subtrees = getNumber(wPlus, parent) - getNumber(wMinus, parent);
        setChange(wPlus, getChange(wPlus) - shift / subtrees);
        setShift(wPlus, getShift(wPlus) + shift);
        setChange(wMinus, getChange(wMinus) + shift / subtrees);
        setPrelim(wPlus, getPrelim(wPlus) + shift);
        setMod(wPlus, getMod(wPlus) + shift);
    }

    /**
     * In difference to the original algorithm we also pass in the leftSibling
     * and the parent of v.
     * <p>
     * <b>Why adding the parameter 'parent of v' (parentOfV) ?</b>
     * <p>
     * In this method we need access to the parent of v. Not every tree
     * implementation may support efficient (i.e. constant time) access to it.
     * On the other hand the (only) caller of this method can provide this
     * information with only constant extra time.
     * <p>
     * Also we need access to the "left most sibling" of v. Not every tree
     * implementation may support efficient (i.e. constant time) access to it.
     * On the other hand the "left most sibling" of v is also the "first child"
     * of the parent of v. The first child of a parent node we can get in
     * constant time. As we got the parent of v we can so also get the
     * "left most sibling" of v in constant time.
     * <p>
     * <b>Why adding the parameter 'leftSibling' ?</b>
     * <p>
     * In this method we need access to the "left sibling" of v. Not every tree
     * implementation may support efficient (i.e. constant time) access to it.
     * However it is easy for the caller of this method to provide this
     * information with only constant extra time.
     * <p>
     * <p>
     * <p>
     * In addition these extra parameters avoid the need for
     * {@link TreeForTreeLayout} to include extra methods "getParent",
     * "getLeftSibling", or "getLeftMostSibling". This keeps the interface
     * {@link TreeForTreeLayout} small and avoids redundant implementations.
     *
     * @param v
     * @param defaultAncestor
     * @param leftSibling     [nullable] the left sibling v, if there is any
     * @param parentOfV       the parent of v
     * @return the (possibly changes) defaultAncestor
     */
    private TreeNode apportion(final TreeNode v, TreeNode defaultAncestor, final TreeNode leftSibling,
                               final TreeNode parentOfV) {
        final TreeNode w = leftSibling;
        if (w == null) {
            // v has no left sibling
            return defaultAncestor;
        }
        // v has left sibling w

        // The following variables "v..." are used to traverse the contours to
        // the subtrees. "Minus" refers to the left, "Plus" to the right
        // subtree. "I" refers to the "inside" and "O" to the outside contour.
        TreeNode vOPlus = v;
        TreeNode vIPlus = v;
        TreeNode vIMinus = w;
        // get leftmost sibling of vIPlus, i.e. get the leftmost sibling of
        // v, i.e. the leftmost child of the parent of v (which is passed
        // in)
        TreeNode vOMinus = tree.getFirstChild(parentOfV);

        Double sIPlus = getMod(vIPlus);
        Double sOPlus = getMod(vOPlus);
        Double sIMinus = getMod(vIMinus);
        Double sOMinus = getMod(vOMinus);

        TreeNode nextRightVIMinus = nextRight(vIMinus);
        TreeNode nextLeftVIPlus = nextLeft(vIPlus);

        while (nextRightVIMinus != null && nextLeftVIPlus != null) {
            vIMinus = nextRightVIMinus;
            vIPlus = nextLeftVIPlus;
            vOMinus = nextLeft(vOMinus);
            vOPlus = nextRight(vOPlus);
            setAncestor(vOPlus, v);
            final double shift = (getPrelim(vIMinus) + sIMinus) - (getPrelim(vIPlus) + sIPlus)
                    + getDistance(vIMinus, vIPlus);

            if (shift > 0) {
                moveSubtree(ancestor(vIMinus, v, parentOfV, defaultAncestor), v, parentOfV, shift);
                sIPlus = sIPlus + shift;
                sOPlus = sOPlus + shift;
            }
            sIMinus = sIMinus + getMod(vIMinus);
            sIPlus = sIPlus + getMod(vIPlus);
            sOMinus = sOMinus + getMod(vOMinus);
            sOPlus = sOPlus + getMod(vOPlus);

            nextRightVIMinus = nextRight(vIMinus);
            nextLeftVIPlus = nextLeft(vIPlus);
        }

        if (nextRightVIMinus != null && nextRight(vOPlus) == null) {
            setThread(vOPlus, nextRightVIMinus);
            setMod(vOPlus, getMod(vOPlus) + sIMinus - sOPlus);
        }

        if (nextLeftVIPlus != null && nextLeft(vOMinus) == null) {
            setThread(vOMinus, nextLeftVIPlus);
            setMod(vOMinus, getMod(vOMinus) + sIPlus - sOMinus);
            defaultAncestor = v;
        }
        return defaultAncestor;
    }

    /**
     * @param v [!tree.isLeaf(v)]
     */
    private void executeShifts(final TreeNode v) {
        double shift = 0;
        double change = 0;
        for (final TreeNode w : tree.getChildrenReverse(v)) {
            change = change + getChange(w);
            setPrelim(w, getPrelim(w) + shift);
            setMod(w, getMod(w) + shift);
            shift = shift + getShift(w) + change;
        }
    }

    /**
     * In difference to the original algorithm we also pass in the leftSibling
     * (see {@link #apportion(Object, Object, Object, Object)} for a
     * motivation).
     *
     * @param v
     * @param leftSibling [nullable] the left sibling v, if there is any
     */
    private void firstWalk(final TreeNode v, final TreeNode leftSibling) {
        if (tree.isLeaf(v)) {
            // No need to set prelim(v) to 0 as the getter takes care of this.

            final TreeNode w = leftSibling;
            if (w != null) {
                // v has left sibling

                setPrelim(v, getPrelim(w) + getDistance(v, w));
            }

        } else {
            // v is not a leaf

            TreeNode defaultAncestor = tree.getFirstChild(v);
            TreeNode previousChild = null;
            for (final TreeNode w : tree.getChildren(v)) {
                firstWalk(w, previousChild);
                defaultAncestor = apportion(w, defaultAncestor, previousChild, v);
                previousChild = w;
            }
            executeShifts(v);
            final double midpoint = (getPrelim(tree.getFirstChild(v)) + getPrelim(tree.getLastChild(v))) / 2.0;
            final TreeNode w = leftSibling;
            if (w != null) {
                // v has left sibling

                setPrelim(v, getPrelim(w) + getDistance(v, w));
                setMod(v, getPrelim(v) - midpoint);

            } else {
                // v has no left sibling

                setPrelim(v, midpoint);
            }
        }
    }

    /**
     * In difference to the original algorithm we also pass in extra level
     * information.
     */
    private void secondWalk(final TreeNode v, final double m, final int level, final double levelStart) {
        // construct the position from the prelim and the level information

        // The rootLocation affects the way how x and y are changed and in what
        // direction.
        final double levelChangeSign = getLevelChangeSign();
        final boolean levelChangeOnYAxis = isLevelChangeInYAxis();
        final double levelSize = getSizeOfLevel(level);

        double x = getPrelim(v) + m;

        double y;
        final AlignmentInLevel alignment = configuration.getAlignmentInLevel();
        if (alignment == AlignmentInLevel.Center) {
            y = levelStart + levelChangeSign * (levelSize / 2);
        } else if (alignment == AlignmentInLevel.TowardsRoot) {
            y = levelStart + levelChangeSign * (getNodeThickness(v) / 2);
        } else {
            y = levelStart + levelSize - levelChangeSign * (getNodeThickness(v) / 2);
        }

        if (!levelChangeOnYAxis) {
            final double t = x;
            x = y;
            y = t;
        }

        positions.put(v, new NormalizedPosition(x, y));

        // update the bounds
        updateBounds(v, x, y);

        // recurse
        if (!tree.isLeaf(v)) {
            final double nextLevelStart = levelStart
                    + (levelSize + configuration.getGapBetweenLevels(level + 1)) * levelChangeSign;
            for (final TreeNode w : tree.getChildren(v)) {
                secondWalk(w, m + getMod(v), level + 1, nextLevelStart);
            }
        }
    }

    @Override
    public Map<TreeNode, Bounds> getNodeBounds() {
        if (nodeBounds == null) {
            nodeBounds = new HashMap<>();
            for (final Entry<TreeNode, Point> entry : positions.entrySet()) {
                final TreeNode node = entry.getKey();
                final Point pos = entry.getValue();
                final Dimension dimension = nodeExtentProvider.getExtents(node);
                final double w = dimension.getWidth();
                final double h = dimension.getHeight();
                final double x = pos.getX() - w / 2;
                final double y = pos.getY() - h / 2;
                nodeBounds.put(node, new Bounds(x, y, w, h));
            }
        }
        return nodeBounds;
    }

    private void addUniqueNodes(final Map<TreeNode, TreeNode> nodes, final TreeNode newNode) {
        if (nodes.put(newNode, newNode) != null) {
            throw new RuntimeException("Node used more than once in tree: " + newNode);
        }
        for (final TreeNode n : tree.getChildren(newNode)) {
            addUniqueNodes(nodes, n);
        }
    }

    // ------------------------------------------------------------------------
    // checkTree

    /**
     * The algorithm calculates the position starting with the root at 0. I.e.
     * the left children will get negative positions. However we want the result
     * to be normalized to (0,0).
     * <p>
     * {@link NormalizedPosition} will normalize the position (given relative to
     * the root position), taking the current bounds into account. This way the
     * left most node bounds will start at x = 0, the top most node bounds at y
     * = 0.
     */
    private class NormalizedPosition extends Point {

        public NormalizedPosition(final double x_relativeToRoot, final double y_relativeToRoot) {
            super(x_relativeToRoot, y_relativeToRoot);
        }

        @Override
        public double getX() {
            return super.getX() - boundsLeft;
        }

        @Override
        public double getY() {
            return super.getY() - boundsTop;
        }
    }
}
