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

package stroom.ai.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The Download Options dialog's content, for an Ask Stroom AI conversation: whether the data
/// attached to it goes in the download.
public class DownloadChat extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox includeDataContexts;

    /// Makes the content, not ticked.
    public DownloadChat() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus on the tick box.
    public void focus() {
        includeDataContexts.setFocus(true);
    }

    /// @return Whether the data attached to the conversation goes in the download.
    public boolean isIncludeDataContexts() {
        return includeDataContexts.getValue();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DownloadChat> {

    }
}
