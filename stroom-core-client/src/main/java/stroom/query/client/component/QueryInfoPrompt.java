/*
 * Copyright 2018 Crown Copyright
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

package stroom.query.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextArea;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The query info dialog's content: why the query is being run, which a site may ask for.
public class QueryInfoPrompt extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextArea queryInfo;

    /// Makes empty content, which has the focus when shown.
    public QueryInfoPrompt() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus in the text.
    public void focus() {
        queryInfo.setFocus(true);
    }

    /// @return The text entered.
    public String getQueryInfo() {
        return queryInfo.getText();
    }

    /// @param queryInfo The text to start with.
    public void setQueryInfo(final String queryInfo) {
        this.queryInfo.setText(queryInfo);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, QueryInfoPrompt> {

    }
}
