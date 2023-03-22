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

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.HasValue;
import com.google.gwt.user.client.ui.ListBox;

import java.util.List;

public class StringListBox
        extends ListBox
        implements HasValue<String> {

    private boolean valueChangeHandlerInitialized;

    public String getSelected() {
        if (getSelectedIndex() < 0) {
            return null;
        }

        return getItemText(getSelectedIndex());
    }

    public void setSelected(final String selected) {
        int index = -1;
        for (int i = 0; i < getItemCount() && index == -1; i++) {
            if (getItemText(i).equals(selected)) {
                index = i;
            }
        }

        setSelectedIndex(index);
    }

    public void addItems(final List<String> items) {
        for (final String item : items) {
            addItem(item);
        }
    }

    @Override
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        // Initialization code
        if (!valueChangeHandlerInitialized) {
            valueChangeHandlerInitialized = true;
            addChangeHandler(event -> ValueChangeEvent.fire(StringListBox.this, getValue()));
        }
        return addHandler(handler, ValueChangeEvent.getType());
    }

    @Override
    public String getValue() {
        return getSelected();
    }

    @Override
    public void setValue(final String value) {
        setValue(value, false);
    }

    @Override
    public void setValue(final String value, final boolean fireEvents) {
        setSelected(value);
        if (fireEvents) {
            ValueChangeEvent.fire(this, value);
        }
    }
}
