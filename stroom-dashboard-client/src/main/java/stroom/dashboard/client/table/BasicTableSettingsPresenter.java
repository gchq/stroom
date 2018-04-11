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

package stroom.dashboard.client.table;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;
import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.DocRef;
import stroom.security.shared.DocumentPermissionNames;
import stroom.util.shared.EqualsBuilder;

import java.util.ArrayList;
import java.util.List;

public class BasicTableSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTableSettingsPresenter.BasicTableSettingsView> {
    private final EntityDropDownPresenter pipelinePresenter;

    @Inject
    public BasicTableSettingsPresenter(final EventBus eventBus, final BasicTableSettingsView view,
                                       final EntityDropDownPresenter pipelinePresenter) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineEntity.ENTITY_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setPipelineView(pipelinePresenter.getView());
    }

    private void setQueryList(final List<Component> list) {
        getView().setQueryList(list);
    }

    private String getQueryId() {
        final Component component = getView().getQuery();
        if (component == null) {
            return null;
        }

        return component.getId();
    }

    private void setQueryId(final String queryId) {
        getView().setQuery(getComponents().get(queryId));
    }

    private boolean extractValues() {
        return getView().isExtractValues();
    }

    private void setExtractValues(final boolean extractValues) {
        getView().setExtractValues(extractValues);
    }

    private DocRef getPipeline() {
        return pipelinePresenter.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        pipelinePresenter.setSelectedEntityReference(pipeline);
    }

    private String getMaxResults() {
        return getView().getMaxResults();
    }

    private void setMaxResults(final String maxResults) {
        getView().setMaxResults(maxResults);
    }

    private boolean showDetail() {
        return getView().isShowDetail();
    }

    private void setShowDetail(final boolean showDetail) {
        getView().setShowDetail(showDetail);
    }

    @Override
    public void read(final ComponentConfig componentData) {
        super.read(componentData);

        final List<Component> list = getComponents().getComponentsByType(QueryPresenter.TYPE.getId());
        setQueryList(list);

        final TableComponentSettings settings = (TableComponentSettings) componentData.getSettings();
        setQueryId(settings.getQueryId());
        setExtractValues(settings.extractValues());
        setPipeline(settings.getExtractionPipeline());
        setMaxResults(fromArray(settings.getMaxResults()));
        setShowDetail(settings.showDetail());
    }

    @Override
    public void write(final ComponentConfig componentData) {
        super.write(componentData);

        final TableComponentSettings settings = (TableComponentSettings) componentData.getSettings();
        settings.setQueryId(getQueryId());
        settings.setExtractValues(extractValues());
        settings.setExtractionPipeline(getPipeline());
        settings.setMaxResults(toArray(getMaxResults()));
        settings.setShowDetail(showDetail());
    }

    @Override
    public boolean isDirty(final ComponentConfig componentData) {
        if (super.isDirty(componentData)) {
            return true;
        }

        final TableComponentSettings settings = (TableComponentSettings) componentData.getSettings();

        final EqualsBuilder builder = new EqualsBuilder();
        builder.append(settings.getQueryId(), getQueryId());
        builder.append(settings.extractValues(), extractValues());
        builder.append(settings.getExtractionPipeline(), getPipeline());
        builder.append(settings.getMaxResults(), getMaxResults());
        builder.append(settings.showDetail(), showDetail());

        return !builder.isEquals();
    }

    private String fromArray(final int[] maxResults) {
        if (maxResults == null || maxResults.length == 0) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxResults.length; i++) {
            sb.append(maxResults[i]);
            if (i < maxResults.length - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private int[] toArray(final String string) {
        if (string == null || string.length() == 0) {
            return null;
        }

        final String[] parts = string.split(",");
        final List<Integer> list = new ArrayList<>();
        for (final String part : parts) {
            try {
                list.add(Integer.parseInt(part.trim()));
            } catch (final RuntimeException e) {
            }
        }

        final int[] array = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            array[i] = list.get(i);
        }

        return array;
    }

    public interface BasicTableSettingsView extends BasicSettingsTabPresenter.SettingsView {
        void setQueryList(List<Component> queryList);

        Component getQuery();

        void setQuery(Component query);

        boolean isExtractValues();

        void setExtractValues(boolean extractValues);

        void setPipelineView(View view);

        String getMaxResults();

        void setMaxResults(String maxResults);

        boolean isShowDetail();

        void setShowDetail(boolean showDetail);
    }
}
