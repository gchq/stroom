/*
 * Copyright 2017 Crown Copyright
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

package stroom.dashboard.client.text;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsBuilder;

import java.util.List;

public class BasicTextSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTextSettingsPresenter.BasicTextSettingsView> {
    private final EntityDropDownPresenter pipelinePresenter;

    @Inject
    public BasicTextSettingsPresenter(final EventBus eventBus, final BasicTextSettingsView view,
                                      final EntityDropDownPresenter pipelinePresenter) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setPipelineView(pipelinePresenter.getView());
    }

    private void setTableList(final List<Component> list) {
        getView().setTableList(list);
    }

    private DocRef getPipeline() {
        return pipelinePresenter.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        pipelinePresenter.setSelectedEntityReference(pipeline);
    }

    private boolean isShowAsHtml() {
        return getView().isShowAsHtml();
    }

    private void setShowAsHtml(final boolean showAsHtml) {
        getView().setShowAsHtml(showAsHtml);
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);

        final List<Component> list = getComponents().getComponentsByType(TablePresenter.TYPE.getId());
        setTableList(list);

        final TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();
        setTableId(settings.getTableId());
        setPipeline(settings.getPipeline());
        setShowAsHtml(settings.isShowAsHtml());
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();
        settings.setTableId(getTableId());
        settings.setPipeline(getPipeline());
        settings.setShowAsHtml(isShowAsHtml());
    }

    private String getTableId() {
        final Component table = getView().getTable();
        if (table == null) {
            return null;
        }

        return table.getId();
    }

    private void setTableId(final String tableId) {
        getView().setTable(getComponents().get(tableId));
    }

    @Override
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final TextComponentSettings settings = (TextComponentSettings) componentData.getSettings();

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getTableId(), getTableId());
        builder.append(settings.getPipeline(), getPipeline());
        builder.append(settings.isShowAsHtml(), isShowAsHtml());

        return !builder.isEquals();
    }

    public interface BasicTextSettingsView extends BasicSettingsTabPresenter.SettingsView {
        void setTableList(List<Component> tableList);

        Component getTable();

        void setTable(Component table);

        void setPipelineView(View view);

        boolean isShowAsHtml();

        void setShowAsHtml(boolean showAsHtml);
    }
}
