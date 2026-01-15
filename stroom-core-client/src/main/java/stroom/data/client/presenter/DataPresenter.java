/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.data.client.SourceTabPlugin;
import stroom.data.client.presenter.ItemNavigatorPresenter.ItemNavigatorView;
import stroom.data.client.presenter.OpenLinkUtil.LinkType;
import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataResource;
import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaResource;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.FetchMarkerResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
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
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.TableBuilder;
import stroom.widget.util.client.TableCell;

import com.google.gwt.core.client.GWT;
import com.google.gwt.dom.client.Style.Unit;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.LayerContainer;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DataPresenter
        extends MyPresenterWidget<ClassificationWrapperView>
        implements TextUiHandlers, Focus {

    private static final DataResource DATA_RESOURCE = com.google.gwt.core.shared.GWT.create(DataResource.class);
    private static final MetaResource META_RESOURCE = com.google.gwt.core.shared.GWT.create(MetaResource.class);

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

    private final DataView dataView;
    private final ClassificationWrapperView classificationWrapperView;
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
    private DataViewType initDataViewType;
    private Boolean errorMarkerMode = null;
    // This is the parent stream type as opposed to the child stream type,
    // i.e. Raw Events rather than say Context
    private String currentStreamType;
    // This is the child stream type, e.g. meta, context
    // but may also be Info which is not really
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
    private BeginSteppingHandler beginSteppingHandler;
    private boolean steppingSource;
    private boolean ignoreActions;
    // Track the tab last used so if we switch streams we can select the same tab again if it has it
    private String lastTabName;
    // The currently selected tab
    private String currentTabName = null;

    private static final List<String> STREAM_ATTRIBUTE_NAMES = List.of("Parent Stream Id", "Stream Id");

    @Inject
    public DataPresenter(final EventBus eventBus,
                         final HtmlPresenter htmlPresenter,
                         final ItemNavigatorPresenter itemNavigatorPresenter,
                         final DataView dataView,
                         final ClassificationWrapperView classificationWrapperView,
                         final TextPresenter textPresenter,
                         final ProgressPresenter progressPresenter,
                         final MarkerListPresenter markerListPresenter,
                         final SourceTabPlugin sourceTabPlugin,
                         final UiConfigCache uiConfigCache,
                         final ClientSecurityContext securityContext,
                         final RestFactory restFactory) {
        super(eventBus, classificationWrapperView);
        classificationWrapperView.setContent(dataView);
        this.dataView = dataView;
        this.classificationWrapperView = classificationWrapperView;
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
        textPresenter.getViewAsHexOption().setChangeHandler((isOn) ->
                update(false));

        addTab(infoTab);
        addTab(errorTab);
        addTab(dataTab);
        addTab(metaTab);
        addTab(contextTab);

        userHasPipelineSteppingPermission = securityContext.hasAppPermission(AppPermission.STEPPING_PERMISSION);

        itemNavigatorPresenter.setDisplay(noNavigatorData);
        dataView.addSourceLinkClickHandler(event ->
                openSourcePresenter());
        dataView.setSourceLinkVisible(true, true);
        dataView.setNavigatorView(itemNavigatorPresenter.getView());
        dataView.setProgressView(progressPresenter.getView());
        progressPresenter.setVisible(false);
    }

    @Override
    public void focus() {
        dataView.focus();
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
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                action.accept(uiConfig.getSource());
            }
        }, dataView);
    }

    private void addTab(final TabData tab) {
        dataView.getTabBar()
                .addTab(tab);
        hideTab(tab, true);
    }

    private void hideTab(final TabData tab, final boolean hide) {
        dataView.getTabBar()
                .setTabHidden(tab, hide);
    }

    @Override
    protected void onBind() {
        super.onBind();

        registerHandler(dataView.getTabBar().addSelectionHandler(event ->
                onNewTabSelected(event.getSelectedItem())));
        registerHandler(dataView.getTabBar().addShowMenuHandler(e -> getEventBus().fireEvent(e)));
        registerHandler(htmlPresenter.getWidget().addDomHandler(e ->
                CopyTextUtil.onClick(e.getNativeEvent(), this), MouseDownEvent.getType()));
    }

    private void onNewTabSelected(final TabData tab) {
        currentTabName = tab.getLabel();
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
                    dataView.setSourceLinkVisible(false, false);
                    setNavigationControlsVisible(false);
                    dataView.setProgressView(null);
                    itemNavigatorPresenter.refreshNavigator();
                    refreshProgressBar(false);
                    refreshTextPresenterContent();
                } else {
//                    dataView.setSourceLinkVisible(true, true);
                    setNavigationControlsVisible(true);
                    dataView.setProgressView(progressPresenter.getView());
                    if (META_TAB_NAME.equals(tab.getLabel())) {
                        fetchDataForCurrentStreamNo(StreamTypeNames.META);
                        refreshProgressBar(false);
                    } else if (CONTEXT_TAB_NAME.equals(tab.getLabel())) {
                        fetchDataForCurrentStreamNo(StreamTypeNames.CONTEXT);
                    } else if (ERROR_TAB_NAME.equals(tab.getLabel())) {
                        errorMarkerMode = true;
                        fetchDataForCurrentStreamNo(null);
                    } else {
                        // Turn off error marker mode if we are currently looking at
                        // an error and switching to the data tab.
                        if (StreamTypeNames.ERROR.equals(currentStreamType)) {
                            errorMarkerMode = false;
                        }
                        fetchDataForCurrentStreamNo(null);
                    }
                }
                // Keep track of the tab we are on for when we switch streams
                lastTabName = tab.getLabel();
            }
        }
    }

    private void updateEditorDisplay() {

        // Determine the syntax highlighting mode
        if (INFO_TAB_NAME.equals(currentTabName)) {
            editorMode = AceEditorMode.TEXT;
        } else if (META_TAB_NAME.equals(currentTabName)) {
            editorMode = AceEditorMode.PROPERTIES;
        } else if (lastResult != null && FetchDataRequest.DisplayMode.HEX.equals(lastResult.getDisplayMode())) {
            editorMode = AceEditorMode.STROOM_HEX_DUMP;
        } else {
            editorMode = AceEditorMode.XML;
        }
        textPresenter.setOptionsToDefaultAvailability();

        // Set up hex viewing availability and state
        // If the source location has a data range then it means we are viewing a preview of
        // a sub-set of the data (i.e. from DataDisplaySupport) and it makes no sense to
        // see hex of this sub-set when the sub-set location is reliant on chars being decodeable.
        if (INFO_TAB_NAME.equals(currentTabName)
            || ERROR_TAB_NAME.equals(currentTabName)
            || currentSourceLocation.getDataRange() != null) {
            textPresenter.getViewAsHexOption().setOff();
            textPresenter.getViewAsHexOption().setUnavailable();
        } else {
            textPresenter.getViewAsHexOption().setAvailable();
        }

        if (lastResult != null && lastResult.hasErrors()) {
            showErrors(lastResult);
        } else {
            final boolean shouldFormatData = lastResult != null
                                             && FetchDataRequest.DisplayMode.TEXT.equals(lastResult.getDisplayMode())
                                             && AceEditorMode.XML.equals(editorMode);

            textPresenter.getViewAsHexOption()
                    .setOn(FetchDataRequest.DisplayMode.HEX.equals(lastResult != null
                            ? lastResult.getDisplayMode()
                            : FetchDataRequest.DisplayMode.TEXT));
            textPresenter.setMode(editorMode);
            textPresenter.setText(data, shouldFormatData);
            textPresenter.setControlsVisible(playButtonVisible);
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
            beginSteppingHandler.beginStepping(StepType.REFRESH, stepLocation, getCurrentChildStreamType());
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
        // We know the location but not what type of data we are fetching so first get the meta
        restFactory
                .create(META_RESOURCE)
                .method(res -> res.fetch(sourceLocation.getMetaId()))
                .onSuccess(meta -> {
                    fetchData(meta, sourceLocation, false);
                })
                .onFailure(caught -> {
                    showErrors(
                            sourceLocation,
                            sourceLocation.getOptChildType().orElse(null),
                            null,
                            Collections.singletonList(caught.getMessage()));
                })
                .taskMonitorFactory(dataView)
                .exec();
    }

    public void fetchData(final Meta meta) {
//        currentSourceLocation = SourceLocation.builder(meta.getId())
//                .build();
//        currentStreamType = meta.getTypeName();
//        markerListPresenter.resetExpandedSeverities();
//
////        GWT.log("ID " + getCurrentMetaId()
////                + " streamType " + currentStreamType
////                + " part " + getCurrentPartNo()
////                + " seg " + getCurrentSegmentNo());
//
//        update(true, meta.getTypeName());

        fetchData(meta, SourceLocation.builder(meta.getId()).build(), true);
    }

    private void fetchData(final Meta meta,
                           final SourceLocation sourceLocation,
                           final boolean fireEvents) {
        if (meta != null) {
            if (meta.getId() != sourceLocation.getMetaId()) {
                showErrors(
                        sourceLocation,
                        sourceLocation.getOptChildType().orElse(null),
                        null,
                        Collections.singletonList(
                                "Meta ID mismatch, " + meta.getId() + " vs " + sourceLocation.getMetaId()));
            } else {
                currentStreamType = meta.getTypeName();

                if (sourceLocation.getDataRange() != null) {
                    // We are displaying a specific range of the data so hide the
                    // nav controls
                    dataView.setNavigatorView(null);
                }
                highlights = new ArrayList<>();
                if (sourceLocation.getFirstHighlight() != null) {
                    sourceLocation.getFirstHighlight()
                            .getAsTextRange()
                            .ifPresent(highlights::add);
                }
                highlightMetaId = sourceLocation.getMetaId();
                highlightPartIndex = sourceLocation.getPartIndex();
                highlightChildDataType = sourceLocation.getChildType();

                // Update the stream source.
                if (!Objects.equals(currentSourceLocation, sourceLocation)) {
                    // New data location so re-fetch
                    currentSourceLocation = sourceLocation;
                    markerListPresenter.resetExpandedSeverities();

                    update(fireEvents, currentStreamType);
                } else {
                    // Same location as before so just refresh any markers/highlights, e.g. in stepping
                    refreshHighlights(lastResult);
                    refreshMarkers(lastResult);
                }
            }

            if (initDataViewType != null) {
                if (initDataViewType.equals(DataViewType.INFO)) {
                    setActiveTab(infoTab, currentStreamType);
                    onNewTabSelected(infoTab);
                } else if (initDataViewType.equals(DataViewType.PREVIEW)) {
                    setActiveTab(dataTab, currentStreamType);
                    onNewTabSelected(dataTab);
                }
                setInitDataViewType(null);
            }
        } else {
            // Null meta
            showErrors(
                    sourceLocation,
                    sourceLocation.getOptChildType().orElse(null),
                    null,
                    Collections.singletonList(
                            "Stream does not exist or you do not have permission to view it"));
        }
    }

    public void setDisplayMode(final DisplayMode displayMode) {
        this.displayMode = displayMode;
    }


    public void update(final boolean fireEvents) {
        update(fireEvents, currentStreamType);
    }

    private void update(final boolean fireEvents,
                        final String streamTypeName) {
        // No point in updating if a currentSourceLocation has not been set
        if (!ignoreActions && currentSourceLocation != null) {
            if (isSameStreamAndPartAsLastTime() && currentAvailableStreamTypes != null) {
                // Same stream/part so we know this type is available and
                // therefore no need to update tabs as

                update(fireEvents, streamTypeName, currentAvailableStreamTypes);
            } else {
                // Different stream/part so we need to check which child stream types are available
                // and pick an appropriate one.
                currentAvailableStreamTypes = null;

                final Long currentMetaId = getCurrentMetaId();
                if (currentMetaId != null && currentMetaId >= 0) {
                    restFactory
                            .create(DATA_RESOURCE)
                            .method(res -> res.getChildStreamTypes(
                                    currentSourceLocation.getMetaId(),
                                    currentSourceLocation.getPartIndex()))
                            .onSuccess(availableChildStreamTypes -> {
//                                GWT.log("Received available child stream types " + availableChildStreamTypes);
                                currentAvailableStreamTypes = availableChildStreamTypes;
                                update(fireEvents, streamTypeName, availableChildStreamTypes);
                            })
                            .onFailure(caught ->
                                    itemNavigatorPresenter.setRefreshing(false))
                            .taskMonitorFactory(dataView)
                            .exec();
                } else {
                    showInvalidStreamErrorMsg();
                    itemNavigatorPresenter.setRefreshing(false);
                }
            }
        }
    }

    private void showInvalidStreamErrorMsg() {
        final Long currentMetaId = getCurrentMetaId();
        final long currentPartNo = getCurrentPartIndex() + 1;
        final long currentRecordNo = getCurrentRecordIndex() + 1;
        textPresenter.setErrorText("Error: Invalid stream ID "
                                   + (currentMetaId != null
                ? currentMetaId
                : "null")
                                   + ":"
                                   + currentPartNo
                                   + ":"
                                   + currentRecordNo, "");
        itemNavigatorPresenter.setDisplay(noNavigatorData);
        dataView.setSourceLinkVisible(false, false);
        showTextPresenter();
    }

    private void update(final boolean fireEvents,
                        final String streamTypeName,
                        final Set<String> availableChildStreamTypes) {
        if (INFO_PSEUDO_STREAM_TYPE.equals(effectiveChildStreamType)) {
            updateAvailableAndSelectedTabs(streamTypeName, availableChildStreamTypes);
            refreshMetaInfoPresenterContent(currentSourceLocation.getMetaId());
            refreshProgressBar(false);
            // As we are not hitting the rest service we need to clear this out else it will
            // think we are on a strm/part that we are not.
            lastResult = null;
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
            if (StreamTypeNames.ERROR.equals(currentStreamType)
                && isInErrorMarkerMode()) {
                request.setDisplayMode(FetchDataRequest.DisplayMode.MARKER);
            } else {
                if (textPresenter.getViewAsHexOption().isOnAndAvailable()) {
                    request.setDisplayMode(FetchDataRequest.DisplayMode.HEX);
                } else {
                    request.setDisplayMode(FetchDataRequest.DisplayMode.TEXT);
                }
            }
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

            final boolean isSame = Objects.equals(getCurrentMetaId(), lastId)
                                   && Objects.equals(getCurrentPartIndex(), lastPartNo);
//            GWT.log(lastId + ":" + lastPartNo
//                    + " => "
//                    + getCurrentMetaId() + ":" + getCurrentPartIndex()
//                    + " = " + isSame);
            return isSame;
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

                        restFactory
                                .create(DATA_RESOURCE)
                                .method(res -> res.fetch(request))
                                .onSuccess(result -> {
                                    // If we are queueing more actions then don't
                                    // update the text.
                                    if (actionQueue.size() == 0) {
                                        setPageResponse(result, request.isFireEvents());
                                        itemNavigatorPresenter.setRefreshing(false);
                                    }
                                })
                                .onFailure(caught ->
                                        itemNavigatorPresenter.setRefreshing(false))
                                .taskMonitorFactory(dataView)
                                .exec();
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

            final FetchDataResult fetchDataResult = (FetchDataResult) result;

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
            classificationWrapperView.setClassification(result.getClassification());

            refresh(result);
            updateAvailableAndSelectedTabs(
                    result.getStreamTypeName(),
                    result.getAvailableChildStreamTypes());
        } else {
            classificationWrapperView.setClassification("");

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
            hideTab(infoTab, false);
            hideTab(errorTab, true);
            hideTab(dataTab, false);
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

        if (dataView.getTabBar().getSelectedTab() != null) {
            lastTabName = dataView.getTabBar().getSelectedTab().getLabel();
        }
    }

    private void setActiveTab(final TabData tab, final String streamType) {
//        GWT.log("Setting active tab to " + tab.getLabel());
        dataView.getTabBar().selectTab(tab);
        currentTabName = tab.getLabel();
        updateEditorDisplay();
    }

    private void showHtmlPresenter() {
        itemNavigatorPresenter.setDisplay(noNavigatorData);
        dataView.getLayerContainer().show(htmlPresenter);
    }

    private void showTextPresenter() {
        itemNavigatorPresenter.setDisplay(navigatorData);
        dataView.getLayerContainer().show(textPresenter);
    }

    private void showMarkerPresenter() {
        itemNavigatorPresenter.setDisplay(navigatorData);
        dataView.getLayerContainer().show(markerListPresenter);
    }

    private void hidePresenters() {
        dataView.getLayerContainer().clear();
    }

    private void refresh(final AbstractFetchDataResult result) {

        refreshTextPresenterContent();
        refreshMetaInfoPresenterContent(result != null
                ? result.getSourceLocation().getMetaId()
                : null);

        // Need pagers even if we have errors as data may be multi-part
        setPagers(result);

        if (result != null && result.hasErrors()) {
            // Data may be multi-part so one part may have errors but the rest not
            setNavigationControlsVisible(true);
            dataView.setSourceLinkVisible(true, false);
            dataView.setProgressView(null);
        } else {
            setNavigationControlsVisible(true);
            dataView.setSourceLinkVisible(true, true);
            dataView.setProgressView(progressPresenter.getView());
            refreshProgressBar(result != null);
            itemNavigatorPresenter.refreshNavigator();
            refreshHighlights(result);
            refreshMarkers(result);
        }
    }

    private void refreshTextPresenterContent() {
        if (lastResult != null && lastResult.hasErrors()) {
            final String childStreamText = lastResult.getSourceLocation().getOptChildType()
                    .map(childType -> " (" + childType + ")")
                    .orElse("");
            final String title = "Unable to display stream ["
                                 + lastResult.getSourceLocation().getIdentifierString()
                                 + "]"
                                 + childStreamText
                                 + " as "
                                 + (lastResult.getDisplayMode() != null
                    ? lastResult.getDisplayMode().name().toLowerCase()
                    : "text");
            final String errorText = String.join("\n", lastResult.getErrors());
            setErrorText(title, errorText);
            textPresenter.setControlsVisible(playButtonVisible);
        } else {
            final boolean shouldFormatData = lastResult != null
                                             && FetchDataRequest.DisplayMode.TEXT.equals(lastResult.getDisplayMode())
                                             && AceEditorMode.XML.equals(editorMode);

            textPresenter.setMode(editorMode);
            textPresenter.setText(data, shouldFormatData);
            textPresenter.setControlsVisible(playButtonVisible);
            // Resets the context menu states to default
            textPresenter.setOptionsToDefaultAvailability();
        }
    }

    private void setErrorText(final String title, final String errorText) {
        textPresenter.setErrorText(title, errorText);
        // Hide a load of the editor options that make no sense for an error msg
        textPresenter.setReadOnly(true);
        textPresenter.getShowIndentGuides().setUnavailable();
        textPresenter.getIndicatorsOption().setUnavailable();
        textPresenter.getLineNumbersOption().setUnavailable();
        textPresenter.getLineWrapOption().setUnavailable();
        textPresenter.getShowActiveLineOption().setUnavailable();
        textPresenter.getShowInvisiblesOption().setUnavailable();
        textPresenter.getStylesOption().setUnavailable();
        textPresenter.getUseVimBindingsOption().setUnavailable();

        // Need to be able to view as hex to diagnose what the data is
        textPresenter.getViewAsHexOption().setAvailable();
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
            && Objects.equals(result.getStreamTypeName(), highlightChildDataType)) {
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
        final int pageOffset = 0;
        int pageCount = 0;

        if (result != null) {
            pageCount = result.getTotalItemCount().getCount().intValue();
        }

        markerListPresenter.setData(markers, pageOffset, pageCount);
    }

    public void setBeginSteppingHandler(final BeginSteppingHandler beginSteppingHandler) {
        this.beginSteppingHandler = beginSteppingHandler;
    }

    public void setSteppingSource(final boolean steppingSource) {
        this.steppingSource = steppingSource;
        errorMarkerMode = !steppingSource;
    }

    public void setNavigationControlsVisible(final boolean visible) {
        if (visible) {
            dataView.setNavigatorView(itemNavigatorPresenter.getView());
        } else {
            dataView.setNavigatorView(null);
        }
    }

    private void fetchMetaInfoData(final Long metaId) {
        if (metaId != null) {
            restFactory
                    .create(DATA_RESOURCE)
                    .method(res -> res.viewInfo(metaId))
                    .onSuccess(this::handleMetaInfoResult)
                    .taskMonitorFactory(dataView)
                    .exec();
        }
    }

    private void handleMetaInfoResult(final List<DataInfoSection> dataInfoSections) {
        final TableBuilder tableBuilder = new TableBuilder();
        for (final DataInfoSection section : dataInfoSections) {
            // Add the section header.
            tableBuilder.row(TableCell.header(section.getTitle(), 2));

            // Add rows.
            section.getEntries()
                    .forEach(entry -> {
                        final String key = entry.getKey();
                        final String value = entry.getValue();

                        tableBuilder.row(SafeHtmlUtils.fromString(key), toHtmlLineBreaks(key, value));
                    });
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tableBuilder::write, Attribute.className("infoTable"));
        htmlPresenter.setHtml(htmlBuilder.toSafeHtml().asString());

        OpenLinkUtil.addClickHandler(this, htmlPresenter.getWidget());
    }

    private SafeHtml toHtmlLineBreaks(final String key, final String value) {
        if (value != null) {
            final HtmlBuilder sb = new HtmlBuilder();
            // Change any line breaks html line breaks
            final String[] lines = value.split("\n");
            for (int i = 0; i < lines.length; i++) {
                final String line = lines[i];
                if (i > 0) {
                    sb.appendTrustedString("<br/>");
                }
                sb.append(line);
            }

            final HtmlBuilder copyLinkHtml = new HtmlBuilder();
            CopyTextUtil.render(value, sb.toSafeHtml(), copyLinkHtml, false);
            if (STREAM_ATTRIBUTE_NAMES.contains(key)) {
                return OpenLinkUtil.render(value, LinkType.STREAM, copyLinkHtml.toSafeHtml());
            } else if ("Feed".equals(key)) {
                return OpenLinkUtil.render(value, LinkType.FEED, copyLinkHtml.toSafeHtml());
            }

            return copyLinkHtml.toSafeHtml();
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

    private void showErrors(final AbstractFetchDataResult result) {
        showErrors(
                result.getSourceLocation(),
                result.getSourceLocation()
                        .getOptChildType()
                        .orElse(null),
                result.getDisplayMode(),
                result.getErrors());
    }

    private void showErrors(final SourceLocation sourceLocation,
                            final String childType,
                            final FetchDataRequest.DisplayMode displayMode,
                            final List<String> errors) {
        final String childStreamText = childType != null
                ? (" (" + childType + ")")
                : "";
        final String displayModeText = displayMode != null
                ? (" as " + displayMode.name().toLowerCase())
                : "";
        final String title = "Unable to display source ["
                             + sourceLocation.getIdentifierString()
                             + "]"
                             + childStreamText
                             + displayModeText;

        final String errorText = Stream.concat(
                        errors != null
                                ? errors.stream()
                                : Stream.empty(),
                        Stream.of("You can right click this pane and select 'View as hex' to see the raw data in " +
                                  "hexadecimal form."))
                .collect(Collectors.joining("\n"));

        dataView.setSourceLinkVisible(false, false);
        setErrorText(title, errorText);
        showTextPresenter();
    }

    public void setInitDataViewType(final DataViewType initDataViewType) {
        this.initDataViewType = initDataViewType;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    public interface DataView extends View, Focus, TaskMonitorFactory {

        void addSourceLinkClickHandler(final ClickHandler clickHandler);

        void setSourceLinkVisible(final boolean isVisible, final boolean isEnabled);

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
            return itemRangeSupplier != null
                    ? itemRangeSupplier.get()
                    : OffsetRange.zero();
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
                final int currPage = (int) (itemOffset / maxItemsPerPage);
                final int newPage = Math.max(0, currPage - 1);
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
