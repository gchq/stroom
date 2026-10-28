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

package stroom.widget.tooltip.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.text.client.Html;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;

/// The content of a grid row's "Information" popup (the info column's): text or HTML that the
/// keyboard can reach and scroll. Its markup is `InfoTooltip.ui.xml`.
public class InfoTooltip extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private final Html content;

    public InfoTooltip() {
        content = BINDER.createAndBindUi(this);
        // A tab stop, so that the focus the popup gives it lands, and the keyboard can scroll it (an
        // HTML widget has no tab index a template can set)
        content.getElement().setTabIndex(0);
        initWidget(content);
    }

    /// @param text The information, as plain text.
    public void setText(final String text) {
        content.setText(text);
    }

    /// @param html The information, as HTML.
    public void setHtml(final SafeHtml html) {
        content.setHTML(html);
    }

    /// Puts the focus on the information, so that a screen reader reads it.
    public void focus() {
        content.getElement().focus();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Html, InfoTooltip> {

    }
}
