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

package stroom.item.client;

import stroom.docref.HasDisplayValue;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.Collection;
import java.util.function.Function;

public class SelectionBox<T> extends BaseSelectionBox<T, SimpleSelectionItemWrapper<T>> {

    private final SimpleSelectionListModel<T> model = new SimpleSelectionListModel<>();

    public SelectionBox() {
        super();
        setModel(model);
    }

    /**
     * Set this if you want a custom display value for each item.
     * If not set it will do the following:
     * <p>If the item implements {@link stroom.docref.HasDisplayValue} use the displayValue.</p>
     * <p>If the item is a {@link String} use that.</p>
     * <p>Else use the value of {@link Object#toString()}.</p>
     * <p>The display value is the value used in the text box part when the item is selected.</p>
     */
    public void setDisplayValueFunction(final Function<T, String> displayValueFunction) {
        model.setDisplayValueFunction(displayValueFunction);
    }

    /**
     * This function will be used to optionally provide a rendered form of the item in the list,
     * instead of plain text (but NOT in the text box part).
     * If not set then it will fall back on displayValueFunction, {@link HasDisplayValue} or
     * {@link Object#toString()}.
     */
    public void setRenderFunction(final Function<T, SafeHtml> renderFunction) {
        model.setRenderFunction(renderFunction);
    }

    public void setNonSelectString(final String nonSelectString) {
        model.setNonSelectString(nonSelectString);
    }

    public void addItems(final Collection<T> items) {
        model.addItems(items);
    }

    public void addItems(final T[] items) {
        model.addItems(items);
    }

    public void addItem(final T item) {
        model.addItem(item);
    }

    public void clear() {
        model.clear();
    }
}
