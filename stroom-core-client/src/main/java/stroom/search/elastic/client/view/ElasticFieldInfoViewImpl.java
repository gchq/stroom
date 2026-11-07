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

package stroom.search.elastic.client.view;

import stroom.data.client.presenter.CopyTextUtil;
import stroom.item.client.SelectionBox;
import stroom.search.elastic.client.presenter.ElasticFieldInfoPresenter.ElasticFieldInfoView;
import stroom.search.elastic.client.presenter.ElasticFieldInfoPresenter.MappingEntry;
import stroom.search.elastic.client.presenter.ElasticFieldInfoUiHandlers;
import stroom.search.elastic.client.presenter.ElasticFieldStatsSupport;
import stroom.search.elastic.shared.ElasticFieldStats;
import stroom.search.elastic.shared.ElasticFieldValueCount;
import stroom.svg.shared.SvgImage;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.NullSafe;
import stroom.widget.button.client.Button;
import stroom.widget.button.client.InlineSvgButton;
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.dropdowntree.client.view.QuickFilter;
import stroom.widget.help.client.HelpButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import java.util.List;
import java.util.Objects;

/// The view of the Info tab of the Elasticsearch index Fields tab. Statistics are drawn with plain widgets, using
/// sized bars for coverage, common values and the distribution of values.
public class ElasticFieldInfoViewImpl
        extends ViewWithUiHandlers<ElasticFieldInfoUiHandlers>
        implements ElasticFieldInfoView {

    private static final String MAPPING_HELP = "How Elasticsearch maps this field. Parameters shown are those set " +
                                               "in the index mapping; change them in Elasticsearch, not Stroom.";

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
    private static final String TOP_VALUES_FILTER_HELP = "Only values containing the characters input, ignoring " +
                                                         "case, will be included.";
    // Labels longer than this need the wider label column
    private static final int WIDE_LABEL_LENGTH = 30;
    private static final String MODE_RANDOM = "Random sample";
    private static final String MODE_EXACT = "Exact (all documents)";

    private final Widget widget;

    @UiField
    Style style;
    @UiField
    FlowPanel mappingHeader;
    @UiField
    Label mappingMessage;
    @UiField
    FlowPanel mappingEntries;
    @UiField
    DateTimeBox fromTime;
    @UiField
    DateTimeBox toTime;
    @UiField
    SelectionBox<Boolean> mode;
    @UiField
    SelectionBox<Integer> sampleSize;
    @UiField
    InlineSvgButton refreshStats;

    // The controls and list of the most common values, kept between renders of the statistics
    private final FlowPanel topValuesControls = new FlowPanel();
    private final SelectionBox<Integer> topValuesCount = new SelectionBox<>();
    private final QuickFilter topValuesFilter = new QuickFilter();
    private final SpinnerSmall topValuesSpinner = new SpinnerSmall();
    private final Label topValuesError = new Label();
    private final FlowPanel topValuesList = new FlowPanel();
    private long topValuesTotalDocs;
    @UiField
    FlowPanel randomControls;
    @UiField
    FlowPanel exactControls;
    @UiField
    SpinnerSmall spinner;
    @UiField
    Label loadingLabel;
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

    @Inject
    public ElasticFieldInfoViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        final HelpButton mappingHelp = HelpButton.create();
        mappingHelp.setHelpContent("Field mapping", new SafeHtmlBuilder()
                .appendHtmlConstant("<p>")
                .appendEscaped(MAPPING_HELP)
                .appendHtmlConstant("</p>")
                .toSafeHtml());
        mappingHeader.add(mappingHelp);

        fromTime.setOptional(true);
        toTime.setOptional(true);
        sampleSize.setDisplayValueFunction(size -> ElasticFieldStatsSupport.formatCount(size.longValue()));
        mode.setDisplayValueFunction(exact -> Boolean.TRUE.equals(exact)
                ? MODE_EXACT
                : MODE_RANDOM);
        mode.addItems(List.of(false, true));
        mode.setValue(false);
        createTopValuesControls();
        refreshStats.setSvg(SvgImage.REFRESH);
        refreshStats.setTitle("Refresh statistics");
        retry.setIcon(SvgImage.REFRESH);
    }

    private void createTopValuesControls() {
        topValuesControls.addStyleName(style.topValuesControls());
        topValuesCount.addStyleName(style.topValuesCount());
        topValuesCount.setDisplayValueFunction(count -> "Top " + count);
        topValuesCount.addItems(ElasticFieldStatsSupport.TOP_VALUES_COUNTS);
        topValuesCount.setValue(ElasticFieldStatsSupport.DEFAULT_TOP_VALUES_COUNT);
        topValuesCount.setTitle("How many of the most common values to show");
        topValuesCount.addValueChangeHandler(event -> {
            if (getUiHandlers() != null && topValuesCount.getValue() != null) {
                getUiHandlers().onTopValuesCountChange(topValuesCount.getValue());
            }
        });
        // A quick filter, so it clears with its clear button or escape like the other filters
        topValuesFilter.addStyleName(style.topValuesFilter());
        topValuesFilter.setPlaceholder("Filter values");
        topValuesFilter.registerPopupTextProvider(() -> new SafeHtmlBuilder()
                .appendHtmlConstant("<b>")
                .appendEscaped("Filter values")
                .appendHtmlConstant("</b><br/>")
                .appendEscaped(TOP_VALUES_FILTER_HELP)
                .appendHtmlConstant("<br/>")
                .toSafeHtml());
        topValuesFilter.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onTopValuesFilterChange(topValuesFilter.getText());
            }
        });
        topValuesSpinner.setVisible(false);
        topValuesError.addStyleName("text-danger");
        topValuesError.setVisible(false);
        topValuesControls.add(topValuesCount);
        topValuesControls.add(topValuesFilter);
        topValuesControls.add(topValuesSpinner);
        topValuesControls.add(topValuesError);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setDateTimePopupProvider(final Provider<DateTimePopup> dateTimePopupProvider) {
        fromTime.setPopupProvider(dateTimePopupProvider);
        toTime.setPopupProvider(dateTimePopupProvider);
    }

    @Override
    public void setTimeRange(final Long fromMs, final Long toMs) {
        fromTime.setValue(fromMs);
        toTime.setValue(toMs);
    }

    @Override
    public Long getFromMs() {
        return fromTime.getValue();
    }

    @Override
    public Long getToMs() {
        return toTime.getValue();
    }

    @Override
    public boolean isTimeRangeValid() {
        return fromTime.isValid() && toTime.isValid();
    }

    @Override
    public void setSampleSizes(final List<Integer> sampleSizes, final int selected) {
        sampleSize.clear();
        sampleSize.addItems(sampleSizes);
        sampleSize.setValue(selected);
    }

    @Override
    public void setMapping(final List<MappingEntry> entries, final String message) {
        mappingEntries.clear();
        mappingMessage.setText(NullSafe.string(message));
        mappingMessage.setVisible(NullSafe.isNonBlankString(message));
        for (final MappingEntry entry : NullSafe.list(entries)) {
            if (entry.getValues() != null) {
                addListEntry(mappingEntries, entry.getName(), entry.getValues());
            } else {
                addEntry(mappingEntries, entry.getName(), entry.getValue());
            }
        }
        mappingEntries.setVisible(NullSafe.hasItems(entries));
    }

    @Override
    public void setLoading(final boolean loading) {
        loadingLabel.setVisible(loading);
        if (loading) {
            clearStats();
        }
    }

    @Override
    public void clearStats() {
        statsMessage.setVisible(false);
        errorPanel.setVisible(false);
        statsContent.clear();
    }

    @Override
    public void showStatsMessage(final String message) {
        clearStats();
        statsMessage.setText(message);
        statsMessage.setVisible(true);
    }

    @Override
    public void showStatsError(final String error) {
        clearStats();
        errorText.setText("Unable to fetch statistics: " + NullSafe.string(error));
        errorPanel.setVisible(true);
    }

    @Override
    public void showStats(final ElasticFieldStats stats) {
        clearStats();
        // Counts are for every document in the time range: exact, or estimated from the sample
        final long totalDocs = Objects.requireNonNullElse(stats.getTotalDocs(), 0L);
        final long docsWithValue = Objects.requireNonNullElse(stats.getDocsWithValue(), 0L);
        // Only a random sample's counts are estimates
        final String sampledHelp = Boolean.TRUE.equals(stats.getExact())
                ? ""
                : SAMPLED_HELP;

        final FlowPanel coverage = createBlock("Coverage");
        coverage.add(createBarRow("Documents with a value", false,
                ElasticFieldStatsSupport.getPercent(docsWithValue, totalDocs),
                ElasticFieldStatsSupport.formatCount(docsWithValue) + " (" +
                ElasticFieldStatsSupport.formatPercent(docsWithValue, totalDocs) + ")"));
        statsContent.add(coverage);

        final FlowPanel values = new FlowPanel();
        values.addStyleName(style.entries());
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
            // The controls are kept between renders, so the filter keeps its text and focus while the user types
            topValuesTotalDocs = totalDocs;
            final FlowPanel topValues = createBlock("Most common values", TOP_VALUES_HELP + sampledHelp);
            topValues.add(topValuesControls);
            topValues.add(topValuesList);
            showTopValues(stats.getTopValues());
            statsContent.add(topValues);
        }

        if (NullSafe.hasItems(stats.getHistogram())) {
            final FlowPanel histogram = createBlock("Distribution", DISTRIBUTION_HELP + sampledHelp);
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
                        ElasticFieldStatsSupport.formatCount(count) + " (" +
                        ElasticFieldStatsSupport.formatPercent(count, totalDocs) + ")");
                if (wideLabels) {
                    row.addStyleName(style.barRowWide());
                }
                histogram.add(row);
            }
            statsContent.add(histogram);
        }
    }

    @Override
    public void showTopValues(final List<ElasticFieldValueCount> topValues) {
        topValuesList.clear();
        if (NullSafe.isEmptyCollection(topValues)) {
            topValuesList.add(new Label(NullSafe.isBlankString(topValuesFilter.getText())
                    ? "No values."
                    : "No values match the filter."));
            return;
        }
        for (final ElasticFieldValueCount valueCount : topValues) {
            final long count = Objects.requireNonNullElse(valueCount.getCount(), 0L);
            topValuesList.add(createBarRow(valueCount.getValue(), true,
                    ElasticFieldStatsSupport.getPercent(count, topValuesTotalDocs),
                    ElasticFieldStatsSupport.formatCount(count) + " (" +
                    ElasticFieldStatsSupport.formatPercent(count, topValuesTotalDocs) + ")"));
        }
    }

    @Override
    public void setTopValuesLoading(final boolean loading) {
        topValuesSpinner.setVisible(loading);
        if (loading) {
            topValuesError.setVisible(false);
        }
    }

    @Override
    public void showTopValuesError(final String error) {
        topValuesError.setText("Unable to fetch the values: " + NullSafe.string(error));
        topValuesError.setVisible(true);
    }

    @Override
    public void setTopValuesFilter(final String filter) {
        // Not firing an event, as the presenter has already applied the filter
        topValuesFilter.setText(NullSafe.string(filter), false);
    }

    @Override
    public TaskMonitorFactory getStatsTaskMonitorFactory() {
        return spinner;
    }

    private FlowPanel createBlock(final String heading) {
        return createBlock(heading, null);
    }

    /// @param heading  the heading of the block
    /// @param helpText text explaining what the block shows, shown by an info button beside the heading, or `null`
    private FlowPanel createBlock(final String heading, final String helpText) {
        final FlowPanel block = new FlowPanel();
        final FlowPanel headingPanel = new FlowPanel();
        headingPanel.addStyleName(style.sectionHeader());
        headingPanel.addStyleName(style.statsHeader());
        final Label headingLabel = new Label(heading);
        headingLabel.addStyleName(style.statsHeading());
        headingPanel.add(headingLabel);
        if (helpText != null) {
            final HelpButton helpButton = HelpButton.create();
            helpButton.setHelpContent(heading, new SafeHtmlBuilder()
                    .appendHtmlConstant("<p>")
                    .appendEscaped(helpText)
                    .appendHtmlConstant("</p>")
                    .toSafeHtml());
            headingPanel.add(helpButton);
        }
        block.add(headingPanel);
        return block;
    }

    private Widget createBarRow(final String label,
                                final boolean copyable,
                                final double percent,
                                final String countText) {
        final FlowPanel row = new FlowPanel();
        row.addStyleName(style.barRow());

        final Widget labelWidget = copyable
                ? createCopyableText(label)
                : new Label(NullSafe.string(label));
        labelWidget.addStyleName(style.barLabel());
        labelWidget.setTitle(NullSafe.string(label));
        row.add(labelWidget);

        final FlowPanel track = new FlowPanel();
        track.addStyleName(style.barTrack());
        final FlowPanel container = new FlowPanel();
        container.addStyleName("percentBarCell-container");
        final FlowPanel bar = new FlowPanel();
        bar.addStyleName("percentBarCell-bar percentBarCell-bar__healthy");
        bar.getElement().getStyle().setWidth(Math.max(0, Math.min(100, percent)), Unit.PCT);
        container.add(bar);
        track.add(container);
        row.add(track);

        final Label countLabel = new Label(countText);
        countLabel.addStyleName(style.barCount());
        row.add(countLabel);
        return row;
    }

    private void addEntryIfSet(final FlowPanel panel, final String name, final String value) {
        if (NullSafe.isNonBlankString(value)) {
            addEntry(panel, name, value);
        }
    }

    private void addListEntry(final FlowPanel panel, final String name, final List<String> values) {
        final Label nameLabel = new Label(name);
        nameLabel.addStyleName(style.entryName());
        final FlowPanel valuesPanel = new FlowPanel();
        valuesPanel.addStyleName(style.entryValue());
        valuesPanel.addStyleName(style.valueList());
        for (final String value : values) {
            valuesPanel.add(createCopyableText(value));
        }
        panel.add(nameLabel);
        panel.add(valuesPanel);
    }

    /// @return the text, with a button to copy it that shows when the mouse is over it
    private static Widget createCopyableText(final String text) {
        final HTML html = new HTML(CopyTextUtil.render(text, false));
        html.addDomHandler(event -> CopyTextUtil.onClick(event.getNativeEvent(), html), MouseDownEvent.getType());
        return html;
    }

    private void addEntry(final FlowPanel panel, final String name, final String value) {
        final Label nameLabel = new Label(name);
        nameLabel.addStyleName(style.entryName());
        final Label valueLabel = new Label(NullSafe.string(value));
        valueLabel.addStyleName(style.entryValue());
        panel.add(nameLabel);
        panel.add(valueLabel);
    }

    @UiHandler("fromTime")
    void onFromTimeChange(final ValueChangeEvent<String> event) {
        onTimeRangeChange();
    }

    @UiHandler("toTime")
    void onToTimeChange(final ValueChangeEvent<String> event) {
        onTimeRangeChange();
    }

    @UiHandler("mode")
    void onModeChange(final ValueChangeEvent<Boolean> event) {
        final boolean exact = Boolean.TRUE.equals(mode.getValue());
        // A random sample is taken from all documents; exact statistics are for a time range
        randomControls.setVisible(!exact);
        exactControls.setVisible(exact);
        if (getUiHandlers() != null) {
            getUiHandlers().onExactChange(exact);
        }
    }

    @UiHandler("sampleSize")
    void onSampleSizeChange(final ValueChangeEvent<Integer> event) {
        if (getUiHandlers() != null && sampleSize.getValue() != null) {
            getUiHandlers().onSampleSizeChange(sampleSize.getValue());
        }
    }

    @UiHandler("refreshStats")
    void onRefreshStatsClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRefreshStats();
        }
    }

    @UiHandler("retry")
    void onRetryClick(final ClickEvent event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onRefreshStats();
        }
    }

    private void onTimeRangeChange() {
        if (getUiHandlers() != null) {
            getUiHandlers().onTimeRangeChange();
        }
    }

    /// UiBinder for [ElasticFieldInfoViewImpl].
    public interface Binder extends UiBinder<Widget, ElasticFieldInfoViewImpl> {

    }

    /// The styles declared in the view's ui.xml.
    public interface Style extends CssResource {

        /// @return the style of a section heading with its help button
        String sectionHeader();

        /// @return the style of a section title, as in the stream browser's Info tab
        String sectionTitle();

        /// @return the style of a section heading after the first
        String laterSection();

        /// @return the style of a list of values
        String valueList();

        /// @return the style of a list of names and values
        String entries();

        /// @return the style of a name in a list of names and values
        String entryName();

        /// @return the style of a value in a list of names and values
        String entryValue();

        /// @return the style of the statistics controls
        String controls();

        /// @return the style of a date-time box
        String dateBox();

        /// @return the style of the sample size selector
        String sampleSize();

        /// @return the style of the statistics content
        String stats();

        /// @return the style of a statistics block heading
        String statsHeading();

        /// @return the style of the line holding a statistics block heading and its info button
        String statsHeader();

        /// @return the style of a labelled bar whose label is long, such as a range of timestamps
        String barRowWide();

        /// @return the style of the line of controls for the most common values
        String topValuesControls();

        /// @return the style of the selector of how many common values to show
        String topValuesCount();

        /// @return the style of the filter of the common values
        String topValuesFilter();

        /// @return the style of a labelled bar
        String barRow();

        /// @return the style of a bar's label
        String barLabel();

        /// @return the style of the space a bar is drawn in
        String barTrack();

        /// @return the style of a bar's count
        String barCount();

        /// @return the style of the error message and retry button
        String errorPanel();
    }
}
