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

package stroom.dashboard.client.text;

import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TextSettings;
import stroom.entity.shared.DocRef;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.shared.PipelineEntity;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsBuilder;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public class BasicTextSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTextSettingsPresenter.BasicTextSettingsView> {
    private final EntityDropDownPresenter pipelinePresenter;

    @Inject
    public BasicTextSettingsPresenter(final EventBus eventBus, final BasicTextSettingsView view,
            final EntityDropDownPresenter pipelinePresenter) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setPipelineView(pipelinePresenter.getView());
    }

    private void setTableIdList(final List<String> list) {
        getView().setTableIdList(list);
    }

    private String getTableId() {
        return getView().getTableId();
    }

    private void setTableId(final String tableId) {
        getView().setTableId(tableId);
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

        final List<String> list = getComponents().getIdListByType(TablePresenter.TYPE.getId());
        setTableIdList(list);

        final TextSettings settings = (TextSettings) componentData.getSettings();
        setTableId(settings.getTableId());
        setPipeline(settings.getPipeline());
        setShowAsHtml(settings.isShowAsHtml());
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final TextSettings settings = (TextSettings) componentData.getSettings();
        settings.setTableId(getTableId());
        settings.setPipeline(getPipeline());
        settings.setShowAsHtml(isShowAsHtml());
    }

    @Override
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final TextSettings settings = (TextSettings) componentData.getSettings();

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getTableId(), getTableId());
        builder.append(settings.getPipeline(), getPipeline());
        builder.append(settings.isShowAsHtml(), isShowAsHtml());

        return !builder.isEquals();
    }

    public interface BasicTextSettingsView extends BasicSettingsTabPresenter.SettingsView {
        void setTableIdList(List<String> tableIdList);

        String getTableId();

        void setTableId(String tableId);

        void setPipelineView(View view);

        boolean isShowAsHtml();

        void setShowAsHtml(boolean showAsHtml);
    }
}
