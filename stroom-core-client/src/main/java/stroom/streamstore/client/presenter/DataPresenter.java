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
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.StepLocation;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamType;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.Highlight;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.Severity;
import stroom.util.shared.SharedList;
import stroom.widget.tab.client.presenter.LayerContainer;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DataPresenter extends MyPresenterWidget<DataPresenter.DataView> implements TextUiHandlers {
    private final TabData errorTab = new TabDataImpl("Error");
    private final TabData dataTab = new TabDataImpl("Data");
    private final TabData metaTab = new TabDataImpl("Meta");
    private final TabData contextTab = new TabDataImpl("Context");
    private final TextPresenter textPresenter;
    private final MarkerListPresenter markerListPresenter;
    private final ClientDispatchAsync dispatcher;
    private final PagerRows pageRows;
    private final PagerRows streamRows;
    private final Map<StreamType, OffsetRange<Long>> streamTypeOffsetRangeMap = new HashMap<>();
    private final boolean userHasPipelineSteppingPermission;

    private boolean errorMarkerMode = true;
    private Long currentStreamId;
    private StreamType currentStreamType;
    private StreamType currentChildStreamType;
    private OffsetRange<Long> currentStreamRange = new OffsetRange<>(0L, 1L);
    private OffsetRange<Long> currentPageRange = new OffsetRange<>(0L, 100L);
    private AbstractFetchDataResult lastResult;
    private List<FetchDataAction> actionQueue;
    private Timer delayedFetchDataTimer;
    private String data;
    private SharedList<Marker> markers;
    private int startLineNo;
    private List<Highlight> highlights;
    private Long highlightStreamId;
    private Long highlightStreamNo;
    private StreamType highlightChildStreamType;
    private boolean playButtonVisible;
    private ClassificationUiHandlers classificationUiHandlers;
    private BeginSteppingHandler beginSteppingHandler;
    private boolean steppingSource;

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
                if ("Meta".equals(tab.getLabel())) {
                    fetchDataForCurrentStreamNo(StreamType.META);
                } else if ("Context".equals(tab.getLabel())) {
                    fetchDataForCurrentStreamNo(StreamType.CONTEXT);
                } else if ("Error".equals(tab.getLabel())) {
                    errorMarkerMode = true;
                    fetchDataForCurrentStreamNo(null);
                } else {
                    // Turn off error marker mode if we are currently looking at
                    // an error and switching to the data tab.
                    if (StreamType.ERROR.equals(currentStreamType) && errorMarkerMode) {
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
    }

    @Override
    public void beginStepping() {
        if (beginSteppingHandler != null) {
            beginSteppingHandler.beginStepping(currentStreamId, currentChildStreamType);
        }
    }

    private void fetchDataForCurrentStreamNo(final StreamType childStreamType) {
        currentStreamRange = new OffsetRange<>(currentStreamRange.getOffset(), 1L);

        streamTypeOffsetRangeMap.put(currentChildStreamType, currentPageRange);
        currentPageRange = streamTypeOffsetRangeMap.get(childStreamType);
        if (currentPageRange == null) {
            currentPageRange = new OffsetRange<>(0L, 100L);
        }

        this.currentChildStreamType = childStreamType;
        update(true);
    }

    public void fetchData(final boolean fireEvents, final Long streamId, final StreamType childStreamType) {
        this.currentStreamId = streamId;
        this.currentChildStreamType = childStreamType;
        currentStreamRange = new OffsetRange<>(0L, 1L);
        currentPageRange = new OffsetRange<>(0L, 100L);
        streamTypeOffsetRangeMap.clear();
        markerListPresenter.resetExpandedSeverities();
        update(fireEvents);
    }

    public void fetchData(final Stream stream) {
        this.currentStreamId = stream.getId();
        this.currentStreamType = stream.getStreamType();
        currentStreamRange = new OffsetRange<>(0L, 1L);
        currentPageRange = new OffsetRange<>(0L, 100L);
        streamTypeOffsetRangeMap.clear();
        markerListPresenter.resetExpandedSeverities();
        update(true);
    }

    public void update(final boolean fireEvents) {
        final Severity[] expandedSeverities = markerListPresenter.getExpandedSeverities();

        final FetchDataAction action = new FetchDataAction();
        action.setStreamId(currentStreamId);
        action.setStreamRange(currentStreamRange);
        action.setPageRange(currentPageRange);
        action.setChildStreamType(currentChildStreamType);
        action.setMarkerMode(errorMarkerMode);
        action.setExpandedSeverities(expandedSeverities);
        action.setFireEvents(fireEvents);
        doFetch(action, fireEvents);
    }

    private void doFetch(final FetchDataAction action, final boolean fireEvents) {
        if (currentStreamId != null) {
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
    }

    private void updateTabs(final StreamType streamType, final List<StreamType> availableChildStreamTypes) {
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
            if (errorMarkerMode && StreamType.ERROR.equals(streamType)) {
                getView().getTabBar().selectTab(errorTab);
                showMarkerPresenter();
            } else if (StreamType.META.equals(streamType)) {
                getView().getTabBar().selectTab(metaTab);
                showTextPresenter();
            } else if (StreamType.CONTEXT.equals(streamType)) {
                getView().getTabBar().selectTab(contextTab);
                showTextPresenter();
            } else {
                getView().getTabBar().selectTab(dataTab);
                showTextPresenter();
            }

            // Show only applicable links.
            hideTab(errorTab, !StreamType.ERROR.equals(currentStreamType));
            hideTab(dataTab, false);
            if (availableChildStreamTypes != null) {
                hideTab(metaTab, !availableChildStreamTypes.contains(StreamType.META));
                hideTab(contextTab, !availableChildStreamTypes.contains(StreamType.CONTEXT));
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

        textPresenter.setText(data);
        textPresenter.format();
        textPresenter.setFirstLineNumber(startLineNo);
        textPresenter.setControlsVisible(playButtonVisible);

        refreshHighlights(result);
        refreshMarkers(result);
    }

    private void refreshHighlights(final AbstractFetchDataResult result) {
        int streamOffset = 0;

        if (result != null) {
            streamOffset = result.getStreamRange().getOffset().intValue();
        }

        // Make sure we have a highlight section to add and that the stream id
        // matches that of the current page, and that the stream number matches
        // the stream number of the current page.
        if (highlights != null && currentStreamId != null && currentStreamId.equals(highlightStreamId)
                && streamOffset == highlightStreamNo
                && EqualsUtil.isEquals(currentChildStreamType, highlightChildStreamType)) {
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
                               final StreamType childStreamType, final List<Highlight> highlights) {
        this.highlights = highlights;
        this.highlightStreamId = stepLocation.getStreamId();
        this.highlightStreamNo = stepLocation.getStreamNo() - 1;
        this.highlightChildStreamType = childStreamType;

        // Set the source type that will be used when the page source is shown.
        // Make sure the right page is shown when the source is displayed.
        final long oldStreamNo = currentStreamRange.getOffset();
        long newStreamNo = oldStreamNo;
        final long oldPageOffset = currentPageRange.getOffset();
        final long pageLength = currentPageRange.getLength();
        int lineNo = 0;

        // If we have a source highlight then use it.
        if (highlights != null && highlights.size() > 0) {
            final Highlight highlight = highlights.get(0);
            newStreamNo = highlightStreamNo;
            lineNo = highlight.getLineFrom();
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
        if (!EqualsUtil.isEquals(currentStreamId, highlightStreamId)
                || !EqualsUtil.isEquals(currentChildStreamType, highlightChildStreamType) || oldStreamNo != newStreamNo
                || oldPageOffset != newPageOffset) {
            currentStreamRange = new OffsetRange<>(newStreamNo, 1L);
            currentPageRange = new OffsetRange<>(newPageOffset, pageLength);

            fetchData(false, highlightStreamId, highlightChildStreamType);
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
            visibleRange = new Range(start, visibleRange.getLength());
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
            currentStreamRange = new OffsetRange<>((long) streamRows.visibleRange.getStart(), 1L);
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
            currentStreamRange = new OffsetRange<>((long) range.getStart(), 1L);
            currentPageRange = new OffsetRange<>(0L, 100L);
            update(false);
        }
    }
}
