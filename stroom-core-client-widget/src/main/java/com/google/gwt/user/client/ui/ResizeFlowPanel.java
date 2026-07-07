/*
 * Copyright 2025 Crown Copyright
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

package com.google.gwt.user.client.ui;

/**
 * A {@link FlowPanel} that participates in the GWT resize chain.
 * <p>
 * {@link DockLayoutPanel} (and its subclass {@link ThinSplitLayoutPanel})
 * only propagates {@code onResize()} to children that implement
 * {@link RequiresResize}. A plain {@link FlowPanel} does not, so any
 * {@link RequiresResize} widgets inside it (such as another
 * {@code DockLayoutPanel}) will never be notified of size changes —
 * breaking the resize chain.
 * <p>
 * This subclass implements both {@link RequiresResize} and
 * {@link ProvidesResize} so it can receive and forward resize events
 * to all children that need them.
 */
public class ResizeFlowPanel extends FlowPanel implements RequiresResize, ProvidesResize {

    @Override
    public void onResize() {
        for (final Widget child : this) {
            if (child instanceof RequiresResize) {
                ((RequiresResize) child).onResize();
            }
        }
    }
}
