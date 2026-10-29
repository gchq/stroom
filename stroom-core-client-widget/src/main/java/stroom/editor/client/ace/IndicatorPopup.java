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

package stroom.editor.client.ace;

import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.Widget;

/// A popup that displays a warning/error message for a given indicator. Its content's markup is
/// `IndicatorPopup.ui.xml`.
public class IndicatorPopup extends PopupPanel {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    Html content;

    /// Makes the popup, empty until [#setHTML(String)] or [#setText(String)] fills it.
    public IndicatorPopup() {
        setModal(false);
        setAutoHideEnabled(true);
        setStyleName("code-editor-indicator");
        // It describes the overview bar's marker or summary under the mouse
        getElement().setAttribute("role", "tooltip");

        setWidget(BINDER.createAndBindUi(this));
    }

    /**
     * Sets the html to be displayed in the popup.
     */
    public void setHTML(final String html) {
        content.setHTML(html);
    }

    /**
     * Sets the text to be displayed in the popup.
     */
    public void setText(final String text) {
        content.setText(text);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, IndicatorPopup> {

    }
}
