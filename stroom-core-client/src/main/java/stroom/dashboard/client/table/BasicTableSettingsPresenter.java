/*
 * Copyright 2016-2025 Crown Copyright
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
import stroom.explorer.client.presenter.DocSelectionBoxPresenter;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.util.shared.NullSafe;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BasicTableSettingsPresenter
        extends BasicSettingsTabPresenter<BasicTableSettingsPresenter.BasicTableSettingsView>
        implements Focus, BasicTableSettingsUihandlers {

    private final DocSelectionBoxPresenter pipelinePresenter;

    @Inject
    public BasicTableSettingsPresenter(final EventBus eventBus,
                                       final BasicTableSettingsView view,
                                       final DocSelectionBoxPresenter pipelinePresenter,
                                       final UiConfigCache uiConfigCache) {
        super(eventBus, view);
        this.pipelinePresenter = pipelinePresenter;

        pipelinePresenter.setIncludedTypes(PipelineDoc.TYPE);
        pipelinePresenter.setRequiredPermissions(DocumentPermission.USE);

        view.setPipelineView(pipelinePresenter.getView());

        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                NullSafe.consume(
                        extendedUiConfig.getQuery(),
                        QueryConfig::getDashboardPipelineSelectorIncludedTags,
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipelinePresenter::setQuickFilter);
            }
        }, this);

        view.setUiHandlers(this);
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
        getView().setQuery(getDashboardContext().getComponents().get(queryId));
    }

    private boolean extractValues() {
        return getView().isExtractValues();
    }

    private void setExtractValues(final boolean extractValues) {
        getView().setExtractValues(extractValues);
    }

    private boolean useDefaultExtractionPipeline() {
        return getView().isUseDefaultExtractionPipeline();
    }

    private void setUseDefaultExtractionPipeline(final boolean useDefaultExtractionPipeline) {
        getView().setUseDefaultExtractionPipeline(useDefaultExtractionPipeline);
        pipelinePresenter.setEnabled(!useDefaultExtractionPipeline);
    }

    @Override
    public void onUseDefaultExtractionPipeline(final boolean useDefaultExtractionPipeline) {
        pipelinePresenter.setEnabled(!useDefaultExtractionPipeline);
    }

    private DocRef getPipeline() {
//        GWT.log("getSelectedEntityReference: " + pipelinePresenter.getSelectedEntityReference());
        return pipelinePresenter.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        pipelinePresenter.setSelectedEntityReference(pipeline, true);
    }

    private String getMaxResults() {
        return getView().getMaxResults();
    }

    private void setMaxResults(final String maxResults) {
        getView().setMaxResults(maxResults);
    }

    private int getPageSize() {
        return getView().getPageSize();
    }

    private void setPageSize(final int pageSize) {
        getView().setPageSize(pageSize);
    }

    private boolean showDetail() {
        return getView().isShowDetail();
    }

    private void setShowDetail(final boolean showDetail) {
        getView().setShowDetail(showDetail);
    }

    private Integer getMaxStringFieldLength() {
        return getView().getMaxStringFieldLength();
    }

    private boolean getOverrideMaxStringFieldLength() {
        return getView().isOverrideMaxStringFieldLength();
    }

    private void setMaxStringFieldLength(final Integer maxStringFieldLength) {
        getView().setMaxStringFieldLength(maxStringFieldLength);
    }

    private void setOverrideMaxStringFieldLength(final boolean overrideMaxStringFieldLength) {
        getView().setOverrideMaxStringFieldLength(overrideMaxStringFieldLength);
        getView().enableMaxStringFieldLength(overrideMaxStringFieldLength);
    }

    @Override
    public void onOverrideMaxStringFieldLength(final boolean overrideMaxStringFieldLength) {
        getView().enableMaxStringFieldLength(overrideMaxStringFieldLength);
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<Component> list = getDashboardContext()
                .getComponents().getSortedComponentsByType(QueryPresenter.TYPE.getId());
        setQueryList(list);

        final TableComponentSettings settings = (TableComponentSettings) componentConfig.getSettings();
        setQueryId(settings.getQueryId());
        setExtractValues(settings.extractValues());
        setUseDefaultExtractionPipeline(settings.useDefaultExtractionPipeline());
        setPipeline(settings.getExtractionPipeline());
        setMaxResults(fromList(settings.getMaxResults()));
        setPageSize(settings.getPageSize() != null
                ? settings.getPageSize()
                : 100);

        setOverrideMaxStringFieldLength(settings.overrideMaxStringFieldLength());
        setMaxStringFieldLength(settings.getMaxStringFieldLength() == null ? 1000 : settings.getMaxStringFieldLength());

        setShowDetail(settings.showDetail());
    }

    @Override
    public ComponentConfig write(final ComponentConfig componentConfig) {
        final ComponentConfig result = super.write(componentConfig);
        final TableComponentSettings oldSettings = (TableComponentSettings) result.getSettings();
        final TableComponentSettings newSettings = writeSettings(oldSettings);
        return result.copy().settings(newSettings).build();
    }

    private TableComponentSettings writeSettings(final TableComponentSettings settings) {
        return settings
                .copy()
                .queryId(getQueryId())
                .extractValues(extractValues())
                .useDefaultExtractionPipeline(useDefaultExtractionPipeline())
                .extractionPipeline(getPipeline())
                .maxResults(toList(getMaxResults()))
                .pageSize(getPageSize())
                .showDetail(showDetail())
                .maxStringFieldLength(getMaxStringFieldLength())
                .overrideMaxStringFieldLength(getOverrideMaxStringFieldLength())
                .build();
    }

    @Override
    public boolean isDirty(final ComponentConfig componentConfig) {
        if (super.isDirty(componentConfig)) {
            return true;
        }

        final TableComponentSettings oldSettings = (TableComponentSettings) componentConfig.getSettings();
        final TableComponentSettings newSettings = writeSettings(oldSettings);

        // Need to compare extractionPipeline including name in case it has been renamed after decoration
        final boolean equal = Objects.equals(oldSettings.getQueryId(), newSettings.getQueryId()) &&
                              Objects.equals(oldSettings.extractValues(), newSettings.extractValues()) &&
                              Objects.equals(oldSettings.useDefaultExtractionPipeline(),
                                      newSettings.useDefaultExtractionPipeline()) &&
                              Objects.equals(oldSettings.getExtractionPipeline(),
                                      newSettings.getExtractionPipeline()) &&
                              Objects.equals(oldSettings.getMaxResults(), newSettings.getMaxResults()) &&
                              Objects.equals(oldSettings.getPageSize(), newSettings.getPageSize()) &&
                              Objects.equals(oldSettings.getShowDetail(), newSettings.getShowDetail()) &&
                              Objects.equals(oldSettings.getMaxStringFieldLength(),
                                      newSettings.getMaxStringFieldLength()) &&
                              Objects.equals(oldSettings.getOverrideMaxStringFieldLength(),
                                      newSettings.getOverrideMaxStringFieldLength());

        return !equal;
    }

    private String fromList(final List<Long> maxResults) {
        if (NullSafe.isEmptyCollection(maxResults)) {
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

    private List<Long> toList(final String string) {
        if (NullSafe.isEmptyString(string)) {
            return null;
        }

        final String[] parts = string.split(",");
        final List<Long> list = new ArrayList<>();
        for (final String part : parts) {
            try {
                list.add(Long.parseLong(part.trim()));
            } catch (final RuntimeException e) {
                // Ignore.
            }
        }

        return list;
    }

    // --------------------------------------------------------------------------------


    public interface BasicTableSettingsView extends BasicSettingsView, HasUiHandlers<BasicTableSettingsUihandlers> {

        void setQueryList(List<Component> queryList);

        Component getQuery();

        void setQuery(Component query);

        boolean isExtractValues();

        void setExtractValues(boolean extractValues);

        boolean isUseDefaultExtractionPipeline();

        void setUseDefaultExtractionPipeline(boolean extractValues);

        void setPipelineView(View view);

        String getMaxResults();

        void setMaxResults(String maxResults);

        int getPageSize();

        void setPageSize(int pageSize);

        boolean isShowDetail();

        void setShowDetail(boolean showDetail);

        void setMaxStringFieldLength(Integer maxStringFieldLength);

        Integer getMaxStringFieldLength();

        void setOverrideMaxStringFieldLength(boolean overrideMaxStringFieldLength);

        void enableMaxStringFieldLength(boolean enable);

        boolean isOverrideMaxStringFieldLength();
    }
}
