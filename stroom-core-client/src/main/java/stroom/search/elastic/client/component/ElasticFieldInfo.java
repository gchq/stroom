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

import stroom.search.elastic.client.model.ElasticFieldStatsSupport;
import stroom.search.elastic.client.model.ElasticMappingEntry;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldValueCount;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.ToolbarButton;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.CopyTextUtil;
import stroom.widget.datepicker.client.component.DateTimeBox;
import stroom.widget.form.client.AccessibleName;
import stroom.widget.form.client.FormGroup;
import stroom.widget.quickfilter.client.QuickFilter;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.spinner.client.SmallBusySpinner;
import stroom.widget.task.client.TaskMonitorFactory;
import stroom.widget.text.client.Html;
import stroom.widget.text.client.Label;
import stroom.widget.util.client.CssProperties;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/// The Info panel of an Elastic index's Fields tab: how the selected field is mapped, then
/// statistics of its values, estimated from a random sample or exact for a time range, drawn with
/// bars for coverage, the most common values and the distribution of values. Its controller
/// ([ElasticFieldInfoController]) fetches the statistics while the panel shows.
public class ElasticFieldInfo extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private static final String TOP_VALUES_HELP = "The values held by the most documents. Each bar shows the " +
                                                  "percentage of all documents that hold the value, and the " +
                                                  "count beside it is the number of documents. A document " +
                                                  "holding several values is counted for each of them.";
    private static final String DISTRIBUTION_HELP = "How the values are spread between the smallest and largest " +
                                                    "value, in up to ten ranges. Each bar shows the percentage of " +
                                                    "all documents with a value in that range, and the count " +
                                                    "beside it is the number of documents. Each range includes " +
                                                    "its start but not its end, except the last, which includes " +
                                                    "the largest value.";
    private static final String SAMPLED_HELP = " Both are estimated from the random sample.";
    private static final String TOP_VALUES_FILTER_HELP = "Only values containing the text entered, ignoring " +
                                                         "case, are shown.";
    // Labels longer than this need the wider label column, e.g. a range of timestamps
    private static final int WIDE_LABEL_LENGTH = 30;
    private static final String MODE_RANDOM = "Random sample";
    private static final String MODE_EXACT = "Exact (all documents)";
    private static final String BAR_ROW = "elastic-field-info__bar-row";
    private static final String BAR_ROW_WIDE = "elastic-field-info__bar-row--wide";
    private static final String PERCENT_WIDTH = "--percent-bar-cell__width";

    @UiField
    Label mappingMessage;
    @UiField
    FlowPanel mappingEntries;
    @UiField
    ToolbarButton refreshStats;
    @UiField
    SmallBusySpinner spinner;
    @UiField
    Label loading;
    @UiField
    SelectionBox<Boolean> mode;
    @UiField
    FlowPanel randomControls;
    @UiField
    SelectionBox<Integer> sampleSize;
    @UiField
    FlowPanel exactControls;
    @UiField
    DateTimeBox fromTime;
    @UiField
    DateTimeBox toTime;
    @UiField
    Label statsMessage;
    @UiField
    FlowPanel errorPanel;
    @UiField
    Label errorText;
    @UiField
    Button retry;
    @UiField
    FlowPanel statsContent;

    // The most common values' controls and list, kept between showings of the statistics, so the
    // filter keeps its text and the focus while the user types
    private final FlowPanel topValuesControls = new FlowPanel();
    private final SelectionBox<Integer> topValuesCount = new SelectionBox<>();
    private final QuickFilter topValuesFilter = new QuickFilter();
    private final SmallBusySpinner topValuesSpinner = new SmallBusySpinner();
    private final Label topValuesError = new Label();
    private final FlowPanel topValuesList = new FlowPanel();
    private long topValuesTotalDocs;

    private Runnable onTimeRangeChange = () -> {
    };
    private IntConsumer onSampleSizeChange = size -> {
    };
    private Consumer<Boolean> onExactChange = exact -> {
    };
    private IntConsumer onTopValuesCountChange = count -> {
    };
    private Consumer<String> onTopValuesFilterChange = filter -> {
    };
    private Runnable onRefresh = () -> {
    };

    /// Makes an empty panel.
    public ElasticFieldInfo() {
        initWidget(BINDER.createAndBindUi(this));
        fromTime.setOptional(true);
        toTime.setOptional(true);
        sampleSize.setDisplayValueFunction(size -> ElasticFieldStatsSupport.formatCount(size.longValue()));
        mode.setDisplayValueFunction(exact -> Boolean.TRUE.equals(exact)
                ? MODE_EXACT
                : MODE_RANDOM);
        mode.addItems(List.of(false, true));
        mode.setValue(false);
        createTopValuesControls();

        fromTime.setOnChange(() -> onTimeRangeChange.run());
        toTime.setOnChange(() -> onTimeRangeChange.run());
        mode.addValueChangeHandler(e -> {
            final boolean exact = Boolean.TRUE.equals(mode.getValue());
            // A random sample is taken from all documents; exact statistics are for a time range
            randomControls.setVisible(!exact);
            exactControls.setVisible(exact);
            onExactChange.accept(exact);
        });
        sampleSize.addValueChangeHandler(e -> {
            if (sampleSize.getValue() != null) {
                onSampleSizeChange.accept(sampleSize.getValue());
            }
        });
        refreshStats.setOnClick(() -> onRefresh.run());
        retry.setOnClick(() -> onRefresh.run());
    }

    private void createTopValuesControls() {
        topValuesControls.setStyleName("elastic-field-info__top-values-controls");
        topValuesCount.addStyleName("elastic-field-info__top-values-count");
        topValuesCount.setDisplayValueFunction(count -> "Top " + count);
        topValuesCount.addItems(ElasticFieldStatsSupport.TOP_VALUES_COUNTS);
        topValuesCount.setValue(ElasticFieldStatsSupport.DEFAULT_TOP_VALUES_COUNT);
        AccessibleName.set(topValuesCount, "Number of values");
        topValuesCount.addValueChangeHandler(e -> {
            if (topValuesCount.getValue() != null) {
                onTopValuesCountChange.accept(topValuesCount.getValue());
            }
        });
        // A quick filter, so it clears with its clear button or Escape as the other filters do
        topValuesFilter.addStyleName("elastic-field-info__top-values-filter");
        topValuesFilter.setPlaceholder("Filter values");
        topValuesFilter.setAccessibleName("Filter values");
        topValuesFilter.registerPopupTextProvider(() -> HtmlBuilder.builder()
                .bold("Filter Values")
                .br()
                .append(TOP_VALUES_FILTER_HELP)
                .toSafeHtml());
        topValuesFilter.setOnChange(text -> onTopValuesFilterChange.accept(text));
        topValuesError.addStyleName("elastic-field-info__error");
        topValuesError.setVisible(false);
        topValuesControls.add(topValuesCount);
        topValuesControls.add(topValuesFilter);
        topValuesControls.add(topValuesSpinner);
        topValuesControls.add(topValuesError);
    }

    /// @return The time range's boxes, whose dialogs the controller opens.
    public List<DateTimeBox> getDateBoxes() {
        return List.of(fromTime, toTime);
    }

    /// @param fromMs The start of the time range, in epoch milliseconds, or null for none.
    /// @param toMs   Its end, or null for none.
    public void setTimeRange(final Long fromMs, final Long toMs) {
        fromTime.setValue(fromMs);
        toTime.setValue(toMs);
    }

    /// @return The start of the time range, in epoch milliseconds, or null for none.
    public Long getFromMs() {
        return fromTime.getValue();
    }

    /// @return The end of the time range, in epoch milliseconds, or null for none.
    public Long getToMs() {
        return toTime.getValue();
    }

    /// @return Whether the time range's boxes are blank or hold times.
    public boolean isTimeRangeValid() {
        return fromTime.getInvalidReason() == null && toTime.getInvalidReason() == null;
    }

    /// @param sampleSizes The sample sizes to offer.
    /// @param selected    The one to choose.
    public void setSampleSizes(final List<Integer> sampleSizes, final int selected) {
        sampleSize.clear();
        sampleSize.addItems(sampleSizes);
        sampleSize.setValue(selected);
    }

    /// Shows how the field is mapped.
    ///
    /// @param entries Its mapping's details.
    /// @param message Text shown in their place, e.g. for a virtual field, or null.
    public void setMapping(final List<ElasticMappingEntry> entries, final String message) {
        mappingEntries.clear();
        mappingMessage.setText(NullSafe.string(message));
        mappingMessage.setVisible(NullSafe.isNonBlankString(message));
        for (final ElasticMappingEntry entry : NullSafe.list(entries)) {
            if (entry.getValues() != null) {
                addListEntry(mappingEntries, entry.getName(), entry.getValues());
            } else {
                addEntry(mappingEntries, entry.getName(), entry.getValue());
            }
        }
        mappingEntries.setVisible(NullSafe.hasItems(entries));
    }

    /// @param loading Whether statistics are being fetched, which clears those shown.
    public void setLoading(final boolean loading) {
        this.loading.setVisible(loading);
        if (loading) {
            clearStats();
        }
    }

    /// Clears the statistics, message or error shown.
    public void clearStats() {
        statsMessage.setVisible(false);
        errorPanel.setVisible(false);
        statsContent.clear();
    }

    /// @param message Why no statistics are shown.
    public void showStatsMessage(final String message) {
        clearStats();
        statsMessage.setText(message);
        statsMessage.setVisible(true);
    }

    /// @param error Why the statistics couldn't be fetched, shown with Retry.
    public void showStatsError(final String error) {
        clearStats();
        errorText.setText("Unable to fetch statistics: " + NullSafe.string(error));
        errorPanel.setVisible(true);
    }

    /// Shows a field's statistics.
    ///
    /// @param stats The statistics.
    public void showStats(final ElasticFieldStats stats) {
        clearStats();
        // Counts are for every document in the time range: exact, or estimated from the sample
        final long totalDocs = Objects.requireNonNullElse(stats.getTotalDocs(), 0L);
        final long docsWithValue = Objects.requireNonNullElse(stats.getDocsWithValue(), 0L);
        final String sampledHelp = Boolean.TRUE.equals(stats.getExact())
                ? ""
                : SAMPLED_HELP;

        final FlowPanel coverage = new FlowPanel();
        coverage.add(createBarRow("Documents with a value", false,
                ElasticFieldStatsSupport.getPercent(docsWithValue, totalDocs),
                countText(docsWithValue, totalDocs)));
        statsContent.add(createBlock("Coverage", null, coverage));

        final FlowPanel values = new FlowPanel();
        values.setStyleName("elastic-field-info__entries");
        if (stats.getDistinctCount() != null) {
            final String approximate = Boolean.TRUE.equals(stats.getDistinctCountExact())
                    ? ""
                    : " (approximate)";
            addEntry(values, "Distinct values",
                    ElasticFieldStatsSupport.formatCount(stats.getDistinctCount()) + approximate);
        }
        addEntryIfSet(values, "Minimum", stats.getMin());
        addEntryIfSet(values, "Maximum", stats.getMax());
        addEntryIfSet(values, "Mean", ElasticFieldStatsSupport.formatDecimal(stats.getMean()));
        addEntryIfSet(values, "Median", ElasticFieldStatsSupport.formatDecimal(stats.getMedian()));
        if (values.getWidgetCount() > 0) {
            statsContent.add(values);
        }

        if (stats.getTopValues() != null) {
            topValuesTotalDocs = totalDocs;
            final FlowPanel topValues = new FlowPanel();
            topValues.add(topValuesControls);
            topValues.add(topValuesList);
            showTopValues(stats.getTopValues());
            statsContent.add(createBlock("Most common values", TOP_VALUES_HELP + sampledHelp, topValues));
        }

        if (NullSafe.hasItems(stats.getHistogram())) {
            final FlowPanel histogram = new FlowPanel();
            // Ranges of timestamps are long, so need a wider label column to show in full
            int longestLabel = 0;
            for (final ElasticFieldValueCount bucket : stats.getHistogram()) {
                longestLabel = Math.max(longestLabel, NullSafe.string(bucket.getValue()).length());
            }
            final boolean wideLabels = longestLabel > WIDE_LABEL_LENGTH;
            for (final ElasticFieldValueCount bucket : stats.getHistogram()) {
                final long count = Objects.requireNonNullElse(bucket.getCount(), 0L);
                final Widget row = createBarRow(bucket.getValue(), false,
                        ElasticFieldStatsSupport.getPercent(count, totalDocs),
                        countText(count, totalDocs));
                row.setStyleName(BAR_ROW_WIDE, wideLabels);
                histogram.add(row);
            }
            statsContent.add(createBlock("Distribution", DISTRIBUTION_HELP + sampledHelp, histogram));
        }
    }

    /// Shows the most common values in place of those shown, e.g. after their number or filter
    /// changed.
    ///
    /// @param topValues The values.
    public void showTopValues(final List<ElasticFieldValueCount> topValues) {
        topValuesList.clear();
        if (NullSafe.isEmptyCollection(topValues)) {
            topValuesList.add(new Label(NullSafe.isBlankString(topValuesFilter.getText())
                    ? "No values."
                    : "No values match the filter."));
        } else {
            for (final ElasticFieldValueCount valueCount : topValues) {
                final long count = Objects.requireNonNullElse(valueCount.getCount(), 0L);
                topValuesList.add(createBarRow(valueCount.getValue(), true,
                        ElasticFieldStatsSupport.getPercent(count, topValuesTotalDocs),
                        countText(count, topValuesTotalDocs)));
            }
        }
    }

    /// @param loading Whether the most common values are being fetched again.
    public void setTopValuesLoading(final boolean loading) {
        topValuesSpinner.setRefreshing(loading);
        if (loading) {
            topValuesError.setVisible(false);
        }
    }

    /// @param error Why the most common values couldn't be fetched.
    public void showTopValuesError(final String error) {
        topValuesError.setText("Unable to fetch the values: " + NullSafe.string(error));
        topValuesError.setVisible(true);
    }

    /// Sets the most common values' filter, without the filter change callback being told.
    ///
    /// @param filter The filter's text.
    public void setTopValuesFilter(final String filter) {
        topValuesFilter.setText(NullSafe.string(filter), false);
    }

    /// @return The task monitor that spins while statistics are fetched.
    public TaskMonitorFactory getTaskMonitor() {
        return spinner;
    }

    /// @param onTimeRangeChange Run when the user changes the time range.
    public void setOnTimeRangeChange(final Runnable onTimeRangeChange) {
        this.onTimeRangeChange = Objects.requireNonNull(onTimeRangeChange, "onTimeRangeChange");
    }

    /// @param onSampleSizeChange Given the sample size the user chooses.
    public void setOnSampleSizeChange(final IntConsumer onSampleSizeChange) {
        this.onSampleSizeChange = Objects.requireNonNull(onSampleSizeChange, "onSampleSizeChange");
    }

    /// @param onExactChange Given whether the user chooses exact statistics.
    public void setOnExactChange(final Consumer<Boolean> onExactChange) {
        this.onExactChange = Objects.requireNonNull(onExactChange, "onExactChange");
    }

    /// @param onTopValuesCountChange Given how many of the most common values the user chooses.
    public void setOnTopValuesCountChange(final IntConsumer onTopValuesCountChange) {
        this.onTopValuesCountChange = Objects.requireNonNull(onTopValuesCountChange, "onTopValuesCountChange");
    }

    /// @param onTopValuesFilterChange Given the most common values' filter when it changes.
    public void setOnTopValuesFilterChange(final Consumer<String> onTopValuesFilterChange) {
        this.onTopValuesFilterChange = Objects.requireNonNull(onTopValuesFilterChange, "onTopValuesFilterChange");
    }

    /// @param onRefresh Run when Refresh Statistics or Retry is pressed.
    public void setOnRefresh(final Runnable onRefresh) {
        this.onRefresh = Objects.requireNonNull(onRefresh, "onRefresh");
    }

    private static String countText(final long count, final long total) {
        return ElasticFieldStatsSupport.formatCount(count) + " (" +
               ElasticFieldStatsSupport.formatPercent(count, total) + ")";
    }

    // A block of the statistics under its label, with help if given
    private static FormGroup createBlock(final String heading, final String helpText, final Widget content) {
        final FormGroup block = new FormGroup();
        block.setLabel(heading);
        if (helpText != null) {
            block.setHelpText(helpText);
        }
        block.add(content);
        return block;
    }

    // A labelled bar as wide as a percentage, with a count after it
    private static Widget createBarRow(final String label,
                                       final boolean copyable,
                                       final double percent,
                                       final String countText) {
        final FlowPanel row = new FlowPanel();
        row.setStyleName(BAR_ROW);

        final Widget labelWidget = copyable
                ? createCopyableText(label)
                : new Label(NullSafe.string(label));
        labelWidget.addStyleName("elastic-field-info__bar-label");
        labelWidget.setTitle(NullSafe.string(label));
        row.add(labelWidget);

        // The percentage bar of a grid's cells (percent-bar-cell.css); the count after it says the
        // same, so it is only drawn
        final FlowPanel bar = new FlowPanel();
        bar.setStyleName("percent-bar-cell__bar percent-bar-cell__bar--healthy");
        CssProperties.set(bar.getElement(), PERCENT_WIDTH, Math.max(0, Math.min(100, percent)) + "%");
        final FlowPanel track = new FlowPanel();
        track.setStyleName("percent-bar-cell elastic-field-info__bar");
        track.getElement().setAttribute("aria-hidden", "true");
        track.add(bar);
        row.add(track);

        final Label count = new Label(countText);
        count.addStyleName("elastic-field-info__bar-count");
        row.add(count);
        return row;
    }

    private static void addEntryIfSet(final FlowPanel panel, final String name, final String value) {
        if (NullSafe.isNonBlankString(value)) {
            addEntry(panel, name, value);
        }
    }

    private static void addEntry(final FlowPanel panel, final String name, final String value) {
        final Label nameLabel = new Label(name);
        nameLabel.addStyleName("elastic-field-info__entry-name");
        final Label valueLabel = new Label(NullSafe.string(value));
        valueLabel.addStyleName("elastic-field-info__entry-value");
        panel.add(nameLabel);
        panel.add(valueLabel);
    }

    private static void addListEntry(final FlowPanel panel, final String name, final List<String> values) {
        final Label nameLabel = new Label(name);
        nameLabel.addStyleName("elastic-field-info__entry-name");
        final FlowPanel valuesPanel = new FlowPanel();
        valuesPanel.setStyleName("elastic-field-info__entry-value elastic-field-info__entry-value--list");
        for (final String value : values) {
            valuesPanel.add(createCopyableText(value));
        }
        panel.add(nameLabel);
        panel.add(valuesPanel);
    }

    // The text, with a button to copy it that shows while the mouse is over it
    private static Widget createCopyableText(final String text) {
        final Html html = new Html(CopyTextUtil.render(text, false));
        html.addDomHandler(event -> CopyTextUtil.onClick(event.getNativeEvent()), MouseDownEvent.getType());
        return html;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ElasticFieldInfo> {

    }
}
