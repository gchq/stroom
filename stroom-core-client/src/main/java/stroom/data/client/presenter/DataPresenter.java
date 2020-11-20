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

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.SourceTabPlugin;
import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataResource;
import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.ViewDataResource;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.svg.client.SvgPresets;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.shared.DataRange;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.HasItems;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Count;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;
import stroom.widget.button.client.ButtonView;
import stroom.widget.button.client.ToggleButtonView;
import stroom.widget.progress.client.presenter.Progress;
import stroom.widget.progress.client.presenter.ProgressPresenter;
import stroom.widget.progress.client.presenter.ProgressPresenter.ProgressView;
import stroom.widget.tab.client.presenter.TabBar;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;
import stroom.widget.tooltip.client.presenter.TooltipUtil;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.FontWeight;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.dom.client.Style.VerticalAlign;
import com.google.gwt.safecss.shared.SafeStyles;
import com.google.gwt.safecss.shared.SafeStylesBuilder;
import com.google.gwt.safecss.shared.SafeStylesUtils;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlBuilder;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DataPresenter extends MyPresenterWidget<DataPresenter.DataView> implements TextUiHandlers {
    private static final ViewDataResource VIEW_DATA_RESOURCE = GWT.create(ViewDataResource.class);
    private static final DataResource DATA_RESOURCE = com.google.gwt.core.shared.GWT.create(DataResource.class);

    private static final SafeStyles META_SECTION_HEAD_STYLES = new SafeStylesBuilder()
            .paddingLeft(0.2, Unit.EM)
            .trustedColor("#1e88e5")
            .fontWeight(FontWeight.BOLD)
            .fontSize(125, Unit.PCT)
            .toSafeStyles();

    private static final SafeStyles META_SECTION_CELL_STYLES = new SafeStylesBuilder()
            .height(2, Unit.EM)
            .verticalAlign(VerticalAlign.BOTTOM)
            .toSafeStyles();

    private static final SafeStyles META_KEY_STYLES = new SafeStylesBuilder()
            .paddingLeft(1, Unit.EM)
            .paddingRight(1, Unit.EM)
            .append(SafeStylesUtils.fromTrustedNameAndValue("line-height", "1.4em"))
            .append(SafeStylesUtils.fromTrustedNameAndValue("font-weight", "500"))
            .toSafeStyles();

    private static final String CONTEXT = "Context";
    private static final String ERROR = "Error";
    private static final String INFO = "Info";
    private static final String META = "Meta";
    private static final String META_DATA = "Meta Data";
    private static final String RECORD = "Record";
    private static final String PAGE = "Page";
    private static final String PART = "Part";

    private final TabData errorTab = new TabDataImpl("Error");
    private final TabData dataTab = new TabDataImpl("Data Preview");
    private final TabData metaTab = new TabDataImpl("Meta");
    private final TabData infoTab = new TabDataImpl("Info");
    private final TabData contextTab = new TabDataImpl("Context");

    private final HtmlPresenter htmlPresenter;
    private final TextPresenter textPresenter;
    private final ItemNavigatorPresenter itemNavigatorPresenter;
    private final ProgressPresenter progressPresenter;
    private final MarkerListPresenter markerListPresenter;
    private final SourceTabPlugin sourceTabPlugin;
    private final SourceOpenSupport sourceOpenSupport;
    private final UiConfigCache uiConfigCache;

    private final RestFactory restFactory;
    private final boolean userHasPipelineSteppingPermission;

    final NoNavigatorData noNavigatorData = new NoNavigatorData();
    final NavigatorData navigatorData = new NavigatorData();

    private boolean errorMarkerMode = true;

    // TODO @AT Most of these currentXXX vars need to go and we just get/set on the currentSourceLocation
    //   We may need some concept of a requestedSourceLocation and a currentSourceLocation as what we get back
    //   may differ from what we asked for
    private Long currentMetaId;
    private String currentStreamType;
    private String currentChildDataType;
    private long currentPartNo;
    private long currentSegmentNo;

    private DataType curDataType;

    private SourceLocation currentSourceLocation;

    private AbstractFetchDataResult lastResult;
    private List<FetchDataRequest> actionQueue;
    private Timer delayedFetchDataTimer;
    private String data;
    private AceEditorMode editorMode = AceEditorMode.XML;
    private List<Marker> markers;

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
    private ButtonView viewSourceBtn;
    // Track the tab last used so if we switch streams we can select the same tab again if it has it
    private String lastTabName;


    @Inject
    public DataPresenter(final EventBus eventBus,
                         final HtmlPresenter htmlPresenter,
                         final ItemNavigatorPresenter itemNavigatorPresenter,
                         final DataView view,
                         final TextPresenter textPresenter,
                         final ProgressPresenter progressPresenter,
                         final MarkerListPresenter markerListPresenter,
                         final SourceTabPlugin sourceTabPlugin,
                         final SourceOpenSupport sourceOpenSupport,
                         final UiConfigCache uiConfigCache,
                         final ClientSecurityContext securityContext,
                         final RestFactory restFactory) {
        super(eventBus, view);
        this.htmlPresenter = htmlPresenter;
        this.itemNavigatorPresenter = itemNavigatorPresenter;
        this.textPresenter = textPresenter;
        this.progressPresenter = progressPresenter;
        // Use properties mode for meta
        this.markerListPresenter = markerListPresenter;
        this.sourceTabPlugin = sourceTabPlugin;
        this.sourceOpenSupport = sourceOpenSupport;
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;

        markerListPresenter.getWidget()
                .getElement()
                .getStyle()
                .setWidth(100, Unit.PCT);
        markerListPresenter.getWidget()
                .getElement()
                .getStyle()
                .setHeight(100, Unit.PCT);
        markerListPresenter.setDataPresenter(this);

        textPresenter.setUiHandlers(this);

        viewSourceBtn = view.addButton(SvgPresets.RAW.title("View source data"));
        viewSourceBtn.setEnabled(true);
        viewSourceBtn.setVisible(true);
        viewSourceBtn.addClickHandler(event -> {
            openSourcePresenter();
        });

        textPresenter.setWrapLines(true);

        addTab(infoTab);
        addTab(errorTab);
        addTab(dataTab);
        addTab(metaTab);
        addTab(contextTab);

        userHasPipelineSteppingPermission = securityContext.hasAppPermission(PermissionNames.STEPPING_PERMISSION);

        itemNavigatorPresenter.setDisplay(noNavigatorData);
        view.setNavigatorView(itemNavigatorPresenter.getView());
        view.setProgressView(progressPresenter.getView());
        progressPresenter.setVisible(false);
    }

    private void setCurrentSegmentNo(final long segmentNo) {
        this.currentSegmentNo = segmentNo;
    }

    public void setCurrentPartNo(final long currentPartNo) {
        this.currentPartNo = currentPartNo;
    }

    private void openSourcePresenter() {
        // No need to supply a data range as it will just open it with the default range
        // that is bigger than our preview range
        final SourceLocation sourceLocation = SourceLocation.builder(currentMetaId)
                .withPartNo(currentPartNo)
                .withSegmentNumber(currentSegmentNo)
                .withChildStreamType(currentChildDataType)
                .withHighlight(currentSourceLocation != null
                        ? currentSourceLocation.getHighlight()
                        : null)
                .build();

        ShowSourceEvent.fire(this, sourceLocation, DisplayMode.STROOM_TAB);
//        sourceTabPlugin.open(sourceLocation, true);
    }

    private void doWithConfig(final Consumer<SourceConfig> action) {
        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        action.accept(uiConfig.getSource()))
                .onFailure(caught -> AlertEvent.fireError(
                        DataPresenter.this,
                        caught.getMessage(),
                        null));
    }

    private void addTab(final TabData tab) {
        getView().getTabBar()
                .addTab(tab);
        hideTab(tab, true);
        // tabs.add(tab);
    }

    private void hideTab(final TabData tab, final boolean hide) {
        getView().getTabBar()
                .setTabHidden(tab, hide);
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
                if (INFO.equals(tab.getLabel())) {
                    getView().getTabBar().selectTab(infoTab);
                    showHtmlPresenter();
                    fetchMetaInfoData(currentMetaId);
                    viewSourceBtn.setVisible(false);
                    itemNavigatorPresenter.refreshNavigator();
                    refreshProgressBar(false);
                } else {
                    viewSourceBtn.setVisible(true);
                    if (META.equals(tab.getLabel())) {
                        editorMode = AceEditorMode.PROPERTIES;
                        fetchDataForCurrentStreamNo(META_DATA);
                        refreshProgressBar(false);
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
                    refreshProgressBar(true);
                }
                lastTabName = tab.getLabel();
            }
        }
    }

    private void updateEditorMode(final String streamType, final TabData tabData) {
       if (tabData != null && streamType != null) {
           final String tabName = tabData.getLabel();
           if (INFO.equals(tabName)) {
               editorMode = AceEditorMode.TEXT;
               refreshTextPresenterContent();
           } else if (errorMarkerMode && ERROR.equals(streamType)) {
               // Not a text editor
           } else if (!errorMarkerMode && ERROR.equals(streamType)) {
               editorMode = AceEditorMode.TEXT;
               refreshTextPresenterContent();
           } else if (META_DATA.equals(streamType)) {
               editorMode = AceEditorMode.PROPERTIES;
               refreshTextPresenterContent();
           } else if (CONTEXT.equals(streamType)) {
               editorMode = AceEditorMode.XML;
               refreshTextPresenterContent();
           } else {
               // Default to xml mode
               editorMode = AceEditorMode.XML;
               refreshTextPresenterContent();
           }
       }
    }

    private void refreshProgressBar(final boolean isVisible) {
        if (isVisible
                && lastResult != null
                && lastResult instanceof FetchDataResult
                && lastResult.getSourceLocation() != null
                && lastResult.getSourceLocation().getDataRange() != null) {

            final DataRange dataRange = lastResult.getSourceLocation().getDataRange();

            final FetchDataResult fetchDataResult = (FetchDataResult) lastResult;

            progressPresenter.setVisible(true);

            // Don't want to confuse the user so the prog bar need to be based on the char/byte content,
            // not segments in a segmented file. Thus for most segmented streams the segment will fit in the
            // data preview and thus will always see 100%
            if (!DataType.SEGMENTED.equals(fetchDataResult.getDataType())
                    && fetchDataResult.getOptTotalBytes().isPresent()
                    && dataRange.getOptByteOffsetFrom().isPresent()
                    && dataRange.getOptByteOffsetTo().isPresent()) {
                // progress based on know byte size and byte offsets
                progressPresenter.setProgress(Progress.boundedRange(
                        fetchDataResult.getTotalBytes() - 1, // count to zero based bound
                        dataRange.getByteOffsetFrom(),
                        dataRange.getByteOffsetTo()));
            } else if (fetchDataResult.getTotalCharacterCount().isExact()) {
                // progress based on known char count and char offsets
                progressPresenter.setProgress(Progress.boundedRange(
                        fetchDataResult.getTotalCharacterCount().getCount() - 1, // count to zero based bound
                        dataRange.getCharOffsetFrom(),
                        dataRange.getCharOffsetTo()));
            } else {
                // progress based on unknown char count and char offsets
                progressPresenter.setProgress(Progress.unboundedRange(
                        dataRange.getCharOffsetFrom(),
                        dataRange.getCharOffsetTo()));
            }
        } else {
            progressPresenter.setVisible(false);
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
        this.currentChildDataType = childDataType;
        update(true);
    }

    public void fetchData(final SourceLocation sourceLocation) {
        if (sourceLocation.getDataRange() != null) {
            // We are displaying a specific range of the data so hide the
            // nav controls
            getView().setNavigatorView(null);
        }
        this.highlights = new ArrayList<>();

        if (sourceLocation.getHighlight() != null) {
            this.highlights.add(sourceLocation.getHighlight());
        }

        this.highlightId = sourceLocation.getId();
        this.highlightPartNo = sourceLocation.getPartNo();
        this.highlightChildDataType = sourceLocation.getChildType();

        // Update the stream source.
        if (!Objects.equals(currentSourceLocation, sourceLocation)) {
            // New data location so re-fetch
            currentPartNo = sourceLocation.getPartNo();
            currentSegmentNo = sourceLocation.getOptSegmentNo()
                    .orElse(-1);
            currentSourceLocation = sourceLocation;
            currentMetaId = sourceLocation.getId();
            currentChildDataType = sourceLocation.getChildType();
            markerListPresenter.resetExpandedSeverities();

            update(false);
        } else {
            refreshHighlights(lastResult);
            refreshMarkers(lastResult);
        }
    }

    public void fetchData(final Meta meta) {
        this.currentMetaId = meta.getId();
        this.currentStreamType = meta.getTypeName();
        currentPartNo = 0;
        currentSegmentNo = 0;
        markerListPresenter.resetExpandedSeverities();
        update(true);
    }

    public void update(final boolean fireEvents) {
        if (!ignoreActions) {
            if (isSameStreamAndPartAsLastTime()) {
                // Same stream/part so we know this type is available and
                // therefore no need to update tabs as
                updateFromResource(fireEvents);
            } else {
                // Different stream/part so we need to check which child stream types are available
                // and pick an appropriate one.

                final Rest<Set<String>> rest = restFactory.create();
                rest
                        .onSuccess(availableChildStreamTypes -> {
                            if (INFO.equals(lastTabName)) {
                                refreshMetaInfoPresenterContent(currentMetaId);
                                updateTabs(currentStreamType, availableChildStreamTypes);
                            } else {
                                // Tabs will be updated by updateFromResource
                                setEffectiveChildStreamType(availableChildStreamTypes);
                                updateFromResource(fireEvents);
                            }
                        })
                        .onFailure(caught ->
                                itemNavigatorPresenter.setRefreshing(false))
                        .call(VIEW_DATA_RESOURCE)
                        .getChildStreamTypes(currentMetaId, currentPartNo);
            }
        }
    }

    private void setEffectiveChildStreamType(final Set<String> availableChildStreamTypes) {
//        GWT.log(currentStreamType + " - " + currentChildDataType + " - " + availableChildStreamTypes);

        if (currentChildDataType != null
                && !availableChildStreamTypes.contains(currentChildDataType)) {
            if (StreamTypeNames.ERROR.equals(currentStreamType)) {
                // See error markers by default
                errorMarkerMode = true;
            }
            // null child data type indicates to show the data
            currentChildDataType = null;
        }
    }

    private void updateFromResource(final boolean fireEvents) {
        final Severity[] expandedSeverities = markerListPresenter.getExpandedSeverities();

        doWithConfig(sourceConfig -> {
            final DataRange dataRange;
            // Error markers are a bit different
            if (StreamTypeNames.ERROR.equals(currentStreamType)) {
                dataRange = DataRange.fromCharOffset(0);
            } else if (StreamTypeNames.META.equals(currentChildDataType)) {
                dataRange = DataRange.fromCharOffset(0);
            } else if (currentSourceLocation != null && currentSourceLocation.getDataRange() != null) {
                // We have a specific range of data, i.e. when using the data() dash func.
                dataRange = currentSourceLocation.getDataRange();
            } else {
                dataRange = DataRange.fromCharOffset(0,
                        sourceConfig.getMaxCharactersInPreviewFetch());
            }
//            GWT.log("Using data range " + dataRange.toString());

            // TODO @AT Do we need to pass the highlight?
            final FetchDataRequest request = new FetchDataRequest(currentMetaId, builder -> {
                builder
                        .withPartNo(currentPartNo)
                        .withSegmentNumber(currentSegmentNo)
                        .withDataRange(dataRange)
                        .withChildStreamType(currentChildDataType);

                if (highlights != null && !highlights.isEmpty()) {
                        builder.withHighlight(highlights.get(0));
                }
            });

            request.setMarkerMode(errorMarkerMode);
            request.setExpandedSeverities(expandedSeverities);
            request.setFireEvents(fireEvents);
            doFetch(request, fireEvents);
        });
    }


    private boolean isSameStreamAndPartAsLastTime() {
        if (lastResult != null) {

            final Long lastId = Optional.ofNullable(lastResult)
                    .flatMap(result -> Optional.ofNullable(result.getSourceLocation()))
                    .map(SourceLocation::getId)
                    .orElse(null);
            final Long lastPartNo = Optional.ofNullable(lastResult)
                    .flatMap(result -> Optional.ofNullable(result.getSourceLocation()))
                    .map(SourceLocation::getPartNo)
                    .orElse(null);

            return Objects.equals(currentMetaId, lastId)
                    && Objects.equals(currentPartNo, lastPartNo);
        } else {
            return false;
        }
    }


    private void doFetch(final FetchDataRequest request, final boolean fireEvents) {
        if (currentMetaId != null) {
            itemNavigatorPresenter.setRefreshing(true);

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
                                        itemNavigatorPresenter.setRefreshing(false);
                                    }
                                })
                                .onFailure(caught -> itemNavigatorPresenter.setRefreshing(false))
                                .call(VIEW_DATA_RESOURCE)
                                .fetch(request);
                    }
                }
            };
        }
    }

    private void setPagers(final AbstractFetchDataResult result) {

        if (result instanceof FetchMarkerResult) {
            // Error: a of b
            final Count<Long> totalPageCount = Count.of(
                    result.getTotalItemCount().getCount() / SourceLocation.MAX_ERRORS_PER_PAGE + 1,
                    true);

            navigatorData.updateStateForOneItemPerPage(
                    totalPageCount,
                    this::setCurrentErrorsPageOffset,
                    this::getCurrentErrorsPageOffset,
                    PAGE);
        } else if (result instanceof FetchDataResult) {

            FetchDataResult fetchDataResult = (FetchDataResult) result;

            if (DataType.SEGMENTED.equals(fetchDataResult.getDataType())) {
                // Record: a of b   Characters: x to y of z
                navigatorData.updateStateForOneItemPerPage(
                        result.getTotalItemCount(),
                        this::setCurrentSegmentNo,
                        this::getCurrentSegmentNo,
                        RECORD);
            } else {
                // non-segmented
                //    Part: a of b   Characters: x to y of z
                // OR                Characters: x to y of z
                navigatorData.updateStateForOneItemPerPage(
                        result.getTotalItemCount(),
                        this::setCurrentPartNo,
                        this::getCurrentPartNo,
                        PART);
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
    }

    long getCurrentErrorsPageOffset() {
        return currentSegmentNo / SourceLocation.MAX_ERRORS_PER_PAGE;
    }

    void setCurrentErrorsPageOffset(final long pageOffset) {
        currentSegmentNo = pageOffset * SourceLocation.MAX_ERRORS_PER_PAGE;
    }

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

        if (result != null) {
            currentChildDataType = Optional.ofNullable(result.getSourceLocation())
                    .map(SourceLocation::getChildType)
                    .orElse(null);

            if (result instanceof FetchMarkerResult) {
                final FetchMarkerResult fetchMarkerResult = (FetchMarkerResult) result;
                markers = fetchMarkerResult.getMarkers();
                curDataType = DataType.MARKER;
            } else if (result instanceof FetchDataResult) {
                final FetchDataResult fetchDataResult = (FetchDataResult) result;
                data = fetchDataResult.getData();
                curDataType = fetchDataResult.getDataType();

                if (DataType.SEGMENTED.equals(fetchDataResult.getDataType())) {
                    textPresenter.setWrapLines(false);
                } else {
                    final int lineCount = result.getSourceLocation().getOptDataRange()
                            .flatMap(DataRange::getLineCount)
                            .orElse(0);

                    // This may conflict with what the user has selected with the right click menu
                    if (lineCount == 1) {
                        // No line breaks so we need to wrap else the line could be massive
                        textPresenter.setWrapLines(true);
                    } else {
                        // probably one record per line so for csv type data easier to read non-wrapped
                        textPresenter.setWrapLines(false);
                    }
                    textPresenter.setWrapLines(lineCount == 1);
                }
            }

            // Let the classification handler know that the classification has
            // changed.
            classificationUiHandlers.setClassification(result.getClassification());

            refresh(result);
            updateTabs(result.getStreamTypeName(), result.getAvailableChildStreamTypes());
        } else {
            classificationUiHandlers.setClassification("");

            refresh(result);
            updateTabs(null, null);
        }
        ignoreActions = false;
    }

    private void updateTabs(final String streamType,
                            final Set<String> availableChildStreamTypes) {

        if (streamType == null) {
            // Hide all links
            hideTab(infoTab, true);
            hideTab(errorTab, true);
            hideTab(dataTab, true);
            hideTab(metaTab, true);
            hideTab(contextTab, true);

            showHtmlPresenter();
            itemNavigatorPresenter.setDisplay(noNavigatorData);
        } else {
            // Highlight the appropriate link.
            if (INFO.equals(lastTabName)) {
                getView().getTabBar().selectTab(infoTab);
                updateEditorMode(streamType, infoTab);
                showHtmlPresenter();
            } else if (errorMarkerMode && ERROR.equals(streamType)) {
                getView().getTabBar().selectTab(errorTab);
                updateEditorMode(streamType, errorTab);
                showMarkerPresenter();
            } else if (META_DATA.equals(streamType)) {
                getView().getTabBar().selectTab(metaTab);
                updateEditorMode(streamType, metaTab);
                showTextPresenter();
            } else if (CONTEXT.equals(streamType)) {
                getView().getTabBar().selectTab(contextTab);
                updateEditorMode(streamType, contextTab);
                showTextPresenter();
            } else {
                getView().getTabBar().selectTab(dataTab);
                updateEditorMode(streamType, dataTab);
                showTextPresenter();
            }

            // Always have an info tab for a stream
            hideTab(infoTab, false);

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
            // Now we have changed tabs, ensure the nav visibility is right
            itemNavigatorPresenter.refreshNavigator();
        }
        lastTabName = getView().getTabBar().getSelectedTab().getLabel();
    }

    private void showHtmlPresenter() {
        itemNavigatorPresenter.setDisplay(noNavigatorData);
        getView().getLayerContainer().show(htmlPresenter);
    }

    private void showTextPresenter() {
        itemNavigatorPresenter.setDisplay(navigatorData);
        getView().getLayerContainer().show(textPresenter);
    }

    private void showMarkerPresenter() {
        // TODO @AT Need one for marker data
        itemNavigatorPresenter.setDisplay(navigatorData);
        getView().getLayerContainer().show(markerListPresenter);
    }

    private void hidePresenters() {
        getView().getLayerContainer().clear();
    }

    private void refresh(final AbstractFetchDataResult result) {

        refreshProgressBar(result != null);
        setPagers(result);
        refreshTextPresenterContent();

        refreshMetaInfoPresenterContent(result != null
                ? result.getSourceLocation().getId()
                : null);

        itemNavigatorPresenter.refreshNavigator();
        refreshHighlights(result);
        refreshMarkers(result);
    }

    private void refreshTextPresenterContent() {
        textPresenter.setMode(editorMode);
        // Only want to try to format (which formats as XML) if we know the
        // data is likely to be XML, else it can mess up the formatting of error text.
        boolean isFormatted = formatOnLoad && AceEditorMode.XML.equals(editorMode);

        textPresenter.setText(data, isFormatted);
        textPresenter.setControlsVisible(playButtonVisible);
    }

    private void refreshMetaInfoPresenterContent(final Long metaId) {

        if (metaId != null && INFO.equals(lastTabName)) {
            fetchMetaInfoData(metaId);
        } else {
            htmlPresenter.setHtml(null);
        }
    }

    private void refreshHighlights(final AbstractFetchDataResult result) {
        int partNo = 0;

        if (result != null) {
            partNo = (int) (result.getSourceLocation().getPartNo());
        }

        // Make sure we have a highlight section to add and that the stream id
        // matches that of the current page, and that the stream number matches
        // the stream number of the current page.
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

    public void setNavigationControlsVisible(final boolean visible) {
        if (visible) {
            getView().setNavigatorView(itemNavigatorPresenter.getView());
        } else {
            getView().setNavigatorView(null);
        }
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

    private AbstractFetchDataResult getLastResult() {
        return lastResult;
    }

    private void fetchMetaInfoData(long metaId) {
        final Rest<List<DataInfoSection>> rest = restFactory.create();
        rest
                .onSuccess(this::handleMetaInfoResult)
                .call(DATA_RESOURCE)
                .info(metaId);
    }

    private void handleMetaInfoResult(final List<DataInfoSection> dataInfoSections) {
        final TooltipUtil.Builder builder = TooltipUtil.builder();

        builder.addTable(tableBuilder -> {
            for (final DataInfoSection section : dataInfoSections) {
                // Add the section header

                tableBuilder.addRow(
                        new SafeHtmlBuilder()
                                .appendHtmlConstant("<span style=\"" + META_SECTION_HEAD_STYLES.asString() + "\">")
                                .appendEscaped(section.getTitle())
                                .appendHtmlConstant("</span>")
                                .toSafeHtml(),
                        null,
                        true,
                        META_SECTION_CELL_STYLES);

                section.getEntries()
                        .forEach(entry ->
                                tableBuilder.addRow(
                                        TooltipUtil.styledSpan(entry.getKey(), META_KEY_STYLES),
                                        replaceJavaLineBreaks(entry.getValue()),
                                        true));
            }
            return tableBuilder.build();
        });

        htmlPresenter.setHtml(builder.build().asString());
    }

    private SafeHtml replaceJavaLineBreaks(final String str) {
        if (str != null) {
            String[] parts = str.split("\\n");
            SafeHtmlBuilder safeHtmlBuilder = new SafeHtmlBuilder();
            for (int i = 0; i < parts.length; i++) {
                safeHtmlBuilder.append(SafeHtmlUtils.fromString(parts[i]));
                if (i != parts.length - 1) {
                    safeHtmlBuilder.appendHtmlConstant("</br>");
                }
            }
            return safeHtmlBuilder.toSafeHtml();
        } else {
            return null;
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface DataView extends View {

        ButtonView addButton(final SvgPreset preset);

        ToggleButtonView addToggleButton(final SvgPreset onPreset,
                                         final SvgPreset offPreset);

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setNavigatorView(ItemNavigatorView itemNavigatorView);

        void setProgressView(final ProgressView progressView);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class NoNavigatorData implements HasItems {

        @Override
        public String getName() {
            return "";
        }

        @Override
        public OffsetRange<Long> getItemRange() {
            return OffsetRange.of(0L,1L);
        }

        @Override
        public void setItemNo(final long itemNo) {
        }

        @Override
        public Count<Long> getTotalItemsCount() {
            return null;
        }

        @Override
        public boolean areNavigationControlsVisible() {
            return false;
        }

        @Override
        public int getMaxItemsPerPage() {
            return 0;
        }

        @Override
        public void firstPage() {
        }

        @Override
        public void nextPage() {
        }

        @Override
        public void previousPage() {
        }

        @Override
        public void lastPage() {
        }

        @Override
        public void refresh() {
        }
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private class NavigatorData implements HasItems {

        private Count<Long> totalItemCount = Count.of(0L, false);
        private Consumer<Long> itemNoFromConsumer = null;
        private Supplier<OffsetRange<Long>> itemRangeSupplier = null;
//        private long previousItemNo = -1;
        private String name = "";
        private int maxItemsPerPage = 1;

        private void updateStateForOneItemPerPage(final Count<Long> totalItemCount,
                                                  final Consumer<Long> itemNoConsumer,
                                                  final Supplier<Long> itemOffsetSupplier,
                                                  final String name) {
            this.totalItemCount = totalItemCount;
            this.itemNoFromConsumer = itemNoConsumer;
            this.itemRangeSupplier = () -> OffsetRange.of(itemOffsetSupplier.get(), 1L);
            this.name = name;
            this.maxItemsPerPage = 1;
        }

        private void updateStateForMultiItemsPerPage(final Count<Long> totalItemCount,
                                                     final Consumer<Long> itemNoConsumer,
                                                     final Supplier<OffsetRange<Long>> itemRangeSupplier,
                                                     final String name,
                                                     final int maxItemsPerPage ) {
            this.totalItemCount = totalItemCount;
            this.itemNoFromConsumer = itemNoConsumer;
            this.itemRangeSupplier = itemRangeSupplier;
            this.name = name;
            this.maxItemsPerPage = maxItemsPerPage;
        }

        private void updateSegmentsCount(final Count<Long> segmentsCount) {
            this.totalItemCount = segmentsCount;
        }

        private void setName(final String name) {
            this.name = name;
        }

        private void setTotalItemCount(final Count<Long> totalItemCount) {
            this.totalItemCount = totalItemCount;
        }

        public void setItemNoFromConsumer(final Consumer<Long> itemNoFromConsumer) {
            this.itemNoFromConsumer = itemNoFromConsumer;
        }

        public void setItemRangeSupplier(final Supplier<OffsetRange<Long>> itemRangeSupplier) {
            this.itemRangeSupplier = itemRangeSupplier;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public OffsetRange<Long> getItemRange() {
            return itemRangeSupplier.get();
        }

        @Override
        public void setItemNo(final long itemNo) {
            itemNoFromConsumer.accept(itemNo);
            update(false);
        }

        @Override
        public Count<Long> getTotalItemsCount() {
            return totalItemCount;
        }

        @Override
        public boolean areNavigationControlsVisible() {
            return true;
        }

        @Override
        public int getMaxItemsPerPage() {
            return maxItemsPerPage;
        }

        @Override
        public void firstPage() {
            setItemNo(0);
        }

        @Override
        public void nextPage() {
            final long itemOffset = itemRangeSupplier.get().getOffset();
            if (totalItemCount.isExact()) {
                setItemNo(Math.min(totalItemCount.getCount(), itemOffset + maxItemsPerPage));
            } else {
                setItemNo(itemOffset + maxItemsPerPage);
            }
        }

        @Override
        public void previousPage() {
            final long itemOffset = itemRangeSupplier.get().getOffset();
            if (totalItemCount.isExact()) {
                // Zero based pages
                int currPage = (int) (itemOffset / maxItemsPerPage);
                int newPage = Math.max(0, currPage - 1);
                setItemNo(newPage * maxItemsPerPage);
            } else {
                setItemNo(Math.max(0, itemOffset - maxItemsPerPage));
            }
        }

        @Override
        public void lastPage() {
            setItemNo(totalItemCount.getCount() - maxItemsPerPage);
        }

        @Override
        public void refresh() {
            update(false);
        }
    }
}
