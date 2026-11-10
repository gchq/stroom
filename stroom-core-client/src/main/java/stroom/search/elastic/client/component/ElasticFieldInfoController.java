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

package stroom.search.elastic.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.search.elastic.client.model.ElasticFieldRow;
import stroom.search.elastic.client.model.ElasticFieldRow.Kind;
import stroom.search.elastic.client.model.ElasticFieldStatsSupport;
import stroom.search.elastic.client.model.ElasticMappingEntry;
import stroom.search.elastic.client.model.ElasticMappingParameterEntries;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldStatsRequest;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexResource;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Controller;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.datepicker.client.component.DateTimeBoxController;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;

/// The Info panel of an Elastic index's Fields tab ([ElasticFieldInfo]): shows how the selected
/// field is mapped and fetches statistics of its values. Statistics are fetched only while the panel
/// shows, and a reply is dropped if the field, time range or sample size has changed since it was
/// asked for.
public class ElasticFieldInfoController extends Controller {

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
                                                 "name, so it can't be searched. Rename it in the field list.";
    static final String MESSAGE_BAD_TIME_RANGE = "Enter a valid time range whose start is before its end.";

    private final ElasticFieldInfo view;
    private final RestFactory restFactory;

    private DocRef index;
    private ElasticFieldRow row;
    private Supplier<List<ElasticVirtualFieldGroup>> virtualGroupsSupplier;
    private int sampleSize = ElasticFieldStatsSupport.DEFAULT_SAMPLE_SIZE;
    private boolean exact;
    private Integer maxSampleSize;
    private boolean active;
    private boolean statsStale = true;
    private int requestId;
    private int topValuesCount = ElasticFieldStatsSupport.DEFAULT_TOP_VALUES_COUNT;
    private String topValuesFilter = "";
    private int topValuesRequestId;

    /// Made by GIN, with its panel.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The panel.
    /// @param restFactory                   Fetches the statistics.
    /// @param dateTimeBoxControllerProvider Makes what opens each time box's dialog.
    @Inject
    public ElasticFieldInfoController(final EventBus eventBus,
                                      final ElasticFieldInfo view,
                                      final RestFactory restFactory,
                                      final Provider<DateTimeBoxController> dateTimeBoxControllerProvider) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        initWidget(view);
        for (final DateTimeBox box : view.getDateBoxes()) {
            make(dateTimeBoxControllerProvider).attach(box);
        }

