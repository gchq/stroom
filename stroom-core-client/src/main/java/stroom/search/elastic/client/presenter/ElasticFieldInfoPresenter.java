/*
 * Copyright 2026 Crown Copyright
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

package stroom.search.elastic.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.search.elastic.client.presenter.ElasticFieldInfoPresenter.ElasticFieldInfoView;
import stroom.search.elastic.client.presenter.ElasticFieldRow.Kind;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldStatsRequest;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.datepicker.client.DateTimePopup;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

/// The Info tab of the Elasticsearch index Fields tab, showing how the selected field is mapped and statistics
/// sampled from its values.
///
/// Statistics are only fetched while the tab is showing. A response is dropped if the field, time range or sample
/// size has changed since its request was sent.
public class ElasticFieldInfoPresenter
        extends MyPresenterWidget<ElasticFieldInfoView>
        implements ElasticFieldInfoUiHandlers {

    private static final ElasticIndexResource ELASTIC_INDEX_RESOURCE = GWT.create(ElasticIndexResource.class);

    static final String MESSAGE_VIRTUAL_MAPPING = "Virtual fields are defined in Stroom, not in the index mapping. " +
                                                  "Their values are the names of their groups.";
    static final String MESSAGE_MISSING_MAPPING = "No source index maps this field. Its settings are kept until " +
                                                  "you remove them.";
    static final String MESSAGE_MISSING_STATS = "This field is not in any source index, so it has no values to " +
                                                "sample.";
    static final String MESSAGE_CONFLICT_STATS = "The source indices map this field to different types, so its " +
                                                 "values can't be sampled together.";
    static final String MESSAGE_CLASHING_STATS = "This virtual field is hidden by the mapped field of the same " +
                                                 "name, so it can't be searched. Rename it on the Settings tab.";
    static final String MESSAGE_BAD_TIME_RANGE = "Enter a valid time range whose start is before its end.";

    private final RestFactory restFactory;

    private DocRef indexDocRef;
    private ElasticFieldRow row;
    private Supplier<List<ElasticVirtualFieldGroup>> virtualGroupsSupplier;
    private int sampleSize = ElasticFieldStatsSupport.DEFAULT_SAMPLE_SIZE;
    private boolean exact;
    private Integer maxSampleSize;
    private boolean active;
    private boolean statsStale = true;
    private int requestId;

    @Inject
    public ElasticFieldInfoPresenter(final EventBus eventBus,
                                     final ElasticFieldInfoView view,
                                     final RestFactory restFactory,
                                     final Provider<DateTimePopup> dateTimePopupProvider) {
        super(eventBus, view);
        this.restFactory = restFactory;

        view.setUiHandlers(this);
        view.setDateTimePopupProvider(dateTimePopupProvider);
        final long nowMs = System.currentTimeMillis();
        view.setTimeRange(ElasticFieldStatsSupport.getDefaultFromMs(nowMs), nowMs);
        view.setSampleSizes(ElasticFieldStatsSupport.SAMPLE_SIZES, sampleSize);
    }

    /// @param indexDocRef the index doc the fields belong to
    /// @param timeField   the name of the index's time field, which the time range filters on, or `null`
    public void setIndex(final DocRef indexDocRef, final String timeField) {
        if (!Objects.equals(this.indexDocRef, indexDocRef)) {
            statsStale = true;
            maxSampleSize = null;
        }
        this.indexDocRef = indexDocRef;
        getView().setTimeFieldName(timeField);
    }

    /// Shows a field. Statistics are fetched again if the field is not the one already shown.
    ///
    /// @param row                   the field to show, or `null` for none
    /// @param virtualGroupsSupplier supplies the groups of a virtual field as currently edited
    public void setField(final ElasticFieldRow row,
                         final Supplier<List<ElasticVirtualFieldGroup>> virtualGroupsSupplier) {
        final boolean changed = !Objects.equals(this.row, row);
        this.row = row;
        this.virtualGroupsSupplier = virtualGroupsSupplier;
        showMapping();
        if (changed) {
            discardPendingRequest();
            getView().clearStats();
            statsStale = true;
            loadIfActive();
        }
    }

    /// @param active `true` when the tab is showing, so statistics may be fetched
    public void setActive(final boolean active) {
        this.active = active;
        loadIfActive();
    }

    @Override
    public void onTimeRangeChange() {
        statsStale = true;
        discardPendingRequest();
        loadIfActive();
    }

    @Override
    public void onSampleSizeChange(final int sampleSize) {
        this.sampleSize = sampleSize;
        statsStale = true;
        discardPendingRequest();
        loadIfActive();
    }

    @Override
    public void onExactChange(final boolean exact) {
        this.exact = exact;
        statsStale = true;
        discardPendingRequest();
        loadIfActive();
    }

    @Override
    public void onRefreshStats() {
        statsStale = true;
        loadIfActive();
    }

    private void loadIfActive() {
        if (active && statsStale && row != null) {
            load();
        }
    }

    private void discardPendingRequest() {
        // Responses carry the id of their request, so moving the id on drops any response still to come
        requestId++;
        getView().setLoading(false);
    }

    private void load() {
        requestId++;
        final int thisRequestId = requestId;
        statsStale = false;

        final String noStatsMessage = getNoStatsMessage(row);
        if (noStatsMessage != null) {
            getView().setLoading(false);
            getView().showStatsMessage(noStatsMessage);
            return;
        }
        // Only exact statistics are limited to a time range; a random sample is taken from all documents
        final Long fromMs = exact
                ? getView().getFromMs()
                : null;
        final Long toMs = exact
                ? getView().getToMs()
                : null;
        if (exact && (!getView().isTimeRangeValid() || (fromMs != null && toMs != null && fromMs >= toMs))) {
            getView().setLoading(false);
            getView().showStatsMessage(MESSAGE_BAD_TIME_RANGE);
            return;
        }

        final List<ElasticVirtualFieldGroup> virtualGroups = row.isVirtual() && virtualGroupsSupplier != null
                ? NullSafe.list(virtualGroupsSupplier.get())
                : null;
        final ElasticFieldStatsRequest request = new ElasticFieldStatsRequest(indexDocRef,
                row.getName(),
                sampleSize,
                fromMs,
                toMs,
                virtualGroups,
                exact);

        getView().setLoading(true);
        restFactory
                .create(ELASTIC_INDEX_RESOURCE)
                .method(resource -> resource.fetchFieldStats(request))
                .onSuccess(stats -> {
                    if (thisRequestId == requestId) {
                        getView().setLoading(false);
                        onStats(stats);
                    }
                })
                .onFailure(error -> {
                    if (thisRequestId == requestId) {
                        getView().setLoading(false);
                        statsStale = true;
                        getView().showStatsError(error.getMessage());
                    }
                })
                .taskMonitorFactory(getView().getStatsTaskMonitorFactory())
                .exec();
    }

    private void onStats(final ElasticFieldStats stats) {
        if (stats == null) {
            getView().clearStats();
            return;
        }
        applyMaxSampleSize(stats.getMaxSampleSize());
        if (NullSafe.isNonBlankString(stats.getMessage())) {
            getView().showStatsMessage(stats.getMessage());
        } else {
            getView().showStats(stats);
        }
    }

    private void applyMaxSampleSize(final Integer maxSampleSize) {
        if (maxSampleSize != null && !maxSampleSize.equals(this.maxSampleSize)) {
            this.maxSampleSize = maxSampleSize;
            // Only offer sample sizes the server allows; a later request with a larger one would be cut down anyway
            sampleSize = ElasticFieldStatsSupport.clampSampleSize(sampleSize, maxSampleSize);
            getView().setSampleSizes(ElasticFieldStatsSupport.getAllowedSampleSizes(maxSampleSize), sampleSize);
        }
    }

    private static String getNoStatsMessage(final ElasticFieldRow row) {
        return switch (row.getKind()) {
            case MISSING -> MESSAGE_MISSING_STATS;
            case CLASHING_VIRTUAL -> MESSAGE_CLASHING_STATS;
            case MAPPED -> row.hasTypeConflict()
                    ? MESSAGE_CONFLICT_STATS
                    : null;
            default -> null;
        };
    }

    private void showMapping() {
        if (row == null) {
            getView().setMapping(List.of(), null);
        } else if (row.getKind() == Kind.MISSING) {
            getView().setMapping(List.of(), MESSAGE_MISSING_MAPPING);
        } else if (row.isVirtual()) {
            getView().setMapping(List.of(), MESSAGE_VIRTUAL_MAPPING);
        } else {
            getView().setMapping(createMappingEntries(row.getField()), null);
        }
    }

    private static List<MappingEntry> createMappingEntries(final ElasticIndexField field) {
        final List<MappingEntry> entries = new ArrayList<>();
        if (field.hasTypeConflict()) {
            // Sorted by index name so the list reads the same each time
            for (final Map.Entry<String, String> entry : new TreeMap<>(field.getConflictingTypes()).entrySet()) {
                entries.add(new MappingEntry("Type in " + entry.getKey(), entry.getValue()));
            }
        } else {
            entries.add(new MappingEntry("Native type", NullSafe.string(field.getNativeType())));
        }
        if (field.getMultiFieldParent() != null) {
            entries.add(new MappingEntry("Multi-field of", field.getMultiFieldParent()));
        }
        if (NullSafe.hasItems(field.getSourceIndices())) {
            entries.add(new MappingEntry("Source indices", field.getSourceIndices()));
        }
        if (field.getMappingParameters() != null) {
            for (final Map.Entry<String, String> entry : new TreeMap<>(field.getMappingParameters()).entrySet()) {
                entries.add(new MappingEntry(entry.getKey(), entry.getValue()));
            }
        }
        return entries;
    }

    /// The view of the Info tab.
    public interface ElasticFieldInfoView extends View, HasUiHandlers<ElasticFieldInfoUiHandlers> {

        /// @param dateTimePopupProvider provides the popup used to pick the time range
        void setDateTimePopupProvider(Provider<DateTimePopup> dateTimePopupProvider);

        /// @param fromMs the start of the time range in epoch milliseconds, or `null` for none
        /// @param toMs   the end of the time range in epoch milliseconds, or `null` for none
        void setTimeRange(Long fromMs, Long toMs);

        /// @return the start of the time range in epoch milliseconds, or `null` for none
        Long getFromMs();

        /// @return the end of the time range in epoch milliseconds, or `null` for none
        Long getToMs();

        /// @return `true` if the time range boxes are empty or hold valid date-times
        boolean isTimeRangeValid();

        /// @param timeField the name of the index's time field, or `null`
        void setTimeFieldName(String timeField);

        /// @param sampleSizes the approximate sample sizes to offer
        /// @param selected    the sample size to select
        void setSampleSizes(List<Integer> sampleSizes, int selected);

        /// @param entries the mapping details of the field
        /// @param message text shown in place of the details, or `null`
        void setMapping(List<MappingEntry> entries, String message);

        /// @param loading `true` while a statistics request is running
        void setLoading(boolean loading);

        /// Removes any statistics, message or error shown.
        void clearStats();

        /// @param message text explaining why no statistics are shown
        void showStatsMessage(String message);

        /// @param error the error from a failed statistics request; a retry action is shown with it
        void showStatsError(String error);

        /// @param stats the statistics to show
        void showStats(ElasticFieldStats stats);

        /// @return the task monitor factory that shows when a statistics request is running
        TaskMonitorFactory getStatsTaskMonitorFactory();
    }

    /// One name and value of a field's mapping details.
    public static final class MappingEntry {

        private final String name;
        private final String value;
        private final List<String> values;

        /// @param name  the name of the detail, e.g. a mapping parameter name
        /// @param value the value of the detail
        public MappingEntry(final String name, final String value) {
            this.name = name;
            this.value = value;
            this.values = null;
        }

        /// @param name   the name of the detail
        /// @param values the values of the detail, shown as a list with a copy button for each
        public MappingEntry(final String name, final List<String> values) {
            this.name = name;
            this.value = null;
            this.values = values;
        }

        /// @return the name of the detail
        public String getName() {
            return name;
        }

        /// @return the value of the detail, or `null` if it has a list of values
        public String getValue() {
            return value;
        }

        /// @return the values of the detail, or `null` if it has a single value
        public List<String> getValues() {
            return values;
        }
    }
}
