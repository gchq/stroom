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

import stroom.data.shared.DataRange;
import stroom.data.shared.DataType;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.ViewDataResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.HasCharacterData;
import stroom.util.shared.Location;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class DataPresenter extends MyPresenterWidget<DataPresenter.DataView> implements TextUiHandlers {
    private static final ViewDataResource VIEW_DATA_RESOURCE = GWT.create(ViewDataResource.class);

    // TODO @AT These need to come from config
    private static final long MAX_INITIAL_CHARS = 1_000L;
    private static final long MAX_CHARS_PER_FETCH = 10_000L;

    private static final DataRange DEFAULT_DATA_RANGE = DataRange.from(0, MAX_INITIAL_CHARS);

    private static final String META = "Meta";
    private static final String META_DATA = "Meta Data";
    private static final String CONTEXT = "Context";
    private static final String ERROR = "Error";
    private static final String CHARACTERS_PAGER_TITLE = "Characters";

    private final TabData errorTab = new TabDataImpl("Error");
    private final TabData dataTab = new TabDataImpl("Data");
    private final TabData metaTab = new TabDataImpl("Meta");
    private final TabData contextTab = new TabDataImpl("Context");

    private final TextPresenter textPresenter;
    private final MarkerListPresenter markerListPresenter;

    private final RestFactory restFactory;
//    private final PagerRows dataPagerRows;
    // TODO @AT rename to commonPagerRows
//    private final PagerRows segmentPagerRows;
    //    private final Map<String, OffsetRange<Long>> dataTypeOffsetRangeMap = new HashMap<>();

    private final Map<String, DataRange> dataTypeRangeMap = new HashMap<>();
    private final boolean userHasPipelineSteppingPermission;

    DataNavigatorData dataNavigatorData = new DataNavigatorData();

    private boolean errorMarkerMode = true;

    private Long currentMetaId;
    private String currentStreamType;
    private String currentChildDataType;

    private long currentPartNo;
    private long currentSegmentNo;
    private long currentErrorNo;

    private DataType curDataType;
    private SourceLocation currentSourceLocation;

//    private OffsetRange<Long> currentDataRange = new OffsetRange<>(0L, 1L);
//    private OffsetRange<Long> currentPageRange = new OffsetRange<>(0L, 100L);

    // The range to display on the current page
    private DataRange currentDataRange = DEFAULT_DATA_RANGE;

    private AbstractFetchDataResult lastResult;
    private List<FetchDataRequest> actionQueue;
    private Timer delayedFetchDataTimer;
    private String data;
    private AceEditorMode editorMode = AceEditorMode.XML;
    private List<Marker> markers;
    private int startLineNo;

    private List<TextRange> highlights;
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
    public DataPresenter(final EventBus eventBus,
                         final DataView view,
                         final TextPresenter textPresenter,
                         final MarkerListPresenter markerListPresenter,
                         final ClientSecurityContext securityContext,
                         final RestFactory restFactory) {
        super(eventBus, view);
        this.textPresenter = textPresenter;
        // Use properties mode for meta
        this.markerListPresenter = markerListPresenter;
        this.restFactory = restFactory;

        markerListPresenter.getWidget().getElement().getStyle().setWidth(100, Unit.PCT);
        markerListPresenter.getWidget().getElement().getStyle().setHeight(100, Unit.PCT);
        markerListPresenter.setDataPresenter(this);

        textPresenter.setUiHandlers(this);

//        dataPagerRows = new PageRows();
//        segmentPagerRows = new SegmentRows();
//        segmentPagerRows = new SimplePagerRows(1);
//        view.setDataPagerRows(dataPagerRows);
//        view.setSegmentPagerRows(segmentPagerRows);

        // Don't want to see x to y of z, want x of y for the part pager
//        view.setSegmentPagerToVisibleState(false);
//        view.setDataPagerTitle(CHARACTERS_PAGER_TITLE);

        dataNavigatorData = new DataNavigatorData();

        view.setNavigatorData(dataNavigatorData);

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

        registerHandler(getView().getTabBar().addSelectionHandler(event ->
                selectTab(event.getSelectedItem())));
    }

    private void selectTab(final TabData tab) {
        // Make sure tabs don't do anything in stepping mode.
        if (!steppingSource) {
            if (tab != null) {
                if (META.equals(tab.getLabel())) {
                    editorMode = AceEditorMode.PROPERTIES;
                    fetchDataForCurrentStreamNo(META_DATA);
                } else if (CONTEXT.equals(tab.getLabel())) {
                    editorMode = AceEditorMode.XML;
                    fetchDataForCurrentStreamNo(CONTEXT);
                } else if (ERROR.equals(tab.getLabel())) {
                    errorMarkerMode = true;
                    editorMode = AceEditorMode.TEXT;
                    fetchDataForCurrentStreamNo(null);
                } else {
                    // Turn off error marker mode if we are currently looking at
                    // an error and switching to the data tab.
                    if (ERROR.equals(currentStreamType) && errorMarkerMode) {
                        errorMarkerMode = false;
                        // Error textual data so display as text
                        editorMode = AceEditorMode.TEXT;
                    } else {
                        // Any old data so treat as XML
                        editorMode = AceEditorMode.XML;
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
//        currentDataRange = new OffsetRange<>(currentDataRange.getOffset(), 1L);

        dataTypeRangeMap.put(currentChildDataType, currentDataRange);
        currentDataRange = dataTypeRangeMap.get(childDataType);

        if (currentDataRange == null) {
//            currentPageRange = new OffsetRange<>(0L, 100L);
            currentDataRange = DEFAULT_DATA_RANGE;
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
//        final long oldPartNo = currentDataRange.getOffset() + 1;
        final long oldPartNo = currentPartNo;

        long newPartNo = sourceLocation.getPartNo();

        final long oldSegmentNo = currentSegmentNo;

//        long recordLength = currentPageRange.getLength();

        // If we have a source highlight then use it
//        int lineNo = 0;
//        if (highlights != null && highlights.size() > 0) {
//            final Highlight highlight = highlights.get(0);
//            lineNo = highlight.getFrom().getLineNo();
//        }
        if (sourceLocation.getOptDataRange().isPresent()) {
            currentDataRange = sourceLocation.getDataRange();
        }

//        long newRecordOffset;
//        if (sourceLocation.getSegmentNo() != -1) {
        // Segmented data
//            recordLength = 1L;
//            newRecordOffset = sourceLocation.getSegmentNo();
//        } else {
        // Non segmented data, aka raw
//            final long page = lineNo / recordLength;
//            newRecordOffset = oldSegmentNo % recordLength;
//            final long tmp = page * recordLength;
//            if (tmp + newRecordOffset < lineNo) {
//                // We can show this page.
//                newRecordOffset = tmp + newRecordOffset;
//            } else {
//                // We need to show the page before.
//                newRecordOffset = tmp - recordLength + newRecordOffset;
//            }
//        }

        // Update the stream source.
        if (!Objects.equals(currentSourceLocation, sourceLocation)) {
            // New data location so re-fetch
//        }
//        if (!EqualsUtil.isEquals(currentMetaId, highlightId)
//                || !EqualsUtil.isEquals(currentChildDataType, highlightChildDataType)
//                || oldPartNo != newPartNo
//                || oldSegmentNo != newRecordOffset) {
//            currentDataRange = new OffsetRange<>(newPartNo - 1, 1L);
            currentPartNo = sourceLocation.getPartNo();
            currentSegmentNo = sourceLocation.getOptSegmentNo()
                    .orElse(-1);
            currentDataRange = sourceLocation.getDataRange();

            currentSourceLocation = sourceLocation;
            currentMetaId = sourceLocation.getId();
            currentChildDataType = sourceLocation.getChildType();
            dataTypeRangeMap.clear();
            markerListPresenter.resetExpandedSeverities();

            update(false);
        } else {
            refreshHighlights(lastResult);
            refreshMarkers(lastResult);
        }
    }

    public void fetchData(final boolean fireEvents, final Long metaId, final String childDataType) {
        currentMetaId = metaId;
        currentChildDataType = childDataType;
        currentPartNo = 0;
        currentSegmentNo = 0;
        currentDataRange = DEFAULT_DATA_RANGE;
        dataTypeRangeMap.clear();
        markerListPresenter.resetExpandedSeverities();
        update(fireEvents);
    }

    public void fetchData(final Meta meta) {
        this.currentMetaId = meta.getId();
        this.currentStreamType = meta.getTypeName();
        currentPartNo = 0;
        currentSegmentNo = 0;
//        currentDataRange = new OffsetRange<>(0L, 1L);
        currentDataRange = DEFAULT_DATA_RANGE;
        dataTypeRangeMap.clear();
        markerListPresenter.resetExpandedSeverities();
        update(true);
    }

    public void update(final boolean fireEvents) {
        if (!ignoreActions) {
            final Severity[] expandedSeverities = markerListPresenter.getExpandedSeverities();

            long charOffset = currentDataRange != null && currentDataRange.getOptCharOffsetFrom().isPresent()
                    ? currentDataRange.getCharOffsetFrom()
                    : 0;

            // TODO @AT Do we need to pass the highlight?
            final FetchDataRequest request = new FetchDataRequest(currentMetaId, builder -> builder
                    .withPartNo(currentPartNo)
                    .withSegmentNumber(currentSegmentNo)
//                    .withDataRange(DataRange.from(charOffset))
                    .withDataRange(currentDataRange != null
                            ? currentDataRange
                            : DEFAULT_DATA_RANGE)
//                    .withHighlight(highlights.get(0))
                    .withChildStreamType(currentChildDataType));

//            request.setStreamId(currentMetaId);
//            request.setStreamRange(currentDataRange);
//            request.setPageRange(currentPageRange);
//            request.setChildStreamType(currentChildDataType);
            request.setMarkerMode(errorMarkerMode);
            request.setExpandedSeverities(expandedSeverities);
            request.setFireEvents(fireEvents);
            doFetch(request, fireEvents);
        }
    }

    private void doFetch(final FetchDataRequest request, final boolean fireEvents) {
        if (currentMetaId != null) {
            getView().setRefreshing(true);

            // Create the action queue and delayed refresh timer if we haven't
            // already.
            ensureActionQueue();

            // Add the action and schedule the timer.
            actionQueue.add(request);
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
                        final FetchDataRequest request = actionQueue.get(actionQueue.size() - 1);
                        actionQueue.clear();

                        final Rest<AbstractFetchDataResult> rest = restFactory.create();
                        rest
                                .onSuccess(result -> {
                                    // If we are queueing more actions then don't
                                    // update the text.
                                    if (actionQueue.size() == 0) {
                                        setPageResponse(result, request.isFireEvents());
                                        getView().setRefreshing(false);
                                    }
                                })
                                .onFailure(caught -> getView().setRefreshing(false))
                                .call(VIEW_DATA_RESOURCE)
                                .fetch(request);
                    }
                }
            };
        }
    }

    private void setPagers(final AbstractFetchDataResult result) {
        long commonPagerOffset = 0;
        long commonPagerLength = 0;
        long commonPagerCount = 0;
        boolean commonPagerCountExact = true;

        long dataPagerOffset = 0;
        long dataPagerLength = 0;
        long dataPagerCount = 0;
        boolean dataPagerCountExact = true;

        if (result != null) {
            commonPagerOffset = result.getItemRange().getOffset();
            commonPagerLength = result.getItemRange().getLength();
            commonPagerCount = result.getTotalItemCount().getCount();
            commonPagerCountExact = result.getTotalItemCount().isExact();


            if (result instanceof FetchMarkerResult) {
                // Error: a of b
//                getView().showSegmentPager(true);
//                getView().showDataPager(false);

//                getView().setSegmentPagerTitle("Error");
//                segmentPagerRows.setVisibleRangeHandler(this::handleSegmentNoRangeChange);
//                getView().setSegmentPagerToVisibleState(true);

                dataNavigatorData.updateSegmentsCount(result.getTotalItemCount());

//                commonPagerOffset = result.getPageRange().getOffset().intValue();

            } else if (result instanceof FetchDataResult) {
                // Make it one based
                dataPagerOffset = result.getSourceLocation()
                        .getDataRange()
                        .getOptCharOffsetFrom()
                        .orElse(0L);
                dataPagerLength = result.getSourceLocation().getDataRange().getLength();
                dataPagerCount = result.getTotalCharacterCount().getCount();
                dataPagerCountExact = result.getTotalCharacterCount().isExact();

                FetchDataResult fetchDataResult = (FetchDataResult) result;
//                getView().showDataPager(true);
//                getView().setDataPagerToVisibleState(true);

                if (DataType.SEGMENTED.equals(fetchDataResult.getDataType())) {
                    // Record: a of b   Characters: x to y of z

//                    getView().showSegmentPager(true);

//                    getView().setSegmentPagerTitle("Record");
//                    segmentPagerRows.setVisibleRangeHandler(this::handleSegmentNoRangeChange);

//                    getView().setSegmentPagerToVisibleState(false);

                    dataNavigatorData.updateSegmentsCount(result.getTotalItemCount());

                } else {
                    // non-segmented
                    //    Part: a of b   Characters: x to y of z
                    // OR                Characters: x to y of z

                    // Only show part pager
//                    getView().showSegmentPager(result.getTotalItemCount().getCount() > 1);

//                    getView().setSegmentPagerTitle("Part");
//                    segmentPagerRows.setVisibleRangeHandler(this::handlePartNoRangeChange);

                    dataNavigatorData.updatePartsCount(result.getTotalItemCount());
                }
            }


//            GWT.log("Segment Pager Offset: " + dataPagerOffset
//                    + " Length: " + dataPagerLength
//                    + " Total: " + dataPagerCount
//                    + " Exact: " + (dataPagerCountExact ? "EXACT" : "NON-EXACT")
//                    + " type: " + (result instanceof FetchDataResult ? ((FetchDataResult) result).getDataType() : "-"));
//
//            GWT.log("Data Pager Offset: " + commonPagerOffset
//                    + " Length: " + commonPagerLength
//                    + " Total: " + commonPagerCount
//                    + " Exact: " + (commonPagerCountExact ? "EXACT" : "NON-EXACT")
//                    + " type: " + (result instanceof FetchDataResult ? ((FetchDataResult) result).getDataType() : "-"));




//            segmentPagerRows.updateRowData((int) commonPagerOffset, (int) commonPagerLength);
//            segmentPagerRows.updateRowCount((int) commonPagerCount, commonPagerCountExact);

//            dataPagerRows.updateRowData((int) dataPagerOffset, (int) dataPagerLength);
//            dataPagerRows.updateRowCount((int) dataPagerCount, dataPagerCountExact);
        } else {
//            getView().showSegmentPager(false);
//            getView().showDataPager(false);
        }
    }

//    private void handleErrorRangeChange(final Range range) {
//        GWT.log("Error range: " + range.getStart() + ":" + range.getLength());
//        currentErrorNo = range.getStart();
//        update(false);
//    }

//    private void handlePartNoRangeChange(final Range range) {
//        GWT.log("Part range: " + range.getStart() + ":" + range.getLength());
//        currentPartNo = range.getStart();
//        update(false);
//    }
//
//    private void handleSegmentNoRangeChange(final Range range) {
//        GWT.log("Segment range: " + range.getStart() + ":" + range.getLength());
//        currentSegmentNo = range.getStart();
//        update(false);
//    }

    private void setPageResponse(final AbstractFetchDataResult result,
                                 final boolean fireEvents) {
        ignoreActions = true;
        this.lastResult = result;

        if (result == null || result.getStreamTypeName() == null || steppingSource) {
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
                curDataType = DataType.MARKER;

//                getView().setDataPagerTitle("Errors");
//                getView().setDataPagerToVisibleState(true);

            } else if (result instanceof FetchDataResult) {
                final FetchDataResult fetchDataResult = (FetchDataResult) result;
                data = fetchDataResult.getData();
                curDataType = fetchDataResult.getDataType();

                if (DataType.SEGMENTED.equals(fetchDataResult.getDataType())) {
//                    getView().setDataPagerTitle("Record");
//                    getView().setDataPagerToVisibleState(false);
                    textPresenter.setWrapLines(false);
                } else {
//                    getView().setDataPagerTitle("Characters");
//                    getView().setDataPagerToVisibleState(true);
                    final int lineCount = result.getSourceLocation().getOptDataRange()
                            .flatMap(DataRange::getLineCount)
                            .orElse(0);

                    // This may conflict with what the user has selected with the right click menu
                    if (lineCount == 1) {
                        textPresenter.setWrapLines(true);
                    } else {
                        textPresenter.setWrapLines(false);
                    }
                }
            }


//            startLineNo = result.getPageRange().getOffset().intValue() + 1;
            startLineNo = result.getSourceLocation().getOptDataRange()
                    .flatMap(DataRange::getOptLocationFrom)
                    .map(Location::getLineNo)
                    .orElse(1);

            // Update the paging controls.
//            getView().showSegmentPager(result.getStreamRowCount().getCount() > 1);

            // Let the classification handler know that the classification has
            // changed.
            classificationUiHandlers.setClassification(result.getClassification());

            refresh(result);
            updateTabs(result.getStreamTypeName(), result.getAvailableChildStreamTypes());

        } else {
//            getView().showSegmentPager(false);
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

        setPagers(result);

        textPresenter.setMode(editorMode);
        // Only want to try to format (which formats as XML) if we know the
        // data is likely to be XML, else it can mess up the formatting of error text.
        boolean isFormatted = formatOnLoad && AceEditorMode.XML.equals(editorMode);
        textPresenter.setText(data, isFormatted);
        textPresenter.setFirstLineNumber(startLineNo);
        textPresenter.setControlsVisible(playButtonVisible);

        getView().refreshNavigator();

        refreshHighlights(result);
        refreshMarkers(result);
    }

    private void refreshHighlights(final AbstractFetchDataResult result) {
        int partNo = 0;

        if (result != null) {
//            partNo = (int) (result.getSourceLocation().getPartNo() + 1);
            partNo = (int) (result.getSourceLocation().getPartNo());
        }

        // Make sure we have a highlight section to add and that the stream id
        // matches that of the current page, and that the stream number matches
        // the stream number of the current page.
        // TODO @AT fix highlighting
        if (highlights != null
                && currentMetaId != null
                && currentMetaId.equals(highlightId)
                && partNo == highlightPartNo
                && EqualsUtil.isEquals(currentChildDataType, highlightChildDataType)) {
            // Set the content to be displayed in the source view with a
            // highlight.
            textPresenter.setHighlights(highlights);
        } else {
            // Set the content to be displayed in the source view without a
            // highlight.
            textPresenter.setHighlights(null);
        }
//        if (highlights != null && currentMetaId != null && currentMetaId.equals(highlightId)
//                && streamNo == highlightPartNo
//                && EqualsUtil.isEquals(currentChildDataType, highlightChildDataType)) {
//            // Set the content to be displayed in the source view with a
//            // highlight.
//            textPresenter.setHighlights(highlights);
//        } else {
//            // Set the content to be displayed in the source view without a
//            // highlight.
//            textPresenter.setHighlights(null);
//        }
    }

    private void refreshMarkers(final AbstractFetchDataResult result) {
        int pageOffset = 0;
        int pageCount = 0;

        if (result != null) {
//            pageOffset = result.getPageRange().getOffset().intValue();
            pageCount = result.getTotalItemCount().getCount().intValue();
        }

        markerListPresenter.setData(markers, pageOffset, pageCount);
    }

    // No longer used
//    public void showStepSource(final Integer taskOffset,
//                               final StepLocation stepLocation,
//                               final String childStreamType,
//                               final List<Highlight> highlights) {
//        this.highlights = highlights;
//        this.highlightId = stepLocation.getId();
//        this.highlightPartNo = stepLocation.getPartNo();
//        this.highlightChildDataType = childStreamType;
//
//        // Set the source type that will be used when the page source is shown.
//        // Make sure the right page is shown when the source is displayed.
//        final long oldStreamNo = currentDataRange.getOffset() + 1;
//        long newStreamNo = oldStreamNo;
//        final long oldPageOffset = currentPageRange.getOffset();
//        final long pageLength = currentPageRange.getLength();
//        int lineNo = 0;
//
//        // If we have a source highlight then use it.
//        if (highlights != null && highlights.size() > 0) {
//            final Highlight highlight = highlights.get(0);
//            newStreamNo = highlightPartNo;
//            lineNo = highlight.getFrom().getLineNo();
//        }
//
//        final long page = lineNo / pageLength;
//        long newPageOffset = oldPageOffset % pageLength;
//        final long tmp = page * pageLength;
//        if (tmp + newPageOffset < lineNo) {
//            // We can show this page.
//            newPageOffset = tmp + newPageOffset;
//        } else {
//            // We need to show the page before.
//            newPageOffset = tmp - pageLength + newPageOffset;
//        }
//
//        // Update the stream source.
//        if (!EqualsUtil.isEquals(currentMetaId, highlightId)
//                || !EqualsUtil.isEquals(currentChildDataType, highlightChildDataType) || oldStreamNo != newStreamNo
//                || oldPageOffset != newPageOffset) {
//            currentDataRange = new OffsetRange<>(newStreamNo - 1, 1L);
//            currentPageRange = new OffsetRange<>(newPageOffset, pageLength);
//
//            fetchData(false, highlightId, highlightChildDataType);
//        } else {
//            refreshHighlights(lastResult);
//            refreshMarkers(lastResult);
//        }
//    }

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

    private long getCurrentPartNo() {
        return currentPartNo;
    }

    private long getCurrentSegmentNo() {
        return currentSegmentNo;
    }

    private DataType getCurDataType() {
        return curDataType;
    }

    private DataRange getCurrentDataRange() {
        return currentDataRange;
    }

    private AbstractFetchDataResult getLastResult() {
        return lastResult;
    }

    public interface DataView extends View {

//        void setSegmentPagerRows(HasRows display);

//        void showSegmentPager(boolean show);
//
//        void showDataPager(boolean show);
//
//        // TODO @AT
//        void setSegmentPagerToVisibleState(final boolean isVisible);
//
//        void setDataPagerRows(HasRows display);
//
//        void setSegmentPagerTitle(final String title);
//
//        void setDataPagerTitle(final String title);
//
//        void setDataPagerToVisibleState(final boolean isVisible);

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setNavigatorData(final HasCharacterData dataNavigatorData);

        void refreshNavigator();

        void setRefreshing(boolean refreshing);
    }

//    private static abstract class PagerRows implements HasRows {
//        private final SimpleEventBus simpleEventBus = new SimpleEventBus();
//        private Range visibleRange;
//        private RowCount<Integer> rowCount = new RowCount<>(0, false);
//        private Consumer<Range> visibleRangeHandler;
//
//        public PagerRows(final int length) {
//            visibleRange = new Range(0, length);
//        }
//
//        @Override
//        public void fireEvent(final GwtEvent<?> event) {
//            simpleEventBus.fireEvent(event);
//        }
//
//        @Override
//        public void setVisibleRange(final int start, final int length) {
//            visibleRange = new Range(start, length);
//            setVisibleRange(visibleRange);
//        }
//
//        @Override
//        public void setVisibleRange(final Range visibleRange) {
//            if (visibleRangeHandler != null) {
//                visibleRangeHandler.accept(visibleRange);
//            }
//        }
//
//        public void setVisibleRangeHandler(final Consumer<Range> visibleRangeHandler) {
//            this.visibleRangeHandler = visibleRangeHandler;
//        }
//
//        @Override
//        public Range getVisibleRange() {
//            return visibleRange;
//        }
//
//        @Override
//        public void setRowCount(final int count, final boolean exact) {
//            rowCount = new RowCount<>(count, exact);
//        }
//
//        @Override
//        public boolean isRowCountExact() {
//            return rowCount.isExact();
//        }
//
//        @Override
//        public int getRowCount() {
//            return rowCount.getCount();
//        }
//
//        @Override
//        public void setRowCount(final int count) {
//            setRowCount(count, true);
//        }
//
//        @Override
//        public com.google.gwt.event.shared.HandlerRegistration addRowCountChangeHandler(
//                final com.google.gwt.view.client.RowCountChangeEvent.Handler handler) {
//            return simpleEventBus.addHandler(RowCountChangeEvent.getType(), handler);
//        }
//
//        @Override
//        public com.google.gwt.event.shared.HandlerRegistration addRangeChangeHandler(final Handler handler) {
//            return simpleEventBus.addHandler(RangeChangeEvent.getType(), handler);
//        }
//
//        public void updateRowData(final int start, final int length) {
//            visibleRange = new Range(start, length);
//            RangeChangeEvent.fire(this, new Range(start, length));
//        }
//
//        public void updateRowCount(final int count, final boolean exact) {
//            rowCount = new RowCount<>(count, exact);
//            RowCountChangeEvent.fire(this, count, exact);
//        }
//    }
//
//
//    private class PageRows extends PagerRows {
//        // TODO @AT Get from config, same one as DataFetcher
//        private static final int SOURCE_PAGE_SIZE = 1000;
//
//        public PageRows() {
//            super(SOURCE_PAGE_SIZE);
//        }
//
//        @Override
//        public void setVisibleRange(final Range range) {
//            GWT.log("Data range: " + range.getStart() + ":" + range.getLength());
//            // TODO @AT fix paging
////            currentPageRange = new OffsetRange<>((long) range.getStart(), (long) range.getLength());
//            currentDataRange = DataRange.from(range.getStart(), range.getLength());
//            update(false);
//        }
//    }


//    private class SegmentRows extends PagerRows {
//        public SegmentRows() {
//            super(1);
//        }
//
//        @Override
//        public void setVisibleRange(final Range range) {
//            GWT.log("Part range: " + range.getStart() + ":" + range.getLength());
//            // Only one part at a time so don't care about range length
//            currentPartNo = range.getStart();
//            update(false);
//        }
//    }

//    private class SimplePagerRows extends PagerRows {
//
//        public SimplePagerRows(final int length) {
//            super(length);
//        }
//
//        @Override
//        public Range getVisibleRange() {
//            final Range range = super.getVisibleRange();
//            // TODO @AT should be consistent with DataFetcher
//            return new Range(range.getStart(), 10_000);
//        }
//    }

    private class DataNavigatorData implements HasCharacterData {

        private RowCount<Long> partsCount = RowCount.of(0L, false);
        private RowCount<Long> segmentsCount = RowCount.of(0L, false);

        public void updatePartsCount(final RowCount<Long> partsCount) {
            this.partsCount = partsCount;
        }

        public void updateSegmentsCount(final RowCount<Long> segmentsCount) {
            this.segmentsCount = segmentsCount;
        }

        @Override
        public boolean isMultiPart() {
            // For now assume segmented and multi-part are mutually exclusive
            return DataType.NON_SEGMENTED.equals(getCurDataType());
        }

        @Override
        public Optional<Long> getPartNo() {
            return Optional.of(getCurrentPartNo());
//            return Optional.ofNullable(currentSourceLocation)
//                    .map(SourceLocation::getPartNo);
        }

        @Override
        public Optional<Long> getTotalParts() {
            return Optional.ofNullable(partsCount)
                    .filter(RowCount::isExact)
                    .map(RowCount::getCount);
        }

        @Override
        public void setPartNo(final long partNo) {
            currentPartNo = partNo;
            showHeadCharacters();
        }

        @Override
        public boolean isSegmented() {
            return DataType.SEGMENTED.equals(getCurDataType())
                    || DataType.MARKER.equals(getCurDataType());
        }

        @Override
        public Optional<Long> getSegmentNoFrom() {
            final AbstractFetchDataResult lastResult = getLastResult();

            if (lastResult != null && isSegmented()) {
                return Optional.ofNullable(lastResult)
                        .map(AbstractFetchDataResult::getItemRange)
                        .map(OffsetRange::getOffset);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Long> getSegmentNoTo() {
            final AbstractFetchDataResult lastResult = getLastResult();

            if (lastResult != null && isSegmented()) {
                return Optional.ofNullable(lastResult)
                        .map(AbstractFetchDataResult::getItemRange)
                        .map(range -> range.getOffset() + range.getLength() - 1);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Long> getTotalSegments() {
            return Optional.ofNullable(segmentsCount)
                    .filter(RowCount::isExact)
                    .map(RowCount::getCount);
        }

        @Override
        public Optional<String> getSegmentName() {
            final AbstractFetchDataResult lastResult = getLastResult();
            if (lastResult == null) {
                return Optional.empty();
            } else if (DataType.MARKER.equals(curDataType)) {
                return Optional.of(ERROR);
            } else if (DataType.SEGMENTED.equals(curDataType)) {
                return Optional.of("Record");
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void setSegmentNoFrom(final long segmentNo) {
            currentSegmentNo = segmentNo;
            update(false);
        }

        @Override
        public Optional<Long> getCharFrom() {
            return Optional.ofNullable(getLastResult())
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .flatMap(DataRange::getOptCharOffsetFrom);
        }

        @Override
        public Optional<Long> getCharTo() {
            return Optional.ofNullable(getLastResult())
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .flatMap(DataRange::getOptCharOffsetTo);
        }

        @Override
        public Optional<Long> getTotalChars() {
            return Optional.ofNullable(getLastResult())
                    .flatMap(result -> Optional.ofNullable(result.getTotalCharacterCount()))
                    .filter(RowCount::isExact)
                    .map(RowCount::getCount);
        }

        @Override
        public void showHeadCharacters() {
            currentDataRange = DataRange.from(0, MAX_INITIAL_CHARS - 1);
            update(false);
        }

        @Override
        public void advanceCharactersForward() {
            if (Long.valueOf(0).equals(currentDataRange.getCharOffsetFrom())) {
                currentDataRange = DataRange.from(
                        currentDataRange.getCharOffsetFrom() + MAX_INITIAL_CHARS,
                        MAX_CHARS_PER_FETCH);
            } else {
                currentDataRange = DataRange.from(
                        currentDataRange.getCharOffsetFrom() + MAX_CHARS_PER_FETCH,
                        MAX_CHARS_PER_FETCH);
            }
            update(false);
        }

        @Override
        public void advanceCharactersBackwards() {
            currentDataRange = DataRange.from(
                    currentDataRange.getCharOffsetFrom() - MAX_CHARS_PER_FETCH,
                    MAX_CHARS_PER_FETCH);
            update(false);
        }

        @Override
        public void refresh() {
            update(false);
        }
    }
}
