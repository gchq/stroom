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

package stroom.widget.text.client;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.HTMLPanel;
import com.google.gwt.user.client.ui.Widget;

/// A pane of HTML, e.g. a stream's information or a dashboard's text as HTML, that scrolls when the
/// HTML is bigger than it. Its markup is `HtmlPane.ui.xml`.
public class HtmlPane extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    HTMLPanel content;

    public HtmlPane() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Shows HTML, which is trusted as it is (it comes from Stroom's own server or code).
    ///
    /// @param html The HTML, or null for none.
    public void setHtml(final String html) {
        content.getElement().setInnerHTML(html == null
                ? ""
                : html);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, HtmlPane> {

    }
}
