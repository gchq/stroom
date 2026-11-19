/*
 * Copyright 2024 Crown Copyright
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

package stroom.query.client.result.vis;

import stroom.query.api.ColumnRef;
import stroom.query.client.result.table.ComponentSelection;
import stroom.query.client.result.table.HasComponentSelection;
import stroom.util.shared.NullSafe;

import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class VisSelectionModel
        implements HasComponentSelection, HasSelectionHandlers<List<ComponentSelection>> {

    private static final List<ColumnRef> COLUMNS = new ArrayList<>();

    static {
        COLUMNS.add(new ColumnRef("name", "name"));
        COLUMNS.add(new ColumnRef("value", "value"));
    }

    private final EventBus eventBus = new SimpleEventBus();
    private final Timer timer;
    private List<ComponentSelection> currentSelection;

    public VisSelectionModel() {
        timer = new Timer() {
            @Override
            public void run() {
                SelectionEvent.fire(VisSelectionModel.this, currentSelection);
            }
        };
    }

    @Override
    public List<ColumnRef> getColumnRefs() {
        return COLUMNS;
    }

    /// Takes what is selected in the visualisation (its frame's selection callback), telling the
    /// selection handlers when it changes.
    ///
    /// @param selection What is selected.
    public void onSelection(final List<ComponentSelection> selection) {
        if (!Objects.equals(currentSelection, selection)) {
            currentSelection = selection;
            timer.schedule(250);
        }
    }

    @Override
    public List<ComponentSelection> getSelection() {
        return NullSafe.list(currentSelection);
    }

    @Override
    public Set<String> getHighlights() {
        return Collections.emptySet();
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<List<ComponentSelection>> handler) {
        return eventBus.addHandler(SelectionEvent.getType(), handler);
    }

    @Override
    public void fireEvent(final GwtEvent<?> event) {
        eventBus.fireEvent(event);
    }
}
