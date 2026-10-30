/*
 * Copyright 2021 Crown Copyright
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

package stroom.core.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.Event;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.ResizeLayoutPanel;
import com.google.gwt.user.client.ui.Widget;

/// A document shown as the whole page, with no main screen around it (a URL naming a document).
public class FullScreen extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ResizeLayoutPanel contentPanel;

    /// Makes the screen, with no content until it is given it.
    public FullScreen() {
        initWidget(BINDER.createAndBindUi(this));
        sinkEvents(Event.KEYEVENTS);
    }

    /// Shows the document.
    ///
    /// @param content The document's widget.
    public void setContent(final IsWidget content) {
        contentPanel.setWidget(content.asWidget());
    }

    interface Binder extends UiBinder<Widget, FullScreen> {

    }
}
