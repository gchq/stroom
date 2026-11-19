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

package stroom.widget.htree.client;

import stroom.widget.htree.client.treelayout.TreeLayout;

public interface ConnectorRenderer<T> {

    void render(TreeLayout<T> treeLayout, double x1, double y1, double x2, double y2, boolean firstChild,
                boolean lastChild);

    /// Draws the connector from a parent to one of its children, e.g. to colour it by what flows
    /// along it. By default the items are ignored.
    ///
    /// @param treeLayout The tree's layout.
    /// @param parent     The item the connector comes from.
    /// @param child      The item the connector goes to.
    /// @param x1         Where the connector starts, at the parent's right.
    /// @param y1         Where the connector starts, at the parent's middle.
    /// @param x2         Where the connector ends, at the child's left.
    /// @param y2         Where the connector ends, at the child's middle.
    /// @param firstChild Whether the child is the parent's first.
    /// @param lastChild  Whether the child is the parent's last.
    default void render(final TreeLayout<T> treeLayout,
                        final T parent,
                        final T child,
                        final double x1,
                        final double y1,
                        final double x2,
                        final double y2,
                        final boolean firstChild,
                        final boolean lastChild) {
        render(treeLayout, x1, y1, x2, y2, firstChild, lastChild);
    }
}
