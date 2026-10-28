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

package stroom.pipeline.structure.client.component;

import stroom.widget.htree.client.treelayout.util.DefaultTreeForTreeLayout;

import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.view.client.SelectionModel;

public abstract class TreePanel<E> extends Composite {

    // Told when a redraw is complete, e.g. to put the keyboard focus back on a box
    private Runnable afterRefresh;

    public abstract Box<E> getBox(E item);

    public abstract Box<E> getTargetBox(Event event, boolean usePosition);

    public abstract void setSelectionModel(SelectionModel<E> selectionModel);

    public abstract void refresh();

    public abstract void refresh(final RefreshCallback callback);

    public abstract DefaultTreeForTreeLayout<E> getTree();

    public abstract void setTree(DefaultTreeForTreeLayout<E> tree);

    /// Sets what to do when a redraw is complete, e.g. to put the keyboard focus back on a box
    /// (as redrawing replaces them).
    ///
    /// @param afterRefresh What to do, or null for nothing.
    public void setAfterRefresh(final Runnable afterRefresh) {
        this.afterRefresh = afterRefresh;
    }

    /// Tells what was set by [#setAfterRefresh] that a redraw is complete.
    protected void fireAfterRefresh() {
        if (afterRefresh != null) {
            afterRefresh.run();
        }
    }

    public interface RefreshCallback {

        void onRefresh();
    }
}
