/*
 * Copyright 2023 Crown Copyright
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

import stroom.analytics.shared.TableBuilderAnalyticProcessConfig;
import stroom.item.client.SelectionBox;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.customdatebox.client.DurationPicker;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.text.client.Html;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A rule's table builder processing settings: whether it runs, on which node, which streams it
/// takes, how long it waits for late data and keeps data, and its progress so far.
public class TableBuilderProcessing extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TickBox enabled;
    @UiField
    SelectionBox<String> node;
    @UiField
    DateTimeBox minMetaCreateTimeMs;
    @UiField
    DateTimeBox maxMetaCreateTimeMs;
    @UiField
    DurationPicker timeToWaitForData;
    @UiField
    DurationPicker dataRetention;
    @UiField
    Html info;
    @UiField
    Button refresh;

    // The node to choose once the nodes come, if they haven't yet
    private String selectedNode;
    private Runnable onChange = () -> {
    };

    /// Makes empty settings.
    public TableBuilderProcessing() {
        initWidget(BINDER.createAndBindUi(this));
        timeToWaitForData.setValue(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build());
        dataRetention.setValue(SimpleDuration.builder().time(1).timeUnit(TimeUnit.HOURS).build());

        enabled.addValueChangeHandler(e -> onChange.run());
        node.addValueChangeHandler(e -> onChange.run());
        minMetaCreateTimeMs.addValueChangeHandler(e -> onChange.run());
        maxMetaCreateTimeMs.addValueChangeHandler(e -> onChange.run());
        timeToWaitForData.addValueChangeHandler(e -> onChange.run());
        dataRetention.addValueChangeHandler(e -> onChange.run());
    }

    /// @return The minimum stream create time's box, for its controller to attach to.
    public DateTimeBox getMinMetaCreateTimeBox() {
        return minMetaCreateTimeMs;
    }

    /// @return The maximum stream create time's box, for its controller to attach to.
    public DateTimeBox getMaxMetaCreateTimeBox() {
        return maxMetaCreateTimeMs;
    }

    /// Offers the nodes, choosing the settings' node if it has been shown, else the first.
    ///
    /// @param nodes The nodes' names.
    public void setNodes(final List<String> nodes) {
        this.node.clear();
        this.node.addItems(nodes);
        if (selectedNode == null) {
            if (!nodes.isEmpty()) {
                //noinspection SequencedCollectionMethodCanBeUsed // Not in GWT
                this.node.setValue(nodes.get(0));
            }
        } else {
            this.node.setValue(selectedNode);
            selectedNode = null;
        }
    }

    /// Shows the settings.
    ///
    /// @param config The settings.
    public void read(final TableBuilderAnalyticProcessConfig config) {
        enabled.setValue(config.isEnabled());
        if (config.getNode() != null) {
            selectedNode = config.getNode();
            node.setValue(config.getNode());
        }
        minMetaCreateTimeMs.setValue(config.getMinMetaCreateTimeMs());
        maxMetaCreateTimeMs.setValue(config.getMaxMetaCreateTimeMs());
        if (config.getTimeToWaitForData() != null) {
            timeToWaitForData.setValue(config.getTimeToWaitForData());
        }
        // None keeps all the data, which the server takes it to mean. Before, none showed the
        // picker's 1 hour, which a save then stored
        dataRetention.setValue(config.getDataRetention());
    }

    /// @return The settings shown.
    public TableBuilderAnalyticProcessConfig write() {
        return TableBuilderAnalyticProcessConfig
                .builder()
                .enabled(enabled.getValue())
                .node(node.getValue())
                .minMetaCreateTimeMs(minMetaCreateTimeMs.getValue())
                .maxMetaCreateTimeMs(maxMetaCreateTimeMs.getValue())
                .timeToWaitForData(timeToWaitForData.getValue())
                .dataRetention(dataRetention.getValue())
                .build();
    }

    /// Shows the table builder's progress.
    ///
    /// @param info The progress, as a table.
    public void setInfo(final SafeHtml info) {
        this.info.setHTML(info);
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onRefresh What to run when the user asks for the progress to be refreshed.
    public void setOnRefresh(final Runnable onRefresh) {
        refresh.setOnClick(onRefresh);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        enabled.setReadOnly(readOnly);
        node.setReadOnly(readOnly);
        minMetaCreateTimeMs.setReadOnly(readOnly);
        maxMetaCreateTimeMs.setReadOnly(readOnly);
        timeToWaitForData.setReadOnly(readOnly);
        dataRetention.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TableBuilderProcessing> {

    }
}
