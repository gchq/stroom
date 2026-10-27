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

package stroom.dashboard.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.RequiresResize;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A dashboard's Embedded Query pane: its query's results, as a table or a visualisation, which
/// are made and dropped as the query runs and its settings change.
public class EmbeddedQuery extends Component implements RequiresResize {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel result;

    /// Makes the pane, showing nothing until [#setResult].
    public EmbeddedQuery() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param result The results to show, a table or a visualisation, or null for none.
    public void setResult(final IsWidget result) {
        if (result != null) {
            this.result.setWidget(result.asWidget());
        } else {
            this.result.clear();
        }
    }

    @Override
    public void onResize() {
        if (result.getWidget() instanceof final RequiresResize requiresResize) {
            requiresResize.onResize();
        }
    }

    interface Binder extends UiBinder<Widget, EmbeddedQuery> {

    }
}
