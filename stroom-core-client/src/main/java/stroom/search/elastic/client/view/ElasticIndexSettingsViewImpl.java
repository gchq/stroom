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

package stroom.search.elastic.client.view;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsPresenter.ElasticIndexSettingsView;
import stroom.search.elastic.client.presenter.ElasticIndexSettingsUiHandlers;
import stroom.widget.layout.client.view.ResizeSimplePanel;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Button;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class ElasticIndexSettingsViewImpl extends ViewWithUiHandlers<ElasticIndexSettingsUiHandlers> implements ElasticIndexSettingsView, ReadOnlyChangeHandler {
    private final Widget widget;

    @UiField
    TextArea description;
    @UiField
    TextBox indexName;
    @UiField
    SimplePanel cluster;
    @UiField
    Button testConnection;
    @UiField
    ResizeSimplePanel retentionExpressionPanel;

    @Inject
    public ElasticIndexSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        description.addKeyDownHandler(e -> fireChange());
        indexName.addKeyDownHandler(e -> fireChange());
    }

    private void fireChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getDescription() {
        return description.getText().trim();
    }

    @Override
    public void setDescription(final String description) { this.description.setText(description); }

    @Override
    public void setClusterView(final View view) { cluster.setWidget(view.asWidget()); }

    @Override
    public String getIndexName() { return indexName.getText().trim(); }

    @Override
    public void setIndexName(final String indexName) { this.indexName.setText(indexName); }

    @Override
    public void setRententionExpressionView(final View view) {
        retentionExpressionPanel.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        description.setEnabled(!readOnly);
        indexName.setEnabled(!readOnly);
    }

    @UiHandler("testConnection")
    public void onTestConnectionClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onTestConnection();
        }
    }

    public interface Binder extends UiBinder<Widget, ElasticIndexSettingsViewImpl> {
    }
}
