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

package stroom.streamstore.client.presenter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

import com.google.gwt.cell.client.FieldUpdater;
import com.google.gwt.cell.client.ImageResourceCell;
import com.google.gwt.cell.client.SafeHtmlCell;
import com.google.gwt.cell.client.TextCell;
import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.resources.client.ImageResource;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.cellview.client.Column;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;

import stroom.cell.expander.client.ExpanderCell;
import stroom.data.grid.client.DataGridView;
import stroom.data.grid.client.DataGridViewImpl;
import stroom.data.grid.client.EndColumn;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.util.shared.Expander;
import stroom.util.shared.Marker;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.StreamLocation;
import stroom.util.shared.Summary;
import stroom.util.shared.TreeRow;
import stroom.xmleditor.client.view.LeftBar;

public class MarkerListPresenter extends MyPresenterWidget<DataGridView<Marker>> {
    private static HashSet<Severity> ALL_SEVERITIES = new HashSet<Severity>(Arrays.asList(Severity.SEVERITIES));

    private static LeftBar.Resources resources;
    private HashSet<Severity> expandedSeverities;
    private DataPresenter dataPresenter;

    @Inject
    public MarkerListPresenter(final EventBus eventBus) {
        super(eventBus, new DataGridViewImpl<Marker>(false, DataGridViewImpl.DEFAULT_LIST_PAGE_SIZE, null));

        if (resources == null) {
            resources = GWT.create(LeftBar.Resources.class);
        }

        addExpanderColumn();
        addSeverityColumn();
        addElementId();
        addStream();
        addLine();
        addCol();
        addMessage();
        getView().addEndColumn(new EndColumn<Marker>());
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
        expanderColumn.setFieldUpdater(new FieldUpdater<Marker, Expander>() {
            @Override
            public void update(final int index, final Marker marker, final Expander value) {
                final Severity severity = marker.getSeverity();
                if (severity != null) {
                    if (expandedSeverities.contains(severity)) {
                        expandedSeverities.remove(severity);
                    } else {
                        expandedSeverities.add(severity);
                    }
                }

                dataPresenter.update(true);
            }
        });
        getView().addColumn(expanderColumn, "<br/>", 16);
    }

    private void addSeverityColumn() {
        getView().addColumn(new Column<Marker, ImageResource>(new ImageResourceCell()) {
            @Override
            public ImageResource getValue(final Marker marker) {
                switch (marker.getSeverity()) {
                case FATAL_ERROR:
                    return resources.fatal();
                case ERROR:
                    return resources.error();
                case WARNING:
                    return resources.warning();
                case INFO:
                    return resources.info();
                }

                return resources.warning();
            }
        }, "", 18);
    }

    private void addElementId() {
        getView().addResizableColumn(new Column<Marker, SafeHtml>(new SafeHtmlCell()) {
            @Override
            public SafeHtml getValue(final Marker marker) {
                if (marker instanceof StoredError) {
                    final StoredError storedError = (StoredError) marker;
                    if (storedError.getElementId() != null) {
                        return SafeHtmlUtils.fromString(storedError.getElementId());
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

                    final SafeHtmlBuilder builder = new SafeHtmlBuilder();
                    builder.appendHtmlConstant("<div style=\"position:absolute;overflow:visible;font-weight:bold;\">");
                    builder.appendEscaped(sb.toString());
                    builder.appendHtmlConstant("</div>");

                    return builder.toSafeHtml();
                }

                return null;
            }
        }, "Element", 150);
    }

    private void addStream() {
        getView().addResizableColumn(new Column<Marker, String>(new TextCell()) {
            @Override
            public String getValue(final Marker marker) {
                if (marker instanceof StoredError) {
                    final StoredError storedError = (StoredError) marker;
                    if (storedError.getLocation() != null && storedError.getLocation() instanceof StreamLocation) {
                        final StreamLocation streamLocation = (StreamLocation) storedError.getLocation();
                        if (streamLocation.getStreamNo() >= 0) {
                            return String.valueOf(streamLocation.getStreamNo());
                        }
                    }
                }
                return null;
            }
        }, "Stream", 50);
    }

    private void addLine() {
        getView().addResizableColumn(new Column<Marker, String>(new TextCell()) {
            @Override
            public String getValue(final Marker marker) {
                if (marker instanceof StoredError) {
                    final StoredError storedError = (StoredError) marker;
                    if (storedError.getLocation().getLineNo() >= 0) {
                        return String.valueOf(storedError.getLocation().getLineNo());
                    }
                }
                return null;
            }
        }, "Line", 50);
    }

    private void addCol() {
        getView().addResizableColumn(new Column<Marker, String>(new TextCell()) {
            @Override
            public String getValue(final Marker marker) {
                if (marker instanceof StoredError) {
                    final StoredError storedError = (StoredError) marker;
                    if (storedError.getLocation().getColNo() >= 0) {
                        return String.valueOf(storedError.getLocation().getColNo());
                    }
                }
                return null;
            }
        }, "Col", 50);
    }

    private void addMessage() {
        getView().addResizableColumn(new Column<Marker, String>(new TextCell()) {
            @Override
            public String getValue(final Marker marker) {
                if (marker instanceof StoredError) {
                    final StoredError storedError = (StoredError) marker;
                    return storedError.getMessage();
                }

                return null;
            }
        }, "Message", 700);
    }

    public void setData(final List<Marker> markers, final int start, final int count) {
        if (markers == null) {
            // Reset visible range.
            getView().setVisibleRangeAndClearData(new Range(0, 100), false);
        } else {
            getView().setRowData(start, markers);
            getView().setRowCount(count);
        }
    }

    public HandlerRegistration addRangeChangeHandler(final RangeChangeEvent.Handler handler) {
        return getView().addRangeChangeHandler(handler);
    }

    public Severity[] getExpandedSeverities() {
        if (expandedSeverities.size() == 0) {
            return null;
        }

        Severity[] severities = new Severity[expandedSeverities.size()];
        severities = expandedSeverities.toArray(severities);
        return severities;
    }

    public void resetExpandedSeverities() {
        expandedSeverities = new HashSet<Severity>(ALL_SEVERITIES);
    }

    public void setDataPresenter(final DataPresenter dataPresenter) {
        this.dataPresenter = dataPresenter;
    }
}
