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

package stroom.data.client.presenter;

import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.SimpleEventBus;
import com.google.gwt.user.client.Timer;
import com.google.gwt.view.client.HasRows;
import com.google.gwt.view.client.Range;
import com.google.gwt.view.client.RangeChangeEvent;
import com.google.gwt.view.client.RangeChangeEvent.Handler;
import com.google.gwt.view.client.RowCountChangeEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.StepLocation;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.Highlight;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedList;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataPresenter extends MyPresenterWidget<DataPresenter.DataView> implements TextUiHandlers {
    private static final String META = "Meta";
    private static final String META_DATA = "Meta Data";
    private static final String CONTEXT = "Context";
    private static final String ERROR = "Error";

    private final TabData errorTab = new TabDataImpl("Error");
    private final TabData dataTab = new TabDataImpl("Data");
    private final TabData metaTab = new TabDataImpl("Meta");
    private final TabData contextTab = new TabDataImpl("Context");
    private final TextPresenter textPresenter;
    private final MarkerListPresenter markerListPresenter;
    private final ClientDispatchAsync dispatcher;
    private final PagerRows pageRows;
    private final PagerRows streamRows;
    private final Map<String, OffsetRange<Long>> dataTypeOffsetRangeMap = new HashMap<>();
    private final boolean userHasPipelineSteppingPermission;

    private boolean errorMarkerMode = true;
    private Long currentMetaId;
    private String currentStreamType;
    private String currentChildDataType;
    private OffsetRange<Long> currentDataRange = new OffsetRange<>(0L, 1L);
    private OffsetRange<Long> currentPageRange = new OffsetRange<>(0L, 100L);
    private AbstractFetchDataResult lastResult;
    private List<FetchDataAction> actionQueue;
    private Timer delayedFetchDataTimer;
    private String data;
    private SharedList<Marker> markers;
    private int startLineNo;
    private List<Highlight> highlights;
    private Long highlightId;
    private Long highlightPartNo;
    private String highlightChildDataType;
    private boolean playButtonVisible;
    private ClassificationUiHandlers classificationUiHandlers;
    private BeginSteppingHandler beginSteppingHandler;
    private boolean steppingSource;
    private boolean formatOnLoad;
    private boolean ignoreActions;

    @Inject
    public DataPresenter(final EventBus eventBus, final DataView view, final TextPresenter textPresenter,
                         final MarkerListPresenter markerListPresenter, final ClientSecurityContext securityContext,
                         final ClientDispatchAsync dispatcher) {
        super(eventBus, view);
        this.textPresenter = textPresenter;
        this.markerListPresenter = markerListPresenter;
        this.dispatcher = dispatcher;

        markerListPresenter.getWidget().getElement().getStyle().setWidth(100, Unit.PCT);
        markerListPresenter.getWidget().getElement().getStyle().setHeight(100, Unit.PCT);
        markerListPresenter.setDataPresenter(this);

        textPresenter.setUiHandlers(this);

        pageRows = new PageRows();
        streamRows = new StreamRows();
        view.setDataPagerRows(pageRows);
        view.setSegmentPagerRows(streamRows);

        addTab(errorTab);
        addTab(dataTab);
        addTab(metaTab);
        addTab(contextTab);

        userHasPipelineSteppingPermission = securityContext.hasAppPermission(PermissionNames.STEPPING_PERMISSION);
    }

    private void addTab(final TabData tab) {
        getView().getTabBar().addTab(tab);
        hideTab(tab, true);
        // tabs.add(tab);
    }

    private void hideTab(final TabData tab, final boolean hide) {
        getView().getTabBar().setTabHidden(tab, hide);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getTabBar().addSelectionHandler(event -> selectTab(event.getSelectedItem())));
    }

    private void selectTab(final TabData tab) {
        // Make sure tabs don't do anything in stepping mode.
        if (!steppingSource) {
            if (tab != null) {
                if (META.equals(tab.getLabel())) {
                    fetchDataForCurrentStreamNo(META_DATA);
                } else if (CONTEXT.equals(tab.getLabel())) {
                    fetchDataForCurrentStreamNo(CONTEXT);
                } else if (ERROR.equals(tab.getLabel())) {
                    errorMarkerMode = true;
                    fetchDataForCurrentStreamNo(null);
                } else {
                    // Turn off error marker mode if we are currently looking at
                    // an error and switching to the data tab.
                    if (ERROR.equals(currentStreamType) && errorMarkerMode) {
                        errorMarkerMode = false;
                    }

                    fetchDataForCurrentStreamNo(null);
                }
            }
        }
    }

    @Override
    public void clear() {
        clear(true);
    }

    private void clear(final boolean fireEvents) {
        // Set response to null.
        if (actionQueue != null) {
            actionQueue.clear();
        }
        setPageResponse(null, fireEvents);
        currentMetaId = null;
    }

    @Override
    public void beginStepping() {
        if (beginSteppingHandler != null && currentMetaId != null) {
            beginSteppingHandler.beginStepping(currentMetaId, currentChildDataType);
        }
    }

    private void fetchDataForCurrentStreamNo(final String childDataType) {
        currentDataRange = new OffsetRange<>(currentDataRange.getOffset(), 1L);

        dataTypeOffsetRangeMap.put(currentChildDataType, currentPageRange);
        currentPageRange = dataTypeOffsetRangeMap.get(childDataType);
        if (currentPageRange == null) {
            currentPageRange = new OffsetRange<>(0L, 100L);
        }

        this.currentChildDataType = childDataType;
        update(true);
    }

    public void fetchData(final SourceLocation sourceLocation) {
        this.highlights = new ArrayList<>();

        if (sourceLocation.getHighlight() != null) {
            this.highlights.add(sourceLocation.getHighlight());
        }

        this.highlightId = sourceLocation.getId();
        this.highlightPartNo = sourceLocation.getPartNo();
        this.highlightChildDataType = sourceLocation.getChildType();

        // Make sure the right page is shown when the source is displayed.
        final long oldPartNo = currentDataRange.getOffset() + 1;
        long newPartNo = highlightPartNo;

        final long oldRecordOffset = currentPageRange.getOffset();

        long recordLength = currentPageRange.getLength();

        // If we have a source highlight then use it
        int lineNo = 0;
        if (highlights != null && highlights.size() > 0) {
            final Highlight highlight = highlights.get(0);
            lineNo = highlight.getFrom().getLineNo();
        }

        long newRecordOffset;
        if (sourceLocation.getHighlight() == null && sourceLocation.getRecordNo() != -1) {
            recordLength = 1L;
            newRecordOffset = sourceLocation.getRecordNo() - 1;
        } else {
            final long page = lineNo / recordLength;
            newRecordOffset = oldRecordOffset % recordLength;
            final long tmp = page * recordLength;
            if (tmp + newRecordOffset < lineNo) {
                // We can show this page.
                newRecordOffset = tmp + newRecordOffset;
            } else {
                // We need to show the page before.
                newRecordOffset = tmp - recordLength + newRecordOffset;
            }
        }

        // Update the stream source.
        if (!EqualsUtil.isEquals(currentMetaId, highlightId)
                || !EqualsUtil.isEquals(currentChildDataType, highlightChildDataType) || oldPartNo != newPartNo
                || oldRecordOffset != newRecordOffset) {
            currentDataRange = new OffsetRange<>(newPartNo - 1, 1L);
            currentPageRange = new OffsetRange<>(newRecordOffset, recordLength);

            this.currentMetaId = highlightId;
            this.currentChildDataType = highlightChildDataType;
            dataTypeOffsetRangeMap.clear();
            markerListPresenter.resetExpandedSeverities();

            update(false);
        } else {
            refreshHighlights(lastResult);
            refreshMarkers(lastResult);
        }
    }

    public void fetchData(final boolean fireEvents, final Long metaId, final String childDataType) {
        this.currentMetaId = metaId;
        this.currentChildDataType = childDataType;
        currentDataRange = new OffsetRange<>(0L, 1L);
        currentPageRange = new OffsetRange<>(0L, 100L);
        dataTypeOffsetRangeMap.clear();
        markerListPresenter.resetExpandedSeverities();
        update(fireEvents);
    }

    public void fetchData(final Meta meta) {
        this.currentMetaId = meta.getId();
        this.currentStreamType = meta.getTypeName();
        currentDataRange = new OffsetRange<>(0L, 1L);
        currentPageRange = new OffsetRange<>(0L, 100L);
        dataTypeOffsetRangeMap.clear();
        markerListPresenter.resetExpandedSeverities();
        update(true);
    }

    public void update(final boolean fireEvents) {
        if (!ignoreActions) {
            final Severity[] expandedSeverities = markerListPresenter.getExpandedSeverities();

            final FetchDataAction action = new FetchDataAction();
            action.setStreamId(currentMetaId);
            action.setStreamRange(currentDataRange);
            action.setPageRange(currentPageRange);
            action.setChildStreamType(currentChildDataType);
            action.setMarkerMode(errorMarkerMode);
            action.setExpandedSeverities(expandedSeverities);
            action.setFireEvents(fireEvents);
            doFetch(action, fireEvents);
        }
    }

    private void doFetch(final FetchDataAction action, final boolean fireEvents) {
        if (currentMetaId != null) {
            getView().setRefreshing(true);

            // Create the action queue and delayed refresh timer if we haven't
            // already.
            ensureActionQueue();

            // Add the action and schedule the timer.
            actionQueue.add(action);
            delayedFetchDataTimer.cancel();
            delayedFetchDataTimer.schedule(250);

        } else {
            clear(fireEvents);
        }
    }

    private void ensureActionQueue() {
        if (actionQueue == null) {
            actionQueue = new ArrayList<>();
            delayedFetchDataTimer = new Timer() {
                @Override
                public void run() {
                    if (actionQueue.size() > 0) {
                        final FetchDataAction action = actionQueue.get(actionQueue.size() - 1);
                        actionQueue.clear();

                        dispatcher.exec(action)
                                .onSuccess(result -> {
                                    // If we are queueing more actions then don't
                                    // update the text.
                                    if (actionQueue.size() == 0) {
                                        setPageResponse(result, action.isFireEvents());
                                        getView().setRefreshing(false);
                                    }
                                })
                                .onFailure(caught -> getView().setRefreshing(false));
                    }
                }
            };
        }
    }

    private void setPageResponse(final AbstractFetchDataResult result, final boolean fireEvents) {
        ignoreActions = true;
        this.lastResult = result;

        if (result == null || result.getStreamType() == null || steppingSource) {
            playButtonVisible = false;
        } else {
            playButtonVisible = beginSteppingHandler != null && userHasPipelineSteppingPermission;
        }

        data = "";
        markers = null;
        startLineNo = 1;

        if (result != null) {
            if (result instanceof FetchMarkerResult) {
                final FetchMarkerResult fetchMarkerResult = (FetchMarkerResult) result;
                markers = fetchMarkerResult.getMarkers();
            } else if (result instanceof FetchDataResult) {
                final FetchDataResult fetchDataResult = (FetchDataResult) result;
                data = fetchDataResult.getData();
            }

            startLineNo = result.getPageRange().getOffset().intValue() + 1;

            // Update the paging controls.
            getView().showSegmentPager(result.getStreamRowCount().getCount() > 1);

            // Let the classification handler know that the classification has
            // changed.
            classificationUiHandlers.setClassification(result.getClassification());

            refresh(result);
            updateTabs(result.getStreamType(), result.getAvailableChildStreamTypes());

        } else {
            getView().showSegmentPager(false);
            // Clear the classification.
            classificationUiHandlers.setClassification("");

            refresh(result);
            updateTabs(null, null);
        }
        ignoreActions = false;
    }

    private void updateTabs(final String streamType, final List<String> availableChildStreamTypes) {
        if (streamType == null) {
            // Hide all links.
            hideTab(errorTab, true);
            hideTab(dataTab, true);
            hideTab(metaTab, true);
            hideTab(contextTab, true);

            hideBothPresenters();

        } else if (steppingSource) {
            // Hide all links except data.
            hideTab(errorTab, true);
            hideTab(dataTab, false);
            hideTab(metaTab, true);
            hideTab(contextTab, true);
            showTextPresenter();
            getView().getTabBar().selectTab(dataTab);

        } else {
            // Highlight the appropriate link.
            if (errorMarkerMode && ERROR.equals(streamType)) {
                getView().getTabBar().selectTab(errorTab);
                showMarkerPresenter();
            } else if (META_DATA.equals(streamType)) {
                getView().getTabBar().selectTab(metaTab);
                showTextPresenter();
            } else if (CONTEXT.equals(streamType)) {
                getView().getTabBar().selectTab(contextTab);
                showTextPresenter();
            } else {
                getView().getTabBar().selectTab(dataTab);
                showTextPresenter();
            }

            // Show only applicable links.
            hideTab(errorTab, !ERROR.equals(currentStreamType));
            hideTab(dataTab, false);
            if (availableChildStreamTypes != null) {
                hideTab(metaTab, !availableChildStreamTypes.contains(META_DATA));
                hideTab(contextTab, !availableChildStreamTypes.contains(CONTEXT));
            } else {
                hideTab(metaTab, true);
                hideTab(contextTab, true);
            }
        }
    }

    private void showTextPresenter() {
        getView().getLayerContainer().show(textPresenter);
    }

    private void showMarkerPresenter() {
        getView().getLayerContainer().show(markerListPresenter);
    }

    private void hideBothPresenters() {
        getView().getLayerContainer().clear();
    }

    private void refresh(final AbstractFetchDataResult result) {
        int streamOffset = 0;
        int streamLength = 0;
        int streamCount = 0;
        boolean streamCountExact = true;
        int pageOffset = 0;
        int pageLength = 0;
        int pageCount = 0;
        boolean pageCountExact = true;

        // Update paging info from the current result.
        if (result != null) {
            streamOffset = result.getStreamRange().getOffset().intValue();
            streamLength = result.getStreamRange().getLength().intValue();
            streamCount = result.getStreamRowCount().getCount().intValue();
            streamCountExact = result.getStreamRowCount().isExact();
            pageOffset = result.getPageRange().getOffset().intValue();
            pageLength = result.getPageRange().getLength().intValue();
            pageCount = result.getPageRowCount().getCount().intValue();
            pageCountExact = result.getPageRowCount().isExact();
        }

        streamRows.updateRowData(streamOffset, streamLength);
        streamRows.updateRowCount(streamCount, streamCountExact);
        pageRows.updateRowData(pageOffset, pageLength);
        pageRows.updateRowCount(pageCount, pageCountExact);

        textPresenter.setText(data, formatOnLoad);
        textPresenter.setFirstLineNumber(startLineNo);
        textPresenter.setControlsVisible(playButtonVisible);

        refreshHighlights(result);
        refreshMarkers(result);
    }

    private void refreshHighlights(final AbstractFetchDataResult result) {
        int streamNo = 0;

        if (result != null) {
            streamNo = result.getStreamRange().getOffset().intValue() + 1;
        }

        // Make sure we have a highlight section to add and that the stream id
        // matches that of the current page, and that the stream number matches
        // the stream number of the current page.
        if (highlights != null && currentMetaId != null && currentMetaId.equals(highlightId)
                && streamNo == highlightPartNo
                && EqualsUtil.isEquals(currentChildDataType, highlightChildDataType)) {
            // Set the content to be displayed in the source view with a
            // highlight.
            textPresenter.setHighlights(highlights);
        } else {
            // Set the content to be displayed in the source view without a
            // highlight.
            textPresenter.setHighlights(null);
        }
    }

    private void refreshMarkers(final AbstractFetchDataResult result) {
        int pageOffset = 0;
        int pageCount = 0;

        if (result != null) {
            pageOffset = result.getPageRange().getOffset().intValue();
            pageCount = result.getPageRowCount().getCount().intValue();
        }

        markerListPresenter.setData(markers, pageOffset, pageCount);
    }

    public void showStepSource(final Integer taskOffset, final StepLocation stepLocation,
                               final String childStreamType, final List<Highlight> highlights) {
        this.highlights = highlights;
        this.highlightId = stepLocation.getId();
        this.highlightPartNo = stepLocation.getPartNo();
        this.highlightChildDataType = childStreamType;

        // Set the source type that will be used when the page source is shown.
        // Make sure the right page is shown when the source is displayed.
        final long oldStreamNo = currentDataRange.getOffset() + 1;
        long newStreamNo = oldStreamNo;
        final long oldPageOffset = currentPageRange.getOffset();
        final long pageLength = currentPageRange.getLength();
        int lineNo = 0;

        // If we have a source highlight then use it.
        if (highlights != null && highlights.size() > 0) {
            final Highlight highlight = highlights.get(0);
            newStreamNo = highlightPartNo;
            lineNo = highlight.getFrom().getLineNo();
        }

        final long page = lineNo / pageLength;
        long newPageOffset = oldPageOffset % pageLength;
        final long tmp = page * pageLength;
        if (tmp + newPageOffset < lineNo) {
            // We can show this page.
            newPageOffset = tmp + newPageOffset;
        } else {
            // We need to show the page before.
            newPageOffset = tmp - pageLength + newPageOffset;
        }

        // Update the stream source.
        if (!EqualsUtil.isEquals(currentMetaId, highlightId)
                || !EqualsUtil.isEquals(currentChildDataType, highlightChildDataType) || oldStreamNo != newStreamNo
                || oldPageOffset != newPageOffset) {
            currentDataRange = new OffsetRange<>(newStreamNo - 1, 1L);
            currentPageRange = new OffsetRange<>(newPageOffset, pageLength);

            fetchData(false, highlightId, highlightChildDataType);
        } else {
            refreshHighlights(lastResult);
            refreshMarkers(lastResult);
        }
    }

    public void setBeginSteppingHandler(final BeginSteppingHandler beginSteppingHandler) {
        this.beginSteppingHandler = beginSteppingHandler;
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        this.classificationUiHandlers = classificationUiHandlers;
    }

    public void setSteppingSource(final boolean steppingSource) {
        this.steppingSource = steppingSource;
        errorMarkerMode = !steppingSource;
    }

    public void setFormatOnLoad(final boolean formatOnLoad) {
        this.formatOnLoad = formatOnLoad;
    }

    public interface DataView extends View {
        void setSegmentPagerRows(HasRows display);

        void showSegmentPager(boolean show);

        void setDataPagerRows(HasRows display);

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setRefreshing(boolean refreshing);
    }

    private static abstract class PagerRows implements HasRows {
        private final SimpleEventBus simpleEventBus = new SimpleEventBus();
        private Range visibleRange;
        private RowCount<Integer> rowCount = new RowCount<>(0, false);

        public PagerRows(final int length) {
            visibleRange = new Range(0, length);
        }

        @Override
        public void fireEvent(final GwtEvent<?> event) {
            simpleEventBus.fireEvent(event);
        }

        @Override
        public void setVisibleRange(final int start, final int length) {
            visibleRange = new Range(start, length);
            setVisibleRange(visibleRange);
        }

        @Override
        public Range getVisibleRange() {
            return visibleRange;
        }

        @Override
        public void setRowCount(final int count, final boolean exact) {
            rowCount = new RowCount<>(count, exact);
        }

        @Override
        public boolean isRowCountExact() {
            return rowCount.isExact();
        }

        @Override
        public int getRowCount() {
            return rowCount.getCount();
        }

        @Override
        public void setRowCount(final int count) {
            setRowCount(count, true);
        }

        @Override
        public com.google.gwt.event.shared.HandlerRegistration addRowCountChangeHandler(
                final com.google.gwt.view.client.RowCountChangeEvent.Handler handler) {
            return simpleEventBus.addHandler(RowCountChangeEvent.getType(), handler);
        }

        @Override
        public com.google.gwt.event.shared.HandlerRegistration addRangeChangeHandler(final Handler handler) {
            return simpleEventBus.addHandler(RangeChangeEvent.getType(), handler);
        }

        public void updateRowData(final int start, final int length) {
            visibleRange = new Range(start, length);
            RangeChangeEvent.fire(this, new Range(start, length));
        }

        public void updateRowCount(final int count, final boolean exact) {
            rowCount = new RowCount<>(count, exact);
            RowCountChangeEvent.fire(this, count, exact);
        }
    }

    private class PageRows extends PagerRows {
        private static final int SOURCE_PAGE_SIZE = 100;

        public PageRows() {
            super(SOURCE_PAGE_SIZE);
        }

        @Override
        public void setVisibleRange(final Range range) {
            currentDataRange = new OffsetRange<>((long) streamRows.visibleRange.getStart(), 1L);
            currentPageRange = new OffsetRange<>((long) range.getStart(), (long) range.getLength());
            update(false);
        }
    }

    private class StreamRows extends PagerRows {
        public StreamRows() {
            super(1);
        }

        @Override
        public void setVisibleRange(final Range range) {
            currentDataRange = new OffsetRange<>((long) range.getStart(), 1L);
            currentPageRange = new OffsetRange<>(0L, 100L);
            update(false);
        }
    }
}
