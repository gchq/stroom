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
import stroom.widget.datepicker.client.DateTimeBox;
import stroom.widget.datepicker.client.DateTimePopup;
import stroom.widget.help.client.HelpButton;
import stroom.widget.spinner.client.SpinnerSmall;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.resources.client.CssResource;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
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
    SelectionBox<Integer> sampleSize;
    @UiField
    Button refreshStats;
    @UiField
    SpinnerSmall spinner;
    @UiField
    Label loadingLabel;
    @UiField
    Label timeFieldLabel;
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
        refreshStats.setIcon(SvgImage.REFRESH);
        retry.setIcon(SvgImage.REFRESH);
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
    public void setTimeFieldName(final String timeField) {
        if (NullSafe.isBlankString(timeField)) {
            timeFieldLabel.setText("Documents are sampled from the time range using the index's main date field.");
        } else {
            timeFieldLabel.setText("Documents are sampled from the time range using the index's main date field, '" +
                                   timeField + "'.");
        }
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
            addEntry(mappingEntries, entry.getName(), entry.getValue());
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
        final long sampledDocs = Objects.requireNonNullElse(stats.getSampledDocs(), 0L);
        final long docsWithValue = Objects.requireNonNullElse(stats.getDocsWithValue(), 0L);

        final FlowPanel summary = new FlowPanel();
        summary.add(new Label("Sampled " + ElasticFieldStatsSupport.formatCount(sampledDocs) +
                              " documents (up to " + ElasticFieldStatsSupport.formatCount(
                                      Objects.requireNonNullElse(stats.getSampleSize(), 0).longValue()) +
                              " per shard)."));
        statsContent.add(summary);

        final FlowPanel coverage = createBlock("Coverage");
        coverage.add(createBarRow("Documents with a value",
                ElasticFieldStatsSupport.getPercent(docsWithValue, sampledDocs),
                ElasticFieldStatsSupport.formatCount(docsWithValue) + " (" +
                ElasticFieldStatsSupport.formatPercent(docsWithValue, sampledDocs) + ")"));
        statsContent.add(coverage);

        final FlowPanel values = new FlowPanel();
        values.addStyleName(style.entries());
        if (stats.getDistinctCount() != null) {
            addEntry(values, "Distinct values",
                    ElasticFieldStatsSupport.formatCount(stats.getDistinctCount()) + " (approximate)");
        }
        addEntryIfSet(values, "Minimum", stats.getMin());
        addEntryIfSet(values, "Maximum", stats.getMax());
        addEntryIfSet(values, "Mean", ElasticFieldStatsSupport.formatDecimal(stats.getMean()));
        addEntryIfSet(values, "Median", ElasticFieldStatsSupport.formatDecimal(stats.getMedian()));
        if (values.getWidgetCount() > 0) {
            statsContent.add(values);
        }

        if (NullSafe.hasItems(stats.getTopValues())) {
            final FlowPanel topValues = createBlock("Most common values");
            for (final ElasticFieldValueCount valueCount : stats.getTopValues()) {
                final long count = Objects.requireNonNullElse(valueCount.getCount(), 0L);
                topValues.add(createBarRow(valueCount.getValue(),
                        ElasticFieldStatsSupport.getPercent(count, sampledDocs),
                        ElasticFieldStatsSupport.formatCount(count) + " (" +
                        ElasticFieldStatsSupport.formatPercent(count, sampledDocs) + " of sample)"));
            }
            statsContent.add(topValues);
        }

        if (NullSafe.hasItems(stats.getHistogram())) {
            long maxCount = 0;
            for (final ElasticFieldValueCount bucket : stats.getHistogram()) {
                maxCount = Math.max(maxCount, Objects.requireNonNullElse(bucket.getCount(), 0L));
            }
            final FlowPanel histogram = createBlock("Distribution");
            for (final ElasticFieldValueCount bucket : stats.getHistogram()) {
                final long count = Objects.requireNonNullElse(bucket.getCount(), 0L);
                // Bars are scaled to the largest bucket so the shape of the distribution is easy to see
                histogram.add(createBarRow(bucket.getValue(),
                        ElasticFieldStatsSupport.getPercent(count, maxCount),
                        ElasticFieldStatsSupport.formatCount(count) + " (" +
                        ElasticFieldStatsSupport.formatPercent(count, sampledDocs) + " of sample)"));
            }
            statsContent.add(histogram);
        }
    }

    @Override
    public TaskMonitorFactory getStatsTaskMonitorFactory() {
        return spinner;
    }

    private FlowPanel createBlock(final String heading) {
        final FlowPanel block = new FlowPanel();
        final Label headingLabel = new Label(heading);
        headingLabel.addStyleName(style.statsHeading());
        block.add(headingLabel);
        return block;
    }

    private Widget createBarRow(final String label, final double percent, final String countText) {
        final FlowPanel row = new FlowPanel();
        row.addStyleName(style.barRow());

        final Label labelWidget = new Label(NullSafe.string(label));
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

        /// @return the style of the refresh button and loading indicator
        String refresh();

        /// @return the style of the statistics content
        String stats();

        /// @return the style of a statistics block heading
        String statsHeading();

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
