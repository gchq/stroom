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

package stroom.widget.tab.client;

import stroom.widget.component.client.AriaPanel;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;

/// A tab bar's overflow button, shown when some tabs don't fit: a button showing every tab in a
/// menu, with the number of tabs hidden. The BEM block `tab-selector` (`tab-selector.css`), its
/// markup `TabSelector.ui.xml`. Its tab bar places it, shows and hides it, and opens its menu.
public class TabSelector extends Composite {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    AriaPanel button;
    @UiField
    Label count;

    /// Makes the button, with no count; its bar hides it until some tabs don't fit.
    public TabSelector() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows how many tabs are hidden, and names the button with it.
    ///
    /// @param hidden The number of tabs that don't fit.
    public void setHiddenCount(final int hidden) {
        count.setText(String.valueOf(hidden));
        button.setAriaLabel("Show all tabs (" + hidden + " hidden)");
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TabSelector> {

    }
}
