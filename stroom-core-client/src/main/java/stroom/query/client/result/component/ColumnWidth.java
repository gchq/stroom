/*
 * Copyright 2026 Crown Copyright
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

package stroom.query.client.result.component;

import stroom.widget.component.client.Component;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A table column's width, in pixels: the content of the Column Width dialog, the keyboard's way to
/// resize a column ([ColumnWidthController]).
public class ColumnWidth extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ValueSpinner width;

    /// Makes the content, with no width. (Enter in the width takes what was typed, as the spinner
    /// does everywhere, and goes no further; Tab reaches OK.)
    public ColumnWidth() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param min The narrowest width allowed, in pixels.
    /// @param max The widest width allowed, in pixels.
    public void setRange(final int min, final int max) {
        width.setMinValue(min);
        width.setMaxValue(max);
    }

    /// @return The width given, in pixels.
    public int getWidth() {
        return width.getIntValue();
    }

    /// @param width The width to start with, in pixels.
    public void setWidth(final int width) {
        this.width.setValue(width);
    }

    /// Puts the focus in the width.
    public void focus() {
        width.focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ColumnWidth> {

    }
}
