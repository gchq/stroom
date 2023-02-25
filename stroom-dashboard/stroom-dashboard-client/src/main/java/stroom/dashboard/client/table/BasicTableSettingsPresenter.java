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

import stroom.dashboard.client.main.BasicSettingsTabPresenter;
import stroom.dashboard.client.main.BasicSettingsView;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.query.QueryPresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.presenter.EntityDropDownPresenter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermissionNames;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BasicTableSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTableSettingsPresenter.BasicTableSettingsView>
        implements Focus {

    private final EntityDropDownPresenter pipelinePresenter;

    @Inject
    public BasicTableSettingsPresenter(final EventBus eventBus, final BasicTableSettingsView view,
                                       final EntityDropDownPresenter pipelinePresenter) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineDoc.DOCUMENT_TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermissionNames.USE);

        view.setPipelineView(pipelinePresenter.getView());
    }

    @Override
    public void focus() {
        getView().focus();
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

        final List<Component> list = getComponents().getSortedComponentsByType(QueryPresenter.TYPE.getId());
        setQueryList(list);

        final TableComponentSettings settings = (TableComponentSettings) componentData.getSettings();
        setQueryId(settings.getQueryId());
        setExtractValues(settings.extractValues());
        setPipeline(settings.getExtractionPipeline());
        setMaxResults(fromList(settings.getMaxResults()));
        setShowDetail(settings.showDetail());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        ComponentConfig result = super.write(componentConfig);
        final TableComponentSettings oldSettings = (TableComponentSettings) result.getSettings();
        final TableComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private TableComponentSettings writeSettings(final TableComponentSettings settings) {
        return settings
                .copy()
                .queryId(getQueryId())
                .extractValues(extractValues())
                .extractionPipeline(getPipeline())
                .maxResults(toList(getMaxResults()))
                .showDetail(showDetail())
                .build();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TableComponentSettings oldSettings = (TableComponentSettings) componentConfig.getSettings();
        final TableComponentSettings newSettings = writeSettings(oldSettings);

        final boolean equal = Objects.equals(oldSettings.getQueryId(), newSettings.getQueryId()) &&
                Objects.equals(oldSettings.extractValues(), newSettings.extractValues()) &&
                Objects.equals(oldSettings.getExtractionPipeline(), newSettings.getExtractionPipeline()) &&
                Objects.equals(oldSettings.getMaxResults(), newSettings.getMaxResults()) &&
                Objects.equals(oldSettings.getShowDetail(), newSettings.getShowDetail());

        return !equal;
    }

    private String fromList(final List<Integer> maxResults) {
        if (maxResults == null || maxResults.size() == 0) {
            return "";
        }

        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < maxResults.size(); i++) {
            sb.append(maxResults.get(i));
            if (i < maxResults.size() - 1) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private List<Integer> toList(final String string) {
        if (string == null || string.length() == 0) {
            return null;
        }

        final String[] parts = string.split(",");
        final List<Integer> list = new ArrayList<>();
        for (final String part : parts) {
            try {
                list.add(Integer.parseInt(part.trim()));
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return list;
    }

    public interface BasicTableSettingsView extends BasicSettingsView {

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
