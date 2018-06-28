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

package stroom.item.client;

import com.google.gwt.event.logical.shared.HasSelectionHandlers;
import stroom.docref.HasDisplayValue;

import java.util.Collection;

public interface ItemListBoxDisplay<T extends HasDisplayValue> extends HasSelectionHandlers<T> {
    void addItem(T item);

    void addItems(Collection<T> list);

    void addItems(T[] list);

    void removeItem(T item);

    void setName(final String name);

    void clear();

    T getSelectedItem();

    void setSelectedItem(T item);

    void setEnabled(boolean enabled);
}
