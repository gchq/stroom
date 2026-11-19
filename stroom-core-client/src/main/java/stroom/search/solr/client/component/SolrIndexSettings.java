/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.solr.client.component;

import stroom.query.client.component.ExpressionEditor;
import stroom.search.solr.shared.SolrConnectionConfig.InstanceType;
import stroom.widget.button.client.Button;
import stroom.widget.component.client.Component;
import stroom.widget.dropdowntree.client.DropDownBox;
import stroom.widget.form.client.TextArea;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/// A Solr index's Settings tab: its collection, how to reach Solr (a single node's URL, or a cloud's
/// URLs or ZooKeeper hosts), its main time field, its default extraction pipeline (a picker its
/// controller attaches), a button to test the connection, and its data retention expression (shown
/// here by its controller).
public class SolrIndexSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox collection;
    @UiField
    SelectionBox<InstanceType> instanceType;
    @UiField
    TextArea solrUrls;
    @UiField
    TickBox useZk;
    @UiField
    TextArea zkHosts;
    @UiField
    TextArea zkPath;
    @UiField
    TextBox timeField;
    @UiField
    DropDownBox defaultExtractionPipeline;
    @UiField
    Button testConnection;
    @UiField
    ExpressionEditor retentionExpressionEditor;

    private Runnable onChange = () -> {
    };

    /// Makes the tab, for a single node.
    public SolrIndexSettings() {
        initWidget(BINDER.createAndBindUi(this));

        instanceType.addItem(InstanceType.SINGLE_NOOE);
        instanceType.addItem(InstanceType.SOLR_CLOUD);

        // The text fields tell of a change as a key goes down
        collection.addKeyDownHandler(e -> onChange.run());
        solrUrls.addKeyDownHandler(e -> onChange.run());
        zkHosts.addKeyDownHandler(e -> onChange.run());
        zkPath.addKeyDownHandler(e -> onChange.run());
        timeField.addKeyDownHandler(e -> onChange.run());
        instanceType.addValueChangeHandler(e -> {
            updateEnabledState();
            onChange.run();
        });
        useZk.addValueChangeHandler(e -> {
            updateEnabledState();
            onChange.run();
        });
        updateEnabledState();
    }

    /// @return The collection, trimmed.
    public String getCollection() {
        return collection.getText().trim();
    }

    /// @param collection The collection.
    public void setCollection(final String collection) {
        this.collection.setText(collection);
    }

    /// @return The type of Solr installation.
    public InstanceType getInstanceType() {
        return instanceType.getValue();
    }

    /// @param instanceType The type of Solr installation, or `null` for a single node.
    public void setInstanceType(final InstanceType instanceType) {
        if (instanceType == null) {
            this.instanceType.setValue(InstanceType.SINGLE_NOOE);
        } else {
            this.instanceType.setValue(instanceType);
        }
        updateEnabledState();
    }

    /// @return The Solr URLs, one per line.
    public List<String> getSolrUrls() {
        return Arrays.stream(solrUrls.getText().split("\n")).collect(Collectors.toList());
    }

    /// @param solrUrls The Solr URLs, shown one per line, or `null` for none.
    public void setSolrUrls(final List<String> solrUrls) {
        if (solrUrls == null) {
            this.solrUrls.setText("");
        } else {
            this.solrUrls.setText(String.join("\n", solrUrls));
        }
    }

    /// @return Whether the cloud is found through its ZooKeeper hosts.
    public boolean isUseZk() {
        return useZk.getValue();
    }

    /// @param useZk Whether the cloud is found through its ZooKeeper hosts.
    public void setUseZk(final boolean useZk) {
        this.useZk.setValue(useZk);
        updateEnabledState();
    }

    /// @return The ZooKeeper hosts, one per line.
    public List<String> getZkHosts() {
        return Arrays.stream(zkHosts.getText().split("\n")).collect(Collectors.toList());
    }

    /// @param zkHosts The ZooKeeper hosts, shown one per line, or `null` for none.
    public void setZkHosts(final List<String> zkHosts) {
        if (zkHosts == null) {
            this.zkHosts.setText("");
        } else {
            this.zkHosts.setText(String.join("\n", zkHosts));
        }
    }

    /// @return The ZooKeeper path.
    public String getZkPath() {
        return zkPath.getText();
    }

    /// @param zkPath The ZooKeeper path, or `null` for none.
    public void setZkPath(final String zkPath) {
        if (zkPath == null) {
            this.zkPath.setText("");
        } else {
            this.zkPath.setText(zkPath);
        }
    }

    /// @return The expression's editor, which its controller attaches the editor's controller to.
    public ExpressionEditor getRetentionExpressionEditor() {
        return retentionExpressionEditor;
    }

    /// @return The main time field's name.
    public String getTimeField() {
        return timeField.getValue();
    }

    /// @param timeField The main time field's name.
    public void setTimeField(final String timeField) {
        this.timeField.setValue(timeField);
    }

    /// @return The box its controller attaches the default extraction pipeline picker to.
    public DropDownBox getDefaultExtractionPipelineBox() {
        return defaultExtractionPipeline;
    }

    /// @param testing Whether the connection is being tested, so Test Connection spins.
    public void setTestingConnection(final boolean testing) {
        testConnection.setLoading(testing);
    }

    /// @return The Test Connection button, which spins while the connection is tested.
    public TaskMonitorFactory getTestConnectionTaskMonitor() {
        return testConnection;
    }

    /// @param onChange Run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @param onTestConnection Run when Test Connection is pressed.
    public void setOnTestConnection(final Runnable onTestConnection) {
        testConnection.setOnClick(onTestConnection);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        instanceType.setReadOnly(readOnly);
        solrUrls.setReadOnly(readOnly);
        useZk.setReadOnly(readOnly);
        zkHosts.setReadOnly(readOnly);
        zkPath.setReadOnly(readOnly);
        collection.setReadOnly(readOnly);
        timeField.setReadOnly(readOnly);
    }

    // The ZooKeeper fields are used only to find a Solr Cloud through ZooKeeper. Read only is
    // applied separately (setReadOnly), so enabling them never makes them editable
    private void updateEnabledState() {
        final boolean zk = InstanceType.SOLR_CLOUD.equals(instanceType.getValue()) && useZk.getValue();
        zkHosts.setEnabled(zk);
        zkPath.setEnabled(zk);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SolrIndexSettings> {

    }
}
