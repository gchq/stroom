/*
 * Copyright 2022 Crown Copyright
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

package stroom.analytics.client.component;

import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// An analytic rule's Shards tab: its data shards above the rows of the shard selected (both shown
/// here by its controller).
public class AnalyticDataShards extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel shardList;
    @UiField
    SimplePanel table;

    /// Makes the tab, with no shards or rows.
    public AnalyticDataShards() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @param shardList The rule's data shards.
    public void setShardList(final IsWidget shardList) {
        this.shardList.setWidget(shardList);
    }

    /// @param table The rows of the shard selected.
    public void setTable(final IsWidget table) {
        this.table.setWidget(table);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, AnalyticDataShards> {

    }
}