        final long nowMs = System.currentTimeMillis();
        view.setTimeRange(ElasticFieldStatsSupport.getDefaultFromMs(nowMs), nowMs);
        view.setSampleSizes(ElasticFieldStatsSupport.SAMPLE_SIZES, sampleSize);
        view.setOnTimeRangeChange(this::onSettingChange);
        view.setOnSampleSizeChange(size -> {
            sampleSize = size;
            onSettingChange();
        });
        view.setOnExactChange(isExact -> {
            exact = isExact;
            onSettingChange();
        });
        view.setOnTopValuesCountChange(count -> {
            topValuesCount = count;
            loadTopValues();
        });
        view.setOnTopValuesFilterChange(this::onTopValuesFilterChange);
        view.setOnRefresh(() -> {
            statsStale = true;
            loadIfActive();
        });
    }

    /// @param index The index the fields belong to.
    public void setIndex(final DocRef index) {
        if (!Objects.equals(this.index, index)) {
            statsStale = true;
            maxSampleSize = null;
        }
        this.index = index;
    }

    /// Shows a field. Its statistics are fetched again if it isn't the field shown.
    ///
    /// @param row                   The field, or null for none.
    /// @param virtualGroupsSupplier Gives a virtual field's groups, as edited.
    public void setField(final ElasticFieldRow row,
                         final Supplier<List<ElasticVirtualFieldGroup>> virtualGroupsSupplier) {
        final boolean changed = !Objects.equals(this.row, row);
        this.row = row;
        this.virtualGroupsSupplier = virtualGroupsSupplier;
        showMapping();
        if (changed) {
            discardPendingRequests();
            // A filter typed for one field's values is unlikely to suit another's
            topValuesFilter = "";
            view.setTopValuesFilter("");
            view.clearStats();
            statsStale = true;
            loadIfActive();
        }
    }

    /// @param active Whether the panel shows, so statistics may be fetched.
    public void setActive(final boolean active) {
        this.active = active;
        loadIfActive();
    }

    private void onSettingChange() {
        statsStale = true;
        discardPendingRequests();
        loadIfActive();
    }

    // The filter waits for the user to stop typing before saying it changed, so the values are
    // fetched at once
    private void onTopValuesFilterChange(final String filter) {
        final String trimmed = NullSafe.trim(filter);
        if (!trimmed.equals(topValuesFilter)) {
            topValuesFilter = trimmed;
            loadTopValues();
        }
    }

    private void loadIfActive() {
        if (active && statsStale && row != null) {
            load();
        }
    }

    private void discardPendingRequests() {
        // Replies carry the id of their request, so moving the ids on drops any reply still to come
        requestId++;
        topValuesRequestId++;
        view.setLoading(false);
        view.setTopValuesLoading(false);
    }

    private void load() {
        requestId++;
        topValuesRequestId++;
        final int thisRequestId = requestId;
        statsStale = false;

        final String noStatsMessage = getNoStatsMessage(row);
        if (noStatsMessage != null) {
            view.setLoading(false);
            view.showStatsMessage(noStatsMessage);
            return;
        }
        // Only exact statistics are for a time range; a random sample is taken from all documents
        final Long fromMs = exact
                ? view.getFromMs()
                : null;
        final Long toMs = exact
                ? view.getToMs()
                : null;
        if (exact && (!view.isTimeRangeValid() || (fromMs != null && toMs != null && fromMs >= toMs))) {
            view.setLoading(false);
            view.showStatsMessage(MESSAGE_BAD_TIME_RANGE);
            return;
        }

        final ElasticFieldStatsRequest request = createRequest(fromMs, toMs, false);
        view.setLoading(true);
        restFactory
                .create(ELASTIC_INDEX_RESOURCE)
                .method(resource -> resource.fetchFieldStats(request))
                .onSuccess(stats -> {
                    if (thisRequestId == requestId) {
                        view.setLoading(false);
                        onStats(stats);
                    }
                })
                .onFailure(error -> {
                    if (thisRequestId == requestId) {
                        view.setLoading(false);
                        statsStale = true;
                        view.showStatsError(error.getMessage());
                    }
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    // Fetches the most common values again, after their number or filter changes, leaving the rest
    // of the statistics as they are
    private void loadTopValues() {
        if (row == null || !active || statsStale || getNoStatsMessage(row) != null) {
            // The statistics are fetched in full when next shown, with the new choice
            return;
        }
        topValuesRequestId++;
        final int thisRequestId = topValuesRequestId;
        final Long fromMs = exact
                ? view.getFromMs()
                : null;
        final Long toMs = exact
                ? view.getToMs()
                : null;
        final ElasticFieldStatsRequest request = createRequest(fromMs, toMs, true);

        view.setTopValuesLoading(true);
        restFactory
                .create(ELASTIC_INDEX_RESOURCE)
                .method(resource -> resource.fetchFieldStats(request))
                .onSuccess(stats -> {
                    if (thisRequestId == topValuesRequestId) {
                        view.setTopValuesLoading(false);
                        if (stats != null && NullSafe.isBlankString(stats.getMessage())) {
                            view.showTopValues(stats.getTopValues());
                        }
                    }
                })
                .onFailure(error -> {
                    if (thisRequestId == topValuesRequestId) {
                        view.setTopValuesLoading(false);
                        view.showTopValuesError(error.getMessage());
                    }
                })
                .owner(this)
                .taskMonitorFactory(view.getTaskMonitor())
                .exec();
    }

    private ElasticFieldStatsRequest createRequest(final Long fromMs, final Long toMs, final boolean topValuesOnly) {
        final List<ElasticVirtualFieldGroup> virtualGroups = row.isVirtual() && virtualGroupsSupplier != null
                ? NullSafe.list(virtualGroupsSupplier.get())
                : null;
        return new ElasticFieldStatsRequest(index,
                row.getName(),
                sampleSize,
                fromMs,
                toMs,
                virtualGroups,
                exact,
                topValuesCount,
                topValuesFilter,
                topValuesOnly);
    }

    private void onStats(final ElasticFieldStats stats) {
        if (stats == null) {
            view.clearStats();
            return;
        }
        applyMaxSampleSize(stats.getMaxSampleSize());
        if (NullSafe.isNonBlankString(stats.getMessage())) {
            view.showStatsMessage(stats.getMessage());
        } else {
            view.showStats(stats);
        }
    }

    private void applyMaxSampleSize(final Integer maxSampleSize) {
        if (maxSampleSize != null && !maxSampleSize.equals(this.maxSampleSize)) {
            this.maxSampleSize = maxSampleSize;
            // Only offer the sample sizes the server allows; a larger one would be cut down anyway
            sampleSize = ElasticFieldStatsSupport.clampSampleSize(sampleSize, maxSampleSize);
            view.setSampleSizes(ElasticFieldStatsSupport.getAllowedSampleSizes(maxSampleSize), sampleSize);
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
            view.setMapping(List.of(), null);
        } else if (row.getKind() == Kind.MISSING) {
            view.setMapping(List.of(), MESSAGE_MISSING_MAPPING);
        } else if (row.isVirtual()) {
            view.setMapping(List.of(), MESSAGE_VIRTUAL_MAPPING);
        } else {
            view.setMapping(createMappingEntries(row.getField()), null);
        }
    }

    private static List<ElasticMappingEntry> createMappingEntries(final ElasticIndexField field) {
        final List<ElasticMappingEntry> entries = new ArrayList<>();
        if (field.hasTypeConflict()) {
            // By index name, so the list reads the same each time
            for (final Map.Entry<String, String> entry : new TreeMap<>(field.getConflictingTypes()).entrySet()) {
                entries.add(new ElasticMappingEntry("Type in " + entry.getKey(), entry.getValue()));
            }
        } else {
            entries.add(new ElasticMappingEntry("Native type", NullSafe.string(field.getNativeType())));
        }
        if (field.getMultiFieldParent() != null) {
            entries.add(new ElasticMappingEntry("Multi-field of", field.getMultiFieldParent()));
        }
        if (NullSafe.hasItems(field.getSourceIndices())) {
            entries.add(new ElasticMappingEntry("Source indices", field.getSourceIndices()));
        }
        entries.addAll(ElasticMappingParameterEntries.create(field.getMappingParameters()));
        return entries;
    }
}
