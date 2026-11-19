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

package stroom.dashboard.client.component;

import stroom.dashboard.client.main.DashboardPane;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.docref.DocRef;
import stroom.explorer.client.component.DocSelectionController;
import stroom.explorer.shared.ExplorerTreeFilter;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.client.DataSourceClient;
import stroom.security.shared.DocumentPermission;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.QueryConfig;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/// A Table pane's Basic settings tab ([BasicTableSettings]): its name, its query, how values are
/// extracted, and how many results it keeps and shows.
public class BasicTableSettingsController extends BasicSettingsTabController<BasicTableSettings> {

    private final DocSelectionController pipeline;
    private final DataSourceClient dataSourceClient;
    private DocRef dataSourceRef;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus             The application's event bus.
    /// @param view                 The content.
    /// @param docSelectionProvider Makes the extraction pipeline's picker.
    /// @param uiConfigCache        Says which pipelines the picker offers.
    /// @param dataSourceClient     Gets a data source's default extraction pipeline.
    @Inject
    public BasicTableSettingsController(final EventBus eventBus,
                                        final BasicTableSettings view,
                                        final Provider<DocSelectionController> docSelectionProvider,
                                        final UiConfigCache uiConfigCache,
                                        final DataSourceClient dataSourceClient) {
        super(eventBus, view);
        this.pipeline = make(docSelectionProvider);
        pipeline.attach(view.getPipelineBox());
        this.dataSourceClient = dataSourceClient;

        pipeline.setIncludedTypes(PipelineDoc.TYPE);
        pipeline.setRequiredPermissions(DocumentPermission.USE);

        // Filter the pipeline picker by tags, if configured
        uiConfigCache.get(guard(extendedUiConfig -> {
            if (extendedUiConfig != null) {
                NullSafe.consume(
                        extendedUiConfig.getQuery(),
                        QueryConfig::getDashboardPipelineSelectorIncludedTags,
                        ExplorerTreeFilter::createTagQuickFilterInput,
                        pipeline::setQuickFilter);
            }
        }), taskMonitor());

        view.setOnUseDefaultExtractionPipelineChange(this::onUseDefaultExtractionPipeline);
        view.setOnOverrideMaxStringFieldLengthChange(view::enableMaxStringFieldLength);
    }

    private void setQueryList(final List<DashboardPane> list) {
        getView().setQueryList(list);
    }

    private String getQueryId() {
        final DashboardPane component = getView().getQuery();
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
        pipeline.setEnabled(!useDefaultExtractionPipeline);
    }

    /// Ticking Use Default Extraction Pipeline puts the data source's default pipeline in
    /// Extraction Pipeline, so that the table uses it straight away rather than only once its
    /// query's data source changes.
    ///
    /// (package-private for its tests).
    ///
    /// @param useDefaultExtractionPipeline Whether the box is now ticked.
    void onUseDefaultExtractionPipeline(final boolean useDefaultExtractionPipeline) {
        pipeline.setEnabled(!useDefaultExtractionPipeline);
        if (useDefaultExtractionPipeline && dataSourceRef != null) {
            dataSourceClient.fetchDefaultExtractionPipeline(dataSourceRef, guard(defaultPipeline -> {
                // Still ticked, and the data source has a default
                if (defaultPipeline != null && useDefaultExtractionPipeline()) {
                    setPipeline(defaultPipeline);
                }
            }), taskMonitor());
        }
    }

    private DocRef getPipeline() {
        return pipeline.getSelectedEntityReference();
    }

    private void setPipeline(final DocRef pipeline) {
        this.pipeline.setSelectedEntityReference(pipeline, true);
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
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final List<DashboardPane> list = getDashboardContext()
                .getComponents().getSortedComponentsByType(QueryPaneController.TYPE.getId());
        setQueryList(list);

        final TableComponentSettings settings = (TableComponentSettings) componentConfig.getSettings();
        dataSourceRef = settings.getDataSourceRef();
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

}
