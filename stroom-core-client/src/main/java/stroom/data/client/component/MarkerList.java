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

package stroom.data.client.component;

import stroom.cell.expander.client.ExpanderCell;
import stroom.cell.info.client.CellIcon;
import stroom.data.client.ColumnSizeConstants;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.svg.shared.SvgImage;
import stroom.util.client.DataGridUtil;
import stroom.util.shared.Expander;
import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StreamLocation;
import stroom.util.shared.Summary;
import stroom.util.shared.TreeRow;
import stroom.widget.component.client.Component;
import stroom.widget.datagrid.client.DataGrid;
import stroom.widget.util.client.HtmlBuilder;

import com.google.gwt.core.client.GWT;
import com.google.gwt.regexp.shared.RegExp;
import com.google.gwt.regexp.shared.SplitResult;
import com.google.gwt.safehtml.client.SafeHtmlTemplates;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/// A stream's markers: a summary row for each severity (bold, its count spanning the columns after
/// its icon), which expands to show its markers, each with its element, stream part, line, column
/// and message.
public class MarkerList extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);
    private static final Template TEMPLATE = GWT.create(Template.class);

    @UiField
    DataGrid<Marker> dataGrid;

    private final RegExp messageCauseDelimiterPattern =
            RegExp.compile(RegExp.quote(StoredError.MESSAGE_CAUSE_DELIMITER));
    private Consumer<Severity> onToggle = severity -> {
    };

    /// Makes an empty list.
    public MarkerList() {
        initWidget(BINDER.createAndBindUi(this));
        // A severity's summary row is keyed by its severity (there is one of each), so it is still the
        // same row, keeping the keyboard's place, when expanding or collapsing it fetches new rows
        dataGrid.setKeyProvider(marker -> marker instanceof final Summary summary
                ? summary.getSeverity()
                : marker);
        // A summary row's text spans the severity's column and the four after it
        dataGrid.setRowSpans((marker, absIndex) -> {
            if (marker instanceof Summary) {
                final Map<Integer, Integer> spans = new HashMap<>();
                spans.put(2, 5);
                return spans;
            }
            return Collections.emptyMap();
        });

        addExpanderColumn();
        addSeverityColumn();
        addElementId();
        addStream();
        addLine();
        addCol();
        addMessage();
    }

    /// Shows a page of markers.
    ///
    /// @param markers The page's markers, or `null` for none.
    /// @param start   The absolute index of the page's first marker.
    /// @param count   How many markers there are in all.
    public void setData(final List<Marker> markers, final int start, final int count) {
        if (markers == null) {
            // Reset visible range.
            dataGrid.setRowData(0, new ArrayList<>());
            dataGrid.setRowCount(0);
        } else {
            dataGrid.setRowData(start, markers);
            dataGrid.setRowCount(count);
        }
    }

    /// @param onToggle Given a summary row's severity when its expander is pressed.
    public void setOnToggle(final Consumer<Severity> onToggle) {
        this.onToggle = onToggle;
    }

    private void addExpanderColumn() {
        // Expander column.
        final Column<Marker, Expander> expanderColumn = new Column<Marker, Expander>(new ExpanderCell()) {
            @Override
            public Expander getValue(final Marker marker) {
                if (marker instanceof TreeRow) {
                    return ((TreeRow) marker).getExpander();
                }
                return null;
            }
        };
        expanderColumn.setFieldUpdater((index, marker, value) -> onToggle.accept(marker.getSeverity()));
        dataGrid.addColumn(expanderColumn, "<br/>", ExpanderCell.getColumnWidth(1));
    }

    private void addSeverityColumn() {
        dataGrid.addColumn(DataGridUtil
                .cellIconColumnBuilder(false, (final Marker marker) -> {
                    switch (marker.getSeverity()) {
                        case FATAL_ERROR:
                            return CellIcon.of(SvgImage.FATAL, "Fatal Error");
                        case ERROR:
                            return CellIcon.of(SvgImage.ERROR, "Error");
                        case WARNING:
                            return CellIcon.of(SvgImage.ALERT, "Warning");
                        case INFO:
                            return CellIcon.of(SvgImage.INFO, "Info");
                        default:
                            return null;
                    }
                })
                .build(), "", ColumnSizeConstants.ICON_COL);
    }

    private void addElementId() {
        dataGrid.addResizableColumn(DataGridUtil
                        .htmlColumnBuilder((final Marker marker) -> {
                            if (marker instanceof StoredError) {
                                final StoredError storedError = (StoredError) marker;
                                if (storedError.getElementId() != null) {
                                    return SafeHtmlUtils.fromString(storedError.getElementId().toString());
                                }

                            } else if (marker instanceof Summary) {
                                final Summary summary = (Summary) marker;

                                final StringBuilder sb = new StringBuilder();
                                sb.append(summary.getSeverity().getSummaryValue());
                                sb.append(" (");
                                if (summary.getTotal() > summary.getCount()) {
                                    sb.append(summary.getCount());
                                    sb.append(" of ");
                                    sb.append(summary.getTotal());

                                    if (summary.getTotal() >= FetchMarkerResult.MAX_TOTAL_MARKERS) {
                                        sb.append("+");
                                    }

                                    if (summary.getTotal() <= 1) {
                                        sb.append(" item)");
                                    } else {
                                        sb.append(" items)");
                                    }
                                } else {
                                    sb.append(summary.getCount());
                                    if (summary.getCount() <= 1) {
                                        sb.append(" item)");
                                    } else {
                                        sb.append(" items)");
                                    }
                                }

                                // Summary rows are bold
                                return TEMPLATE.summary(sb.toString());
                            }

                            return null;
                        })
                        .build(),
                "Element",
                150);
    }

    private void addStream() {
        dataGrid.addResizableColumn(DataGridUtil
                        .htmlColumnBuilder((final Marker marker) -> {
                            final StoredError storedError = convertToStoredError(marker);
                            if (storedError != null && storedError.getLocation() != null &&
                                storedError.getLocation() instanceof StreamLocation) {
                                final StreamLocation streamLocation =
                                        (StreamLocation) storedError.getLocation();
                                if (streamLocation.getPartIndex() > -1) {
                                    return SafeHtmlUtils
                                            .fromString(String.valueOf(streamLocation.getPartIndex() + 1));
                                } else {
                                    return null;
                                }
                            } else {
                                return null;
                            }
                        })
                        .build(),
                "Stream",
                50);
    }

    private void addLine() {
        dataGrid.addResizableColumn(DataGridUtil
                        .htmlColumnBuilder((final Marker marker) -> {
                            final StoredError storedError = convertToStoredError(marker);
                            if (storedError != null && storedError.getLocation().getLineNo() >= 0) {
                                return SafeHtmlUtils
                                        .fromString(String.valueOf(storedError.getLocation().getLineNo()));
                            } else {
                                return null;
                            }
                        })
                        .build(),
                "Line",
                50);
    }

    private void addCol() {
        dataGrid.addResizableColumn(DataGridUtil
                        .htmlColumnBuilder((final Marker marker) -> {
                            final StoredError storedError = convertToStoredError(marker);
                            if (storedError != null && storedError.getLocation().getColNo() >= 0) {
                                return SafeHtmlUtils
                                        .fromString(String.valueOf(storedError.getLocation().getColNo()));
                            } else {
                                return null;
                            }
                        })
                        .build(),
                "Col",
                50);
    }

    private StoredError convertToStoredError(final Marker marker) {
        return marker instanceof StoredError
                ? (StoredError) marker
                : null;
    }

    private void addMessage() {
        dataGrid.addResizableColumn(DataGridUtil
                        .htmlColumnBuilder((final Marker marker) -> {
                            final StoredError storedError = convertToStoredError(marker);
                            if (storedError == null) {
                                return SafeHtmlUtils.EMPTY_SAFE_HTML;
                            }

                            // Some messages, e.g. ref data ones have multiple sub msgs within the msgs
                            // so split them out
                            final SplitResult splitResult = messageCauseDelimiterPattern.split(
                                    storedError.getMessage());

                            final HtmlBuilder htmlBuilder = new HtmlBuilder();
                            for (int i = 0; i < splitResult.length(); i++) {
                                if (i != 0) {
                                    htmlBuilder.append(HtmlBuilder.EN_SPACE);
                                    htmlBuilder.appendTrustedString("> ");
                                }

                                if (splitResult.get(i) != null) {
                                    htmlBuilder.append(splitResult.get(i));
                                    htmlBuilder.br();
                                }
                            }
                            return htmlBuilder.toSafeHtml();
                        })
                        .build(),
                "Message",
                800);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MarkerList> {

    }

    // A severity's summary, in bold
    interface Template extends SafeHtmlTemplates {

        @Template("<div class=\"marker-list__summary\">{0}</div>")
        SafeHtml summary(String text);
    }
}
