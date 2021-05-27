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
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.util.shared.EqualsUtil;
import stroom.util.shared.HasItems;
import stroom.util.shared.Marker;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;
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
import com.google.gwt.event.dom.client.ClickHandler;
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

    private static final String CONTEXT_TAB_NAME = "Context";
    private static final String DATA_PREVIEW_TAB_NAME = "Data Preview";
    private static final String ERROR_TAB_NAME = "Error";
    private static final String INFO_TAB_NAME = "Info";
    private static final String META_TAB_NAME = "Meta";

    private static final String INFO_PSEUDO_STREAM_TYPE = "Info";

    private static final String RECORD_PAGER_UNIT = "Record";
    private static final String PAGE_PAGER_UNIT = "Page";
    private static final String PART_PAGER_UNIT = "Part";

    private final TabData errorTab = new TabDataImpl(ERROR_TAB_NAME);
    private final TabData dataTab = new TabDataImpl(DATA_PREVIEW_TAB_NAME);
    private final TabData metaTab = new TabDataImpl(META_TAB_NAME);
    private final TabData infoTab = new TabDataImpl(INFO_TAB_NAME);
    private final TabData contextTab = new TabDataImpl(CONTEXT_TAB_NAME);

    private final HtmlPresenter htmlPresenter;
    private final TextPresenter textPresenter;
    private final ItemNavigatorPresenter itemNavigatorPresenter;
    private final ProgressPresenter progressPresenter;
    private final MarkerListPresenter markerListPresenter;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private final boolean userHasPipelineSteppingPermission;
    private final NoNavigatorData noNavigatorData = new NoNavigatorData();
    private final NavigatorData navigatorData = new NavigatorData();

    private DisplayMode displayMode = null;
    private Boolean errorMarkerMode = null;
    // This is the parent stream type as opposed to the child stream type,
    // i.e. Raw Events rather than say Context
    private String currentStreamType;
    private String effectiveChildStreamType;
    private Set<String> currentAvailableStreamTypes = null;
    private DataType curDataType;
    private SourceLocation currentSourceLocation;
    private AbstractFetchDataResult lastResult;
    private List<FetchDataRequest> actionQueue;
    private Timer delayedFetchDataTimer;
    private String data;
    private AceEditorMode editorMode = AceEditorMode.XML;
    private List<Marker> markers;
    private List<TextRange> highlights;
    private Long highlightMetaId;
    private Long highlightPartIndex;
    private String highlightChildDataType;
    private boolean playButtonVisible;
    private ClassificationUiHandlers classificationUiHandlers;
    private BeginSteppingHandler beginSteppingHandler;
    private boolean steppingSource;
    private boolean ignoreActions;
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
        textPresenter.setWrapLines(true);

        addTab(infoTab);
        addTab(errorTab);
        addTab(dataTab);
        addTab(metaTab);
        addTab(contextTab);

        userHasPipelineSteppingPermission = securityContext.hasAppPermission(PermissionNames.STEPPING_PERMISSION);

        itemNavigatorPresenter.setDisplay(noNavigatorData);
        view.addSourceLinkClickHandler(event ->
                openSourcePresenter());
        view.setSourceLinkVisible(true);
        view.setNavigatorView(itemNavigatorPresenter.getView());
        view.setProgressView(progressPresenter.getView());
        progressPresenter.setVisible(false);
    }

    private void setCurrentRecordIndex(final long recordIndex) {
        this.currentSourceLocation = currentSourceLocation.copy()
                .withRecordIndex(recordIndex)
                .build();
    }

    public void setCurrentPartIndex(final long currentPartIndex) {
        this.currentSourceLocation = currentSourceLocation.copy()
                .withPartIndex(currentPartIndex)
                .build();
    }

    private void openSourcePresenter() {
        // No need to supply a data range as it will just open it with the default range
        // that is bigger than our preview range

        // Open it in the same type of view as we are in now
        ShowDataEvent.fire(
                this,
                currentSourceLocation,
                DataViewType.SOURCE,
                (displayMode != null
                        ? displayMode
                        : DisplayMode.STROOM_TAB));
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
    }

    private void hideTab(final TabData tab, final boolean hide) {
        getView().getTabBar()
                .setTabHidden(tab, hide);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(getView().getTabBar().addSelectionHandler(event ->
                onNewTabSelected(event.getSelectedItem())));
    }

    private void onNewTabSelected(final TabData tab) {
        // Make sure tabs don't do anything in stepping mode.
        if (!steppingSource) {
            if (tab != null) {
                // Clear the text presenter while we wait for the new data to come down
                textPresenter.setText(null);
                if (INFO_TAB_NAME.equals(tab.getLabel())) {
                    setActiveTab(infoTab, null);
                    effectiveChildStreamType = INFO_PSEUDO_STREAM_TYPE;
                    showHtmlPresenter();
                    fetchMetaInfoData(getCurrentMetaId());
                    getView().setSourceLinkVisible(false);
                    itemNavigatorPresenter.refreshNavigator();
                    refreshProgressBar(false);
                    refreshTextPresenterContent();
                } else {
                    getView().setSourceLinkVisible(true);
                    if (META_TAB_NAME.equals(tab.getLabel())) {
                        editorMode = AceEditorMode.PROPERTIES;
                        fetchDataForCurrentStreamNo(StreamTypeNames.META);
                        refreshProgressBar(false);
                    } else if (CONTEXT_TAB_NAME.equals(tab.getLabel())) {
                        editorMode = AceEditorMode.XML;
                        fetchDataForCurrentStreamNo(StreamTypeNames.CONTEXT);
                    } else if (ERROR_TAB_NAME.equals(tab.getLabel())) {
                        errorMarkerMode = true;
                        editorMode = AceEditorMode.TEXT;
                        fetchDataForCurrentStreamNo(null);
                    } else {
                        // Turn off error marker mode if we are currently looking at
                        // an error and switching to the data tab.
                        if (StreamTypeNames.ERROR.equals(currentStreamType)) {
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
                lastTabName = tab.getLabel();
            }
        }
    }

    private void updateEditorMode(final String streamType, final TabData tabData) {
        if (tabData != null && streamType != null) {
            final String tabName = tabData.getLabel();
            if (INFO_TAB_NAME.equals(tabName)) {
                editorMode = AceEditorMode.TEXT;
            } else if (isInErrorMarkerMode() && StreamTypeNames.ERROR.equals(streamType)) {
                // Not a text editor
            } else if (!isInErrorMarkerMode() && StreamTypeNames.ERROR.equals(streamType)) {
                editorMode = AceEditorMode.TEXT;
            } else if (META_TAB_NAME.equals(tabName)) {
                editorMode = AceEditorMode.PROPERTIES;
            } else if (CONTEXT_TAB_NAME.equals(tabName)) {
                editorMode = AceEditorMode.XML;
            } else {
                // Default to xml mode
                editorMode = AceEditorMode.XML;
            }
        }
    }

    private boolean isInErrorMarkerMode() {
        // Marker mode is default so treat null like true
        return errorMarkerMode == null || errorMarkerMode;
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
            } else if (fetchDataResult.getTotalCharacterCount() != null
                    && fetchDataResult.getTotalCharacterCount().isExact()
                    && dataRange.getCharOffsetFrom() != null
                    && dataRange.getCharOffsetTo() != null) {
                // progress based on known char count and char offsets
                progressPresenter.setProgress(Progress.boundedRange(
                        fetchDataResult.getTotalCharacterCount().getCount() - 1, // count to zero based bound
                        dataRange.getCharOffsetFrom(),
                        dataRange.getCharOffsetTo()));
            } else if (dataRange.getCharOffsetFrom() != null
                    && dataRange.getCharOffsetTo() != null) {
                // progress based on unknown char count and char offsets
                progressPresenter.setProgress(Progress.unboundedRange(
                        dataRange.getCharOffsetFrom(),
                        dataRange.getCharOffsetTo()));
            } else {
                dumpRangeInfo(fetchDataResult);
                progressPresenter.setVisible(false);
            }
        } else {
            progressPresenter.setVisible(false);
        }
    }

    private void dumpRangeInfo(final FetchDataResult result) {
        if (result.getSourceLocation() != null) {
            final DataRange dataRange = result.getSourceLocation().getDataRange();
            GWT.log("DataRange "
                    + " charFrom " + dataRange.getCharOffsetFrom()
                    + " charTo " + dataRange.getCharOffsetTo()
                    + " chars " + result.getTotalCharacterCount()
                    + " byteFrom " + dataRange.getByteOffsetFrom()
                    + " byteTo " + dataRange.getByteOffsetTo()
                    + " bytes " + result.getTotalBytes());
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
        currentSourceLocation = null;
    }

    @Override
    public void beginStepping() {
        if (beginSteppingHandler != null && getCurrentMetaId() != null) {
            final StepLocation stepLocation = new StepLocation(getCurrentMetaId(),
                    getCurrentPartIndex(),
                    getCurrentRecordIndex());
            beginSteppingHandler.beginStepping(stepLocation, getCurrentChildStreamType());
        }
    }

    private void fetchDataForCurrentStreamNo(final String childDataType) {
        effectiveChildStreamType = childDataType;
        currentSourceLocation = currentSourceLocation.copy()
                .withChildStreamType(childDataType)
                .build();
        update(true, currentStreamType);
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

        this.highlightMetaId = sourceLocation.getMetaId();
        this.highlightPartIndex = sourceLocation.getPartIndex();
        this.highlightChildDataType = sourceLocation.getChildType();

        // Update the stream source.
        if (!Objects.equals(currentSourceLocation, sourceLocation)) {
            // New data location so re-fetch
            currentSourceLocation = sourceLocation;
            markerListPresenter.resetExpandedSeverities();

            update(false, currentStreamType);
        } else {
            refreshHighlights(lastResult);
            refreshMarkers(lastResult);
        }
    }

    public void setDisplayMode(final DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    public void fetchData(final Meta meta) {
        currentSourceLocation = SourceLocation.builder(meta.getId())
                .build();
        currentStreamType = meta.getTypeName();
        markerListPresenter.resetExpandedSeverities();

//        GWT.log("ID " + getCurrentMetaId()
//                + " streamType " + currentStreamType
//                + " part " + getCurrentPartNo()
//                + " seg " + getCurrentSegmentNo());

        update(true, meta.getTypeName());
    }

    public void update(final boolean fireEvents) {
        update(fireEvents, currentStreamType);
    }

    private void update(final boolean fireEvents,
                        final String streamTypeName) {
        if (!ignoreActions) {
            if (isSameStreamAndPartAsLastTime() && currentAvailableStreamTypes != null) {
                // Same stream/part so we know this type is available and
                // therefore no need to update tabs as

                update(fireEvents, streamTypeName, currentAvailableStreamTypes);
            } else {
                // Different stream/part so we need to check which child stream types are available
                // and pick an appropriate one.
                currentAvailableStreamTypes = null;

                if (getCurrentMetaId() != null) {
                    final Rest<Set<String>> rest = restFactory.create();
                    rest
                            .onSuccess(availableChildStreamTypes -> {
//                                GWT.log("Received available child stream types " + availableChildStreamTypes);
                                currentAvailableStreamTypes = availableChildStreamTypes;
                                update(fireEvents, streamTypeName, availableChildStreamTypes);
                            })
                            .onFailure(caught ->
                                    itemNavigatorPresenter.setRefreshing(false))
                            .call(DATA_RESOURCE)
                            .getChildStreamTypes(
                                    currentSourceLocation.getMetaId(),
                                    currentSourceLocation.getPartIndex());
                }
            }
        }
    }

    private void update(final boolean fireEvents,
                        final String streamTypeName,
                        final Set<String> availableChildStreamTypes) {
        if (INFO_PSEUDO_STREAM_TYPE.equals(effectiveChildStreamType)) {
            updateAvailableAndSelectedTabs(streamTypeName, availableChildStreamTypes);
            refreshMetaInfoPresenterContent(currentSourceLocation.getMetaId());
            refreshProgressBar(false);
        } else {
            // Tabs will be updated by updateFromResource
            setEffectiveChildStreamType(streamTypeName, availableChildStreamTypes);
            updateFromResource(fireEvents, currentSourceLocation);
        }
    }

    private void setEffectiveChildStreamType(final String streamTypeName,
                                             final Set<String> availableChildStreamTypes) {
//        GWT.log(currentStreamType + " - " + currentChildDataType + " - " + availableChildStreamTypes);

        if (effectiveChildStreamType != null
                && availableChildStreamTypes.contains(effectiveChildStreamType)) {
            if (!Objects.equals(currentSourceLocation.getChildType(), effectiveChildStreamType)) {
                currentSourceLocation = currentSourceLocation.copy()
                        .withChildStreamType(effectiveChildStreamType)
                        .build();
            }
            //previous child stream type is available on this stream so use it
        } else {
            if (StreamTypeNames.ERROR.equals(streamTypeName) && errorMarkerMode == null) {
                // See error markers by default
                errorMarkerMode = true;
            }
            // null child data type indicates to show the data child stream
            effectiveChildStreamType = null;
            currentSourceLocation = currentSourceLocation.copy()
                    .withChildStreamType(null)
                    .build();
        }
    }

    private void updateFromResource(final boolean fireEvents, final SourceLocation sourceLocation) {
        final Severity[] expandedSeverities = markerListPresenter.getExpandedSeverities();

        doWithConfig(sourceConfig -> {
            final DataRange dataRange;
            // Error markers are a bit different
            if (StreamTypeNames.ERROR.equals(currentStreamType)) {
                dataRange = DataRange.fromCharOffset(0);
            } else if (StreamTypeNames.META.equals(currentSourceLocation.getChildType())) {
                dataRange = DataRange.fromCharOffset(0);
            } else if (currentSourceLocation.getDataRange() != null) {
                // We have a specific range of data, i.e. when using the data() dash func.
                dataRange = currentSourceLocation.getDataRange();
            } else {
                dataRange = DataRange.fromCharOffset(0,
                        sourceConfig.getMaxCharactersInPreviewFetch());
            }
//            GWT.log("Using data range " + dataRange.toString());

            // TODO @AT Do we need to pass the highlight?
            final SourceLocation.Builder builder = SourceLocation.builder(currentSourceLocation.getMetaId())
                    .withPartIndex(currentSourceLocation.getPartIndex())
                    .withRecordIndex(currentSourceLocation.getRecordIndex())
                    .withDataRange(dataRange)
                    .withChildStreamType(currentSourceLocation.getChildType());
            if (highlights != null && !highlights.isEmpty()) {
                builder.withHighlight(highlights.get(0));
            }

            final FetchDataRequest request = new FetchDataRequest(builder.build());
            request.setMarkerMode(StreamTypeNames.ERROR.equals(currentStreamType)
                    && isInErrorMarkerMode());
            request.setExpandedSeverities(expandedSeverities);
            request.setFireEvents(fireEvents);
            doFetch(request, fireEvents);
        });
    }

    private boolean isSameStreamAndPartAsLastTime() {
        if (lastResult != null) {

            final Long lastId = Optional.ofNullable(lastResult)
                    .flatMap(result -> Optional.ofNullable(result.getSourceLocation()))
                    .map(SourceLocation::getMetaId)
                    .orElse(null);
            final Long lastPartNo = Optional.ofNullable(lastResult)
                    .flatMap(result -> Optional.ofNullable(result.getSourceLocation()))
                    .map(SourceLocation::getPartIndex)
                    .orElse(null);

            return Objects.equals(getCurrentMetaId(), lastId)
                    && Objects.equals(getCurrentPartIndex(), lastPartNo);
        } else {
            return false;
        }
    }

    private void doFetch(final FetchDataRequest request, final boolean fireEvents) {
        if (getCurrentMetaId() != null) {
            itemNavigatorPresenter.setRefreshing(true);

            // Create the action queue and delayed refresh timer if we haven't
            // already. This allows the user to hit the next/prev btn multiple times without
            // sending a request for each. The downside is that is makes the page slower
            ensureActionQueue();

            // Add the action and schedule the timer.
            actionQueue.add(request);
            delayedFetchDataTimer.cancel();
            delayedFetchDataTimer.schedule(100);
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
                                .call(DATA_RESOURCE)
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
                    PAGE_PAGER_UNIT);
        } else if (result instanceof FetchDataResult) {

            FetchDataResult fetchDataResult = (FetchDataResult) result;

            if (DataType.SEGMENTED.equals(fetchDataResult.getDataType())) {
                // Record: a of b   Characters: x to y of z
                navigatorData.updateStateForOneItemPerPage(
                        result.getTotalItemCount(),
                        this::setCurrentRecordIndex,
                        this::getCurrentRecordIndex,
                        RECORD_PAGER_UNIT);
            } else {
                // non-segmented
                //    Part: a of b   Characters: x to y of z
                // OR                Characters: x to y of z
                navigatorData.updateStateForOneItemPerPage(
                        result.getTotalItemCount(),
                        this::setCurrentPartIndex,
                        this::getCurrentPartIndex,
                        PART_PAGER_UNIT);
            }
        }
//            GWT.log("Segment Pager Offset: " + dataPagerOffset
//                    + " Length: " + dataPagerLength
//                    + " Total: " + dataPagerCount
//                    + " Exact: " + (dataPagerCountExact ? "EXACT" : "NON-EXACT")
//                    + " type: " + (result instanceof FetchDataResult
//                    ? ((FetchDataResult) result).getDataType()
//                    : "-"));
//
//            GWT.log("Data Pager Offset: " + commonPagerOffset
//                    + " Length: " + commonPagerLength
//                    + " Total: " + commonPagerCount
//                    + " Exact: " + (commonPagerCountExact ? "EXACT" : "NON-EXACT")
//                    + " type: " + (result instanceof FetchDataResult
//                    ? ((FetchDataResult) result).getDataType()
//                    : "-"));
    }

    long getCurrentErrorsPageOffset() {
        return getCurrentRecordIndex() / SourceLocation.MAX_ERRORS_PER_PAGE;
    }

    void setCurrentErrorsPageOffset(final long pageOffset) {
        setCurrentRecordIndex(pageOffset * SourceLocation.MAX_ERRORS_PER_PAGE);
    }

    private void setPageResponse(final AbstractFetchDataResult result,
                                 final boolean fireEvents) {
//        GWT.log("Received"
//                + " id " + result.getSourceLocation().getId()
//                + " streamType " + result.getStreamTypeName()
//                + " childStreamType " + result.getSourceLocation().getChildType());
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
            updateAvailableAndSelectedTabs(
                    result.getStreamTypeName(),
                    result.getAvailableChildStreamTypes());
        } else {
            classificationUiHandlers.setClassification("");

            refresh(result);
            updateAvailableAndSelectedTabs(null, null);
        }
        ignoreActions = false;
    }

    private void updateAvailableAndSelectedTabs(final String streamType,
                                                final Set<String> availableChildStreamTypes) {

//        GWT.log("streamType " + currentStreamType
//                + " childStreamType " + currentSourceLocation.getChildType()
//                + " availableChildStreamTypes " + availableChildStreamTypes
//                + " effectiveChildStreamType " + effectiveChildStreamType);

        if (availableChildStreamTypes == null) {
            // Hide all links
            hideTab(infoTab, true);
            hideTab(errorTab, true);
            hideTab(dataTab, true);
            hideTab(metaTab, true);
            hideTab(contextTab, true);
        } else {
            hideTab(infoTab, false); // info always available
            hideTab(errorTab, !StreamTypeNames.ERROR.equals(streamType));
            hideTab(dataTab, streamType == null);
            hideTab(metaTab, !availableChildStreamTypes.contains(StreamTypeNames.META));
            hideTab(contextTab, !availableChildStreamTypes.contains(StreamTypeNames.CONTEXT));
        }

        if (streamType == null) {
            showHtmlPresenter();
            itemNavigatorPresenter.setDisplay(noNavigatorData);
        } else {
            // Select the tab based
            final TabData newActiveTab;
            if (INFO_PSEUDO_STREAM_TYPE.equals(effectiveChildStreamType)) {
                newActiveTab = infoTab;
                setActiveTab(newActiveTab, streamType);
                showHtmlPresenter();
            } else if (isInErrorMarkerMode() && StreamTypeNames.ERROR.equals(streamType)) {
                newActiveTab = errorTab;
                effectiveChildStreamType = null;
                setActiveTab(newActiveTab, streamType);
                showMarkerPresenter();
            } else if (StreamTypeNames.META.equals(effectiveChildStreamType)) {
                newActiveTab = metaTab;
                setActiveTab(newActiveTab, streamType);
                showTextPresenter();
            } else if (StreamTypeNames.CONTEXT.equals(effectiveChildStreamType)) {
                newActiveTab = contextTab;
                setActiveTab(newActiveTab, streamType);
                showTextPresenter();
            } else {
                newActiveTab = dataTab;
                effectiveChildStreamType = null;
                setActiveTab(newActiveTab, streamType);
                showTextPresenter();
            }
            // Now we have changed tabs, ensure the nav visibility is right
            itemNavigatorPresenter.refreshNavigator();
        }

        if (getView().getTabBar().getSelectedTab() != null) {
            lastTabName = getView().getTabBar().getSelectedTab().getLabel();
        }
    }

    private void setActiveTab(final TabData tab, final String streamType) {
//        GWT.log("Setting active tab to " + tab.getLabel());
        getView().getTabBar().selectTab(tab);
        if (streamType != null) {
            updateEditorMode(streamType, tab);
        }
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
                ? result.getSourceLocation().getMetaId()
                : null);

        itemNavigatorPresenter.refreshNavigator();
        refreshHighlights(result);
        refreshMarkers(result);
    }

    private void refreshTextPresenterContent() {
        textPresenter.setMode(editorMode);
        // Only want to try to format (which formats as XML) if we know the
        // data is likely to be XML, else it can mess up the formatting of error text.
        textPresenter.setText(data, AceEditorMode.XML.equals(editorMode));
        textPresenter.setControlsVisible(playButtonVisible);
    }

    private void refreshMetaInfoPresenterContent(final Long metaId) {

        if (metaId != null && INFO_TAB_NAME.equals(lastTabName)) {
            fetchMetaInfoData(metaId);
        } else {
            htmlPresenter.setHtml(null);
        }
    }

    private void refreshHighlights(final AbstractFetchDataResult result) {
        int partIndex = 0;

        if (result != null) {
            partIndex = (int) (result.getSourceLocation().getPartIndex());
        }

        // Make sure we have a highlight section to add and that the stream id
        // matches that of the current page, and that the stream number matches
        // the stream number of the current page.
        if (highlights != null
                && Objects.equals(getCurrentMetaId(), highlightMetaId)
                && partIndex == highlightPartIndex
                && result != null
                && EqualsUtil.isEquals(result.getStreamTypeName(), highlightChildDataType)) {
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

    public void setNavigationControlsVisible(final boolean visible) {
        if (visible) {
            getView().setNavigatorView(itemNavigatorPresenter.getView());
        } else {
            getView().setNavigatorView(null);
        }
    }

    private void fetchMetaInfoData(long metaId) {
        final Rest<List<DataInfoSection>> rest = restFactory.create();
        rest
                .onSuccess(this::handleMetaInfoResult)
                .call(DATA_RESOURCE)
                .viewInfo(metaId);
    }

    private void handleMetaInfoResult(final List<DataInfoSection> dataInfoSections) {
        final TooltipUtil.Builder builder = TooltipUtil.builder();

        builder.addTwoColTable(tableBuilder -> {
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

    private Long getCurrentMetaId() {
        return currentSourceLocation != null
                ? currentSourceLocation.getMetaId()
                : null;
    }

    private String getCurrentChildStreamType() {
        return currentSourceLocation != null
                ? currentSourceLocation.getChildType()
                : null;
    }

    private long getCurrentPartIndex() {
        return currentSourceLocation != null
                ? currentSourceLocation.getPartIndex()
                : 0;
    }

    private long getCurrentRecordIndex() {
        return currentSourceLocation != null
                ? currentSourceLocation.getRecordIndex()
                : 0;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    public interface DataView extends View {

        void addSourceLinkClickHandler(final ClickHandler clickHandler);

        void setSourceLinkVisible(final boolean isVisible);

        TabBar getTabBar();

        LayerContainer getLayerContainer();

        void setNavigatorView(final ItemNavigatorView itemNavigatorView);

        void setProgressView(final ProgressView progressView);
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    private static class NoNavigatorData implements HasItems {

        @Override
        public String getName() {
            return "";
        }

        @Override
        public OffsetRange getItemRange() {
            return new OffsetRange(0L, 1L);
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
        private Supplier<OffsetRange> itemRangeSupplier = null;
        private String name = "";
        private int maxItemsPerPage = 1;

        private void updateStateForOneItemPerPage(final Count<Long> totalItemCount,
                                                  final Consumer<Long> itemNoConsumer,
                                                  final Supplier<Long> itemOffsetSupplier,
                                                  final String name) {
            this.totalItemCount = totalItemCount;
            this.itemNoFromConsumer = itemNoConsumer;
            this.itemRangeSupplier = () -> new OffsetRange(itemOffsetSupplier.get(), 1L);
            this.name = name;
            this.maxItemsPerPage = 1;
        }

        public void setItemNoFromConsumer(final Consumer<Long> itemNoFromConsumer) {
            this.itemNoFromConsumer = itemNoFromConsumer;
        }

        public void setItemRangeSupplier(final Supplier<OffsetRange> itemRangeSupplier) {
            this.itemRangeSupplier = itemRangeSupplier;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public OffsetRange getItemRange() {
            return itemRangeSupplier.get();
        }

        @Override
        public void setItemNo(final long itemNo) {
            itemNoFromConsumer.accept(itemNo);
            update(false, currentStreamType);
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
            update(false, currentStreamType);
        }
    }
}
