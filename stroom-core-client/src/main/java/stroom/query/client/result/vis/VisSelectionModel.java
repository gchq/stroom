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
import stroom.util.client.JSONUtil;
import stroom.util.shared.NullSafe;
import stroom.visualisation.shared.VisSelectionKey;

import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.EventBus;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// What is selected in a visualisation, and the keys its selections have (see [VisSelectionKey]),
/// for other panes' selection handlers to use.
public class VisSelectionModel
        implements HasComponentSelection, HasSelectionHandlers<List<ComponentSelection>> {

    // The keys of a visualisation that declares none, as all were taken to have before keys were
    // declared
    private static final List<ColumnRef> UNDECLARED_KEYS = List.of(
            new ColumnRef("name", "name"),
            new ColumnRef("value", "value"));

    private final EventBus eventBus = new SimpleEventBus();
    private final Timer timer;
    private List<ComponentSelection> currentSelection;
    private List<ColumnRef> keys = UNDECLARED_KEYS;

    public VisSelectionModel() {
        timer = new Timer() {
            @Override
            public void run() {
                SelectionEvent.fire(VisSelectionModel.this, currentSelection);
            }
        };
    }

    /// The keys a visualisation's selections have, as columns, e.g. for a text pane to read its
    /// stream id from or a selection handler to insert.
    ///
    /// @return The keys the visualisation declares (none if nothing can be selected in it), or `name`
    ///         and `value` if it doesn't declare them.
    @Override
    public List<ColumnRef> getColumnRefs() {
        return keys;
    }

    /// Takes the keys the visualisation shown declares, whenever one is loaded.
    ///
    /// @param visSettings The visualisation's settings, or null if it has none or they can't be read.
    public void setVisSettings(final JSONObject visSettings) {
        keys = columnRefsOf(parseSelectionKeys(visSettings));
    }

    /// Reads the keys a visualisation's settings say its selections have, from their `selection`
    /// section (see [VisSelectionKey]). An entry without a key is left out.
    ///
    /// @param visSettings The visualisation's settings, may be null.
    /// @return Its selection keys, in the order declared (empty if it declares that nothing can be
    ///         selected in it), or null if it has no `selection` section.
    public static List<VisSelectionKey> parseSelectionKeys(final JSONObject visSettings) {
        final JSONArray array = visSettings == null
                ? null
                : JSONUtil.getArray(visSettings.get("selection"));
        if (array == null) {
            return null;
        }
        final List<VisSelectionKey> selectionKeys = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            final JSONObject json = JSONUtil.getObject(array.get(i));
            final String key = json == null
                    ? null
                    : JSONUtil.getString(json.get("key"));
            if (key != null) {
                selectionKeys.add(new VisSelectionKey(key,
                        JSONUtil.getString(json.get("label")),
                        JSONUtil.getString(json.get("type"))));
            }
        }
        return selectionKeys;
    }

    /// The columns of a visualisation's selection keys, one per key in the order declared. Each
    /// column's id and name are the key itself, as that is what its selections' values are under.
    /// Keys that are blank or have spaces around them are left out, as no selection the
    /// visualisation sends under them could be named in a parameter.
    ///
    /// @param selectionKeys The keys from its settings: empty if nothing can be selected in it, or
    ///                      null if it doesn't declare them.
    /// @return The columns, or `name` and `value` if it doesn't declare its keys, as all
    ///         visualisations were taken to have before keys were declared.
    public static List<ColumnRef> columnRefsOf(final List<VisSelectionKey> selectionKeys) {
        if (selectionKeys == null) {
            return UNDECLARED_KEYS;
        }
        final List<ColumnRef> columnRefs = new ArrayList<>();
        for (final VisSelectionKey selectionKey : selectionKeys) {
            final String key = NullSafe.get(selectionKey, VisSelectionKey::getKey);
            if (NullSafe.isNonBlankString(key) && key.equals(key.trim())) {
                final ColumnRef columnRef = new ColumnRef(key, key);
                if (!columnRefs.contains(columnRef)) {
                    columnRefs.add(columnRef);
                }
            }
        }
        return Collections.unmodifiableList(columnRefs);
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
