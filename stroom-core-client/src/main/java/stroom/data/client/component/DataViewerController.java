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

package stroom.data.client.component;

import stroom.data.client.BeginSteppingHandler;
import stroom.data.client.DataViewType;
import stroom.data.client.DisplayMode;
import stroom.data.client.OpenLinkUtil;
import stroom.data.client.OpenLinkUtil.LinkType;
import stroom.data.client.SourceTabPlugin;
import stroom.data.client.event.ShowDataEvent;
import stroom.data.shared.DataInfoSection;
import stroom.data.shared.DataResource;
import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.RestFactory;
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
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.util.shared.HasItems;
import stroom.util.shared.Location;
import stroom.util.shared.Marker;
import stroom.util.shared.NullSafe;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerLayer;
import stroom.widget.datagrid.client.CopyTextUtil;
import stroom.widget.progress.client.Progress;
import stroom.widget.progress.client.SourceProgressBar;
import stroom.widget.tab.client.TabData;
import stroom.widget.tab.client.TabDataImpl;
import stroom.widget.text.client.HtmlPane;
import stroom.widget.util.client.Console;
import stroom.widget.util.client.HtmlBuilder;
import stroom.widget.util.client.HtmlBuilder.Attribute;
import stroom.widget.util.client.HtmlTableBuilder;
import stroom.widget.util.client.HtmlTableCell;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.MouseDownEvent;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.gwt.safehtml.shared.SafeHtmlUtils;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/// A stream's data ([DataViewer]): its Info, Error, Data Preview, Meta and Context, a page or record
/// at a time with a navigator, its markers (e.g. errors) and its classification; it opens the
/// stream's source, or begins stepping through it.
public class DataViewerController
        extends Controller
        implements Focus {

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

    // How long to wait before fetching while the user moves through records, parts or streams
    private static final int FETCH_DELAY_MILLIS = 100;

    private final TabData errorTab = new TabDataImpl(ERROR_TAB_NAME);
    private final TabData dataTab = new TabDataImpl(DATA_PREVIEW_TAB_NAME);
    private final TabData metaTab = new TabDataImpl(META_TAB_NAME);
    private final TabData infoTab = new TabDataImpl(INFO_TAB_NAME);
    private final TabData contextTab = new TabDataImpl(CONTEXT_TAB_NAME);

    private final HtmlPane htmlPane;
    // The pane as a layer of the data view, kept, as the container tells its layers apart by identity
    private final ControllerLayer htmlLayer;
    private final DataTextPaneController textPane;
    private final ControllerLayer textPaneLayer;
    private final ItemNavigatorController itemNavigator;
    private final ClassificationLabelController classificationLabelController;
    private final MarkerListController markerList;
    private final ControllerLayer markerLayer;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private final boolean userHasPipelineSteppingPermission;
    private final NoNavigatorData noNavigatorData = new NoNavigatorData();
    private final NavigatorData navigatorData = new NavigatorData();

    private DataViewer dataView;
    private SourceProgressBar sourceProgressBar;
    // Whether the progress bar belongs in the view at all (not on the Info tab, nor for data with
    // errors), and whether there is progress for it to show; it shows when both are true
    private boolean progressInView = true;
    private boolean progressBarShown;

    private DisplayMode displayMode = null;
    private DataViewType initDataViewType;
    private Boolean errorMarkerMode = null;
    // This is the parent stream type as opposed to the child stream type,
    // i.e. Raw Events rather than say Context
    private String currentStreamType;
    // This is the child stream type, e.g. meta, context
    // but may also be Info which is not really
    private String effectiveChildStreamType;
    // The child stream types of the stream and part last asked about, or null if not known
    private Set<String> currentAvailableStreamTypes = null;
    private Long availableStreamTypesMetaId;
    private long availableStreamTypesPartIndex;
    private DataType curDataType;
    private SourceLocation currentSourceLocation;
    private AbstractFetchDataResult lastResult;
    // The id of the last fetch begun, so that a reply to an earlier one isn't shown
    private int lastFetchId;
    // Likewise of the Info last asked for, and the stream it is for
    private int lastInfoId;
    private Long infoMetaId;
    // Likewise of the stream's details last asked for, for a location
    private int lastLocationId;
    // Begins the fetch that is waiting, if one is
    private Timer delayedFetchDataTimer;
    // Whether the user has chosen View as Hex, which the option itself doesn't say: it is off on tabs
    // that can't show hex
    private boolean viewAsHex;
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

    /// Made by GIN, with its parts; [#attach(DataViewer)] gives it its viewer.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param htmlPane                      Shows the stream's Info.
    /// @param itemNavigator                 Navigates the stream's parts and records.
    /// @param classificationLabelController Gives the classification banner its colours.
    /// @param textPane                      Shows the data.
    /// @param markerList                    Shows the stream's markers, e.g. its errors.
    /// @param sourceTabPlugin               Opens the stream's source in a tab.
    /// @param uiConfigCache                 Gives how much data to show at a time.
    /// @param securityContext               Says whether the user may step through the data.
    /// @param restFactory                   Fetches the data.
    @Inject
    public DataViewerController(final EventBus eventBus,
                                final HtmlPane htmlPane,
                                final ItemNavigatorController itemNavigator,
                                final ClassificationLabelController classificationLabelController,
                                final DataTextPaneController textPane,
                                final MarkerListController markerList,
                                final SourceTabPlugin sourceTabPlugin,
                                final UiConfigCache uiConfigCache,
                                final ClientSecurityContext securityContext,
                                final RestFactory restFactory) {
        super(eventBus);
        this.itemNavigator = own(itemNavigator);
        // The data's pane is one of the layers swapped in and out of the view as tabs change
        this.textPane = own(textPane).attach(new DataTextPane());
        own(markerList);
        this.classificationLabelController = own(classificationLabelController);
        this.htmlPane = htmlPane;
        htmlPane.setAriaLabel(INFO_TAB_NAME);
        this.htmlLayer = new ControllerLayer(htmlPane);
        textPaneLayer = new ControllerLayer(textPane);
        // Use properties mode for meta
        this.markerList = markerList;
        this.markerLayer = new ControllerLayer(markerList);
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;

        markerList.setOnExpandedChange(() -> update(true));

        textPane.setOnBeginStepping(this::beginStepping);
        textPane.setWrapLines(true);
        textPane.getOptions().getViewAsHexOption().setChangeHandler((isOn) -> {
            if (!ignoreActions) {
                viewAsHex = isOn;
                update(false);
            }
        });

        userHasPipelineSteppingPermission = securityContext.hasAppPermission(AppPermission.STEPPING_PERMISSION);

        onDispose(() -> {
            if (delayedFetchDataTimer != null) {
                delayedFetchDataTimer.cancel();
            }
        });
        own(htmlPane.addDomHandler(e ->
                CopyTextUtil.onClick(e.getNativeEvent(), this), MouseDownEvent.getType()));
        own(OpenLinkUtil.addClickHandler(this, htmlPane));
    }

    /// Attaches this to a viewer, e.g. one a template declares, with its tabs, and its navigator
    /// and classification banner to the viewer's. The viewer is then this controller's widget,
    /// e.g. to show it as a dialog.
    ///
    /// @param view The viewer.
    /// @return This, for its owner to keep.
    public DataViewerController attach(final DataViewer view) {
        this.dataView = Objects.requireNonNull(view, "view");
        initWidget(view);
        classificationLabelController.attach(view.getClassificationLabel());
        itemNavigator.attach(view.getNavigator());
        sourceProgressBar = view.getProgressBar();

        addTab(infoTab);
        addTab(errorTab);
        addTab(dataTab);
        addTab(metaTab);
        addTab(contextTab);

        itemNavigator.setDisplay(noNavigatorData);
        view.setOnSourceLink(this::openSourceController);
        view.setSourceLinkVisible(true, true);
        view.setNavigatorVisible(true);
        setProgressInView(true);
        setProgressBarShown(false);

        view.setOnSelectTab(this::onNewTabSelected);
        view.setOnShowMenu(this::fireEvent);
        return this;
    }

    // Puts the progress bar in the view, or takes it out
    private void setProgressInView(final boolean inView) {
        progressInView = inView;
        dataView.setProgressVisible(progressInView && progressBarShown);
    }

    // Shows the progress bar, if it is in the view, or hides it
    private void setProgressBarShown(final boolean shown) {
        progressBarShown = shown;
        dataView.setProgressVisible(progressInView && progressBarShown);
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

    private void openSourceController() {
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
        uiConfigCache.get(guard(uiConfig -> {
            if (uiConfig != null) {
                action.accept(uiConfig.getSource());
            }
        }), dataView.getSpinner());
    }

    private void addTab(final TabData tab) {
        dataView.addTab(tab);
        hideTab(tab, true);
    }

    private void hideTab(final TabData tab, final boolean hide) {
        dataView.setTabHidden(tab, hide);
    }

    private void onNewTabSelected(final TabData tab) {
        currentTabName = tab.getLabel();
        // Make sure tabs don't do anything in stepping mode.
        if (!steppingSource) {
            if (tab != null) {
                // Clear the text controller while we wait for the new data to come down
                textPane.setText(null);

                if (INFO_TAB_NAME.equals(tab.getLabel())) {
                    ignoringActions(() -> setActiveTab(infoTab, null));
                    effectiveChildStreamType = INFO_PSEUDO_STREAM_TYPE;
                    lastTabName = tab.getLabel();
                    showHtmlController();
                    dataView.setSourceLinkVisible(false, false);
                    setNavigationControlsVisible(false);
                    setProgressInView(false);
                    itemNavigator.refreshNavigator();
                    refreshProgressBar(false);
                    refreshTextControllerContent();
                    // Shows the Info, and the tabs if the stream's aren't known yet
                    update(true);
                } else {
                    setNavigationControlsVisible(true);
                    setProgressInView(true);
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

    // Runs something that changes the tab or the editor's options, which mustn't fetch as it does when
    // the user changes them (e.g. View as Hex is turned off on Info)
    private void ignoringActions(final Runnable runnable) {
        final boolean wasIgnoringActions = ignoreActions;
        ignoreActions = true;
        try {
            runnable.run();
        } finally {
            ignoreActions = wasIgnoringActions;
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
        textPane.setOptionsToDefaultAvailability();

        // Set up hex viewing availability and state
        // If the source location has a data range then it means we are viewing a preview of
        // a sub-set of the data (i.e. from DataDisplaySupport) and it makes no sense to
        // see hex of this sub-set when the sub-set location is reliant on chars being decodeable.
        if (INFO_TAB_NAME.equals(currentTabName)
            || ERROR_TAB_NAME.equals(currentTabName)
            || NullSafe.nonNull(currentSourceLocation, SourceLocation::getDataRange)) {
            textPane.getOptions().getViewAsHexOption().setOff();
            textPane.getOptions().getViewAsHexOption().setUnavailable();
        } else {
            textPane.getOptions().getViewAsHexOption().setAvailable();
        }

        if (lastResult != null && lastResult.hasErrors()) {
            showErrors(lastResult);
            textPane.setControlsVisible(playButtonVisible);
        } else {
            final boolean shouldFormatData = lastResult != null
                                             && FetchDataRequest.DisplayMode.TEXT.equals(lastResult.getDisplayMode())
                                             && AceEditorMode.XML.equals(editorMode);

            textPane.getOptions().getViewAsHexOption()
                    .setOn(FetchDataRequest.DisplayMode.HEX.equals(lastResult != null
                            ? lastResult.getDisplayMode()
                            : FetchDataRequest.DisplayMode.TEXT));
            showText(shouldFormatData);
            textPane.setControlsVisible(playButtonVisible);
        }
    }

    // Puts the data in the text pane, unless Info is shown: the text isn't then, and another tab
    // clears it as it fetches its own
    private void showText(final boolean format) {
        if (!INFO_TAB_NAME.equals(currentTabName)) {
            textPane.setMode(editorMode);
            textPane.setText(data, format);
            // The data may be a range of the stream, so number its lines from the range's first
            // line, as SourceViewerController does (a highlight is placed by these numbers)
            textPane.setFirstLineNumber(getFirstLineNo(lastResult));
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

            setProgressBarShown(true);

            // Don't want to confuse the user so the prog bar need to be based on the char/byte content,
            // not segments in a segmented file. Thus for most segmented streams the segment will fit in the
            // data preview and thus will always see 100%
            if (!DataType.SEGMENTED.equals(fetchDataResult.getDataType())
                && fetchDataResult.getOptTotalBytes().isPresent()
                && dataRange.getOptByteOffsetFrom().isPresent()
                && dataRange.getOptByteOffsetTo().isPresent()) {
                // progress based on know byte size and byte offsets
                sourceProgressBar.setProgress(Progress.boundedRange(
                        fetchDataResult.getTotalBytes() - 1, // count to zero based bound
                        dataRange.getByteOffsetFrom(),
                        dataRange.getByteOffsetTo()));
            } else if (fetchDataResult.getTotalCharacterCount() != null
                       && fetchDataResult.getTotalCharacterCount().isExact()
                       && dataRange.getCharOffsetFrom() != null
                       && dataRange.getCharOffsetTo() != null) {
                // progress based on known char count and char offsets
                sourceProgressBar.setProgress(Progress.boundedRange(
                        fetchDataResult.getTotalCharacterCount().getCount() - 1, // count to zero based bound
                        dataRange.getCharOffsetFrom(),
                        dataRange.getCharOffsetTo()));
            } else if (dataRange.getCharOffsetFrom() != null
                       && dataRange.getCharOffsetTo() != null) {
                // progress based on unknown char count and char offsets
                sourceProgressBar.setProgress(Progress.unboundedRange(
                        dataRange.getCharOffsetFrom(),
                        dataRange.getCharOffsetTo()));
            } else {
                dumpRangeInfo(fetchDataResult);
                setProgressBarShown(false);
            }
        } else {
            setProgressBarShown(false);
        }
    }

    private void dumpRangeInfo(final FetchDataResult result) {
        if (result.getSourceLocation() != null) {
            final DataRange dataRange = result.getSourceLocation().getDataRange();
            Console.debug(() -> "DataRange "
                    + " charFrom " + dataRange.getCharOffsetFrom()
                    + " charTo " + dataRange.getCharOffsetTo()
                    + " chars " + result.getTotalCharacterCount()
                    + " byteFrom " + dataRange.getByteOffsetFrom()
                    + " byteTo " + dataRange.getByteOffsetTo()
                    + " bytes " + result.getTotalBytes());
        }
    }

    /// Clears what is shown.
    public void clear() {
        // Set response to null, and drop the reply to any fetch
        beginFetch(false);
        itemNavigator.setRefreshing(false);
        lastLocationId++;
        currentAvailableStreamTypes = null;
        setPageResponse(null);
        currentSourceLocation = null;
    }

    /// Starts stepping through the stream shown, from the record shown.
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
        // There is none once cleared, e.g. with no stream chosen
        if (currentSourceLocation != null) {
            currentSourceLocation = currentSourceLocation.copy()
                    .withChildStreamType(childDataType)
                    .build();
        }
        update(true);
    }

    public void fetchData(final SourceLocation sourceLocation) {
        // We know the location but not what type of data we are fetching so first get the meta
        lastLocationId++;
        final int locationId = lastLocationId;
        restFactory
                .create(META_RESOURCE)
                .method(res -> res.fetch(sourceLocation.getMetaId()))
                .onSuccess(meta -> {
                    if (locationId == lastLocationId) {
                        fetchData(meta, sourceLocation, false);
                    }
                })
                .onFailure(caught -> {
                    if (locationId == lastLocationId) {
                        showFetchError(sourceLocation, caught.getMessage());
                    }
                })
                .owner(this)
                .taskMonitorFactory(dataView.getSpinner())
                .exec();
    }

    public void fetchData(final Meta meta) {
        lastLocationId++;
        fetchData(meta, SourceLocation.builder(meta.getId()).build(), true);
    }

    private void fetchData(final Meta meta,
                           final SourceLocation sourceLocation,
                           final boolean fireEvents) {
        if (meta != null) {
            if (meta.getId() != sourceLocation.getMetaId()) {
                showFetchError(sourceLocation,
                        "Meta ID mismatch, " + meta.getId() + " vs " + sourceLocation.getMetaId());
            } else {
                currentStreamType = meta.getTypeName();

                if (sourceLocation.getDataRange() != null) {
                    // We are displaying a specific range of the data so hide the
                    // nav controls
                    dataView.setNavigatorVisible(false);
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
                    markerList.resetExpandedSeverities();

                    // The user may be moving through a list of streams
                    update(fireEvents, true);
                } else {
                    // Same location as before so just refresh any markers/highlights, e.g. in stepping
                    refreshHighlights(lastResult);
                    refreshMarkers(lastResult);
                }
            }

            if (initDataViewType != null) {
                if (initDataViewType.equals(DataViewType.INFO)) {
                    ignoringActions(() -> setActiveTab(infoTab, currentStreamType));
                    onNewTabSelected(infoTab);
                } else if (initDataViewType.equals(DataViewType.PREVIEW)) {
                    ignoringActions(() -> setActiveTab(dataTab, currentStreamType));
                    onNewTabSelected(dataTab);
                }
                setInitDataViewType(null);
            }
        } else {
            // Null meta
            showFetchError(sourceLocation,
                    "Stream does not exist or you do not have permission to view it");
        }
    }

    // Shows why a stream can't be fetched, forgetting the one before, so that a late reply for it isn't
    // shown instead
    private void showFetchError(final SourceLocation sourceLocation, final String message) {
        if (currentSourceLocation != null) {
            clear();
        }
        showErrors(
                sourceLocation,
                sourceLocation.getOptChildType().orElse(null),
                null,
                Collections.singletonList(message));
    }

    public void setDisplayMode(final DisplayMode displayMode) {
        this.displayMode = displayMode;
    }

    /// Fetches what is shown again, at once.
    ///
    /// @param fireEvents Whether the request says to fire events.
    public void update(final boolean fireEvents) {
        update(fireEvents, false);
    }

    // Fetches what is shown, after a delay if the user may be moving through records, parts or streams,
    // so that they can without sending requests for each
    private void update(final boolean fireEvents, final boolean delayed) {
        // No point in updating if a currentSourceLocation has not been set
        if (!ignoreActions && currentSourceLocation != null) {
            final Fetch fetch = beginFetch(fireEvents);

            // Don't show one stream's Info for another
            if (!Objects.equals(infoMetaId, getCurrentMetaId())) {
                clearInfo();
            }

            if (delayed) {
                itemNavigator.setRefreshing(true);
                delayedFetchDataTimer = new Timer() {
                    @Override
                    public void run() {
                        update(fetch);
                    }
                };
                delayedFetchDataTimer.schedule(FETCH_DELAY_MILLIS);
            } else {
                update(fetch);
            }
        }
    }

    private void update(final Fetch fetch) {
        if (areAvailableStreamTypesKnown()) {
            // Same stream/part so we know which child stream types are available
            update(fetch, currentAvailableStreamTypes);
        } else {
            // Different stream/part so we need to check which child stream types are available
            // and pick an appropriate one.
            currentAvailableStreamTypes = null;

            final Long currentMetaId = getCurrentMetaId();
            if (currentMetaId != null && currentMetaId >= 0) {
                final long partIndex = currentSourceLocation.getPartIndex();
                restFactory
                        .create(DATA_RESOURCE)
                        .method(res -> res.getChildStreamTypes(currentMetaId, partIndex))
                        .onSuccess(availableChildStreamTypes -> {
                            if (isLatest(fetch)) {
                                setAvailableStreamTypes(currentMetaId, partIndex, availableChildStreamTypes);
                                update(fetch, availableChildStreamTypes);
                            }
                        })
                        .onFailure(caught -> {
                            if (isLatest(fetch)) {
                                itemNavigator.setRefreshing(false);
                                showSimpleError(caught.getMessage());
                                // Known not to be available, so as not to ask, and fail, with each tab;
                                // the data's reply may know better
                                setAvailableStreamTypes(currentMetaId, partIndex, Collections.emptySet());
                                update(fetch, currentAvailableStreamTypes);
                            }
                        })
                        .owner(this)
                        .taskMonitorFactory(dataView.getSpinner())
                        .exec();
            } else {
                showInvalidStreamErrorMsg();
                itemNavigator.setRefreshing(false);
            }
        }
    }

    private void setAvailableStreamTypes(final Long metaId,
                                         final long partIndex,
                                         final Set<String> availableChildStreamTypes) {
        currentAvailableStreamTypes = availableChildStreamTypes;
        availableStreamTypesMetaId = metaId;
        availableStreamTypesPartIndex = partIndex;
    }

    // Begins a fetch, so that a request that is waiting isn't sent, and the reply to one that has been
    // isn't shown
    private Fetch beginFetch(final boolean fireEvents) {
        if (delayedFetchDataTimer != null) {
            delayedFetchDataTimer.cancel();
        }
        lastFetchId++;
        return new Fetch(lastFetchId, fireEvents);
    }

    private boolean isLatest(final Fetch fetch) {
        return fetch.id() == lastFetchId;
    }

    private boolean areAvailableStreamTypesKnown() {
        return currentAvailableStreamTypes != null
               && Objects.equals(getCurrentMetaId(), availableStreamTypesMetaId)
               && getCurrentPartIndex() == availableStreamTypesPartIndex;
    }

    private void showInvalidStreamErrorMsg() {
        final Long currentMetaId = getCurrentMetaId();
        final long currentPartNo = getCurrentPartIndex() + 1;
        final long currentRecordNo = getCurrentRecordIndex() + 1;
        textPane.setErrorText("Error: Invalid stream ID "
                                   + (currentMetaId != null
                ? currentMetaId
                : "null")
                                   + ":"
                                   + currentPartNo
                                   + ":"
                                   + currentRecordNo, "");
        itemNavigator.setDisplay(noNavigatorData);
        dataView.setSourceLinkVisible(false, false);
        showTextController();
    }

    private void update(final Fetch fetch,
                        final Set<String> availableChildStreamTypes) {
        if (INFO_PSEUDO_STREAM_TYPE.equals(effectiveChildStreamType)) {
            // As we are not hitting the rest service we need to clear this out else it will
            // think we are on a strm/part that we are not.
            if (!isSameStreamAndPartAsLastTime()) {
                // Nor is the classification known, until the Info's reply: show none, rather than another
                // stream's
                if (!isSameStreamAsLastTime()) {
                    dataView.setClassification("");
                }
                lastResult = null;
            }
            ignoringActions(() -> updateAvailableAndSelectedTabs(currentStreamType, availableChildStreamTypes));
            refreshMetaInfoControllerContent(currentSourceLocation.getMetaId());
            refreshProgressBar(false);
            itemNavigator.setRefreshing(false);
        } else {
            // Tabs will be updated by updateFromResource
            setEffectiveChildStreamType(currentStreamType, availableChildStreamTypes);
            updateFromResource(fetch);
        }
    }

    private void setEffectiveChildStreamType(final String streamTypeName,
                                             final Set<String> availableChildStreamTypes) {
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

    private void updateFromResource(final Fetch fetch) {
        final Severity[] expandedSeverities = markerList.getExpandedSeverities();

        doWithConfig(sourceConfig -> {
            if (!isLatest(fetch)) {
                return;
            }

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
                // A range of the data can't be shown as hex (updateEditorDisplay)
                if (viewAsHex && currentSourceLocation.getDataRange() == null) {
                    request.setDisplayMode(FetchDataRequest.DisplayMode.HEX);
                } else {
                    request.setDisplayMode(FetchDataRequest.DisplayMode.TEXT);
                }
            }
            request.setExpandedSeverities(expandedSeverities);
            request.setFireEvents(fetch.fireEvents());
            doFetch(request, fetch);
        });
    }

    private boolean isSameStreamAndPartAsLastTime() {
        return isSameStreamAsLastTime()
               && getCurrentPartIndex() == lastResult.getSourceLocation().getPartIndex();
    }

    private boolean isSameStreamAsLastTime() {
        final SourceLocation lastLocation = NullSafe.get(lastResult, AbstractFetchDataResult::getSourceLocation);
        return lastLocation != null && Objects.equals(getCurrentMetaId(), lastLocation.getMetaId());
    }

    private void doFetch(final FetchDataRequest request, final Fetch fetch) {
        itemNavigator.setRefreshing(true);
        restFactory
                .create(DATA_RESOURCE)
                .method(res -> res.fetch(request))
                .onSuccess(result -> {
                    if (isLatest(fetch)) {
                        setPageResponse(result);
                        itemNavigator.setRefreshing(false);
                    }
                })
                .onFailure(caught -> {
                    if (isLatest(fetch)) {
                        showFetchFailure(caught.getMessage());
                    }
                })
                .owner(this)
                .taskMonitorFactory(dataView.getSpinner())
                .exec();
    }

    // Shows why the data couldn't be fetched, in the tab chosen and in place of the data before, with the
    // navigator able to fetch it again
    private void showFetchFailure(final String message) {
        itemNavigator.setRefreshing(false);
        if (!isSameStreamAsLastTime()) {
            // Neither the classification nor how many records there are is known
            dataView.setClassification("");
            navigatorData.updateStateForOneItemPerPage(
                    Count.of(0L, false),
                    this::setCurrentRecordIndex,
                    this::getCurrentRecordIndex,
                    RECORD_PAGER_UNIT);
        }
        lastResult = null;
        data = "";
        markers = null;
        ignoringActions(() -> updateAvailableAndSelectedTabs(currentStreamType, currentAvailableStreamTypes));
        setNavigationControlsVisible(true);
        refreshProgressBar(false);
        showSimpleError(message);
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
    }

    long getCurrentErrorsPageOffset() {
        return getCurrentRecordIndex() / SourceLocation.MAX_ERRORS_PER_PAGE;
    }

    void setCurrentErrorsPageOffset(final long pageOffset) {
        setCurrentRecordIndex(pageOffset * SourceLocation.MAX_ERRORS_PER_PAGE);
    }

    private void setPageResponse(final AbstractFetchDataResult result) {
        ignoringActions(() -> showPageResponse(result));
    }

    private void showPageResponse(final AbstractFetchDataResult result) {
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
                    textPane.setWrapLines(false);
                } else {
                    final int lineCount = result.getSourceLocation().getOptDataRange()
                            .flatMap(DataRange::getLineCount)
                            .orElse(0);

                    // This may conflict with what the user has selected with the right click menu
                    if (lineCount == 1) {
                        // No line breaks so we need to wrap else the line could be massive
                        textPane.setWrapLines(true);
                    } else {
                        // probably one record per line so for csv type data easier to read non-wrapped
                        textPane.setWrapLines(false);
                    }
                    textPane.setWrapLines(lineCount == 1);
                }
            }

            if (result.getAvailableChildStreamTypes() != null && result.getSourceLocation() != null) {
                setAvailableStreamTypes(result.getSourceLocation().getMetaId(),
                        result.getSourceLocation().getPartIndex(),
                        result.getAvailableChildStreamTypes());
            }

            // Let the classification handler know that the classification has
            // changed.
            dataView.setClassification(result.getClassification());

            refresh(result);
            updateAvailableAndSelectedTabs(
                    result.getStreamTypeName(),
                    result.getAvailableChildStreamTypes());
        } else {
            dataView.setClassification("");

            refresh(result);
            updateAvailableAndSelectedTabs(null, null);
        }
    }

    private void updateAvailableAndSelectedTabs(final String streamType,
                                                final Set<String> availableChildStreamTypes) {

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
            showHtmlController();
            itemNavigator.setDisplay(noNavigatorData);
        } else {
            // Select the tab based
            final TabData newActiveTab;
            if (INFO_PSEUDO_STREAM_TYPE.equals(effectiveChildStreamType)) {
                newActiveTab = infoTab;
                setActiveTab(newActiveTab, streamType);
                showHtmlController();
            } else if (isInErrorMarkerMode() && StreamTypeNames.ERROR.equals(streamType)) {
                newActiveTab = errorTab;
                effectiveChildStreamType = null;
                setActiveTab(newActiveTab, streamType);
                showMarkerController();
            } else if (StreamTypeNames.META.equals(effectiveChildStreamType)) {
                newActiveTab = metaTab;
                setActiveTab(newActiveTab, streamType);
                showTextController();
            } else if (StreamTypeNames.CONTEXT.equals(effectiveChildStreamType)) {
                newActiveTab = contextTab;
                setActiveTab(newActiveTab, streamType);
                showTextController();
            } else {
                newActiveTab = dataTab;
                effectiveChildStreamType = null;
                setActiveTab(newActiveTab, streamType);
                showTextController();
            }
            // Now we have changed tabs, ensure the nav visibility is right
            itemNavigator.refreshNavigator();
        }

        if (dataView.getSelectedTab() != null) {
            lastTabName = dataView.getSelectedTab().getLabel();
        }
    }

    private void setActiveTab(final TabData tab, final String streamType) {
        Console.debug(() -> "Setting active tab to " + tab.getLabel());
        dataView.selectTab(tab);
        currentTabName = tab.getLabel();
        updateEditorDisplay();
    }

    private void showHtmlController() {
        itemNavigator.setDisplay(noNavigatorData);
        dataView.showLayer(htmlLayer);
    }

    private void showTextController() {
        itemNavigator.setDisplay(navigatorData);
        dataView.showLayer(textPaneLayer);
    }

    private void showMarkerController() {
        itemNavigator.setDisplay(navigatorData);
        dataView.showLayer(markerLayer);
    }

    private void hideControllers() {
        dataView.clearLayers();
    }

    private void refresh(final AbstractFetchDataResult result) {
        // Choosing the tab shows the text, in the tab's mode, but none is chosen without a stream type
        if (result == null || result.getStreamTypeName() == null) {
            refreshTextControllerContent();
        }
        refreshMetaInfoControllerContent(result != null
                ? result.getSourceLocation().getMetaId()
                : null);

        // Need pagers even if we have errors as data may be multi-part
        setPagers(result);

        if (result != null && result.hasErrors()) {
            // Data may be multi-part so one part may have errors but the rest not
            setNavigationControlsVisible(true);
            dataView.setSourceLinkVisible(true, false);
            setProgressInView(false);
        } else {
            setNavigationControlsVisible(true);
            dataView.setSourceLinkVisible(true, true);
            setProgressInView(true);
            refreshProgressBar(result != null);
            itemNavigator.refreshNavigator();
            refreshHighlights(result);
            refreshMarkers(result);
        }
    }

    private void refreshTextControllerContent() {
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
            textPane.setControlsVisible(playButtonVisible);
        } else {
            final boolean shouldFormatData = lastResult != null
                                             && FetchDataRequest.DisplayMode.TEXT.equals(lastResult.getDisplayMode())
                                             && AceEditorMode.XML.equals(editorMode);

            showText(shouldFormatData);
            textPane.setControlsVisible(playButtonVisible);
            // Resets the context menu states to default
            textPane.setOptionsToDefaultAvailability();
        }
    }

    private void setErrorText(final String title, final String errorText) {
        textPane.setErrorText(title, errorText);
        // Hide a load of the editor options that make no sense for an error msg
        textPane.setReadOnly(true);
        textPane.getOptions().getShowIndentGuides().setUnavailable();
        textPane.getOptions().getIndicatorsOption().setUnavailable();
        textPane.getOptions().getLineNumbersOption().setUnavailable();
        textPane.getOptions().getLineWrapOption().setUnavailable();
        textPane.getOptions().getHighlightActiveLineOption().setUnavailable();
        textPane.getOptions().getShowInvisiblesOption().setUnavailable();
        textPane.getOptions().getStylesOption().setUnavailable();
        textPane.getOptions().getUseVimBindingsOption().setUnavailable();

        // Need to be able to view as hex to diagnose what the data is
        textPane.getOptions().getViewAsHexOption().setAvailable();
    }

    private void refreshMetaInfoControllerContent(final Long metaId) {

        if (metaId != null && INFO_TAB_NAME.equals(lastTabName)) {
            fetchMetaInfoData(metaId);
        } else {
            clearInfo();
        }
    }

    // Clears the Info, and drops any reply to come
    private void clearInfo() {
        lastInfoId++;
        infoMetaId = null;
        htmlPane.setHtml(null);
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
            // Compare child types: the result's stream type name is that of the stream (e.g.
            // 'Events'), never the null child type of a highlight in the main data
            && isSameChildType(result.getSourceLocation().getChildType(), highlightChildDataType)) {
            // Set the content to be displayed in the source view with a
            // highlight.
            textPane.setHighlights(highlights);
        } else {
            // Set the content to be displayed in the source view without a
            // highlight.
            textPane.setHighlights(null);
        }
    }

    private static int getFirstLineNo(final AbstractFetchDataResult result) {
        if (result == null || result.getSourceLocation() == null) {
            return 1;
        }
        return result.getSourceLocation()
                .getOptDataRange()
                .flatMap(DataRange::getOptLocationFrom)
                .map(Location::getLineNo)
                .orElse(1);
    }

    private static boolean isSameChildType(final String childType1, final String childType2) {
        // Not Objects.equals: in compiled GWT, a child type that was never set can be undefined
        // rather than null, and Objects.equals sees the two as different
        if (childType1 == null) {
            return childType2 == null;
        }
        return childType1.equals(childType2);
    }

    private void refreshMarkers(final AbstractFetchDataResult result) {
        final int pageOffset = 0;
        int pageCount = 0;

        if (result != null) {
            pageCount = result.getTotalItemCount().getCount().intValue();
        }

        markerList.setData(markers, pageOffset, pageCount);
    }

    public void setBeginSteppingHandler(final BeginSteppingHandler beginSteppingHandler) {
        this.beginSteppingHandler = beginSteppingHandler;
    }

    public void setSteppingSource(final boolean steppingSource) {
        this.steppingSource = steppingSource;
        errorMarkerMode = !steppingSource;
    }

    public void setNavigationControlsVisible(final boolean visible) {
        // A specific range of the data (e.g. a preview from DataDisplaySupport) has nothing to
        // navigate, so its navigator stays hidden (as fetchData hides it)
        final boolean isDataRange = currentSourceLocation != null && currentSourceLocation.getDataRange() != null;
        if (visible && !isDataRange) {
            dataView.setNavigatorVisible(true);
        } else {
            dataView.setNavigatorVisible(false);
        }
    }

    private void fetchMetaInfoData(final Long metaId) {
        if (metaId != null) {
            lastInfoId++;
            infoMetaId = metaId;
            final int infoId = lastInfoId;
            restFactory
                    .create(DATA_RESOURCE)
                    .method(res -> res.viewInfo(metaId))
                    .onSuccess(result -> {
                        if (infoId == lastInfoId) {
                            handleMetaInfoResult(result);
                        }
                    })
                    .owner(this)
                    .taskMonitorFactory(dataView.getSpinner())
                    .exec();
        }
    }

    private void handleMetaInfoResult(final List<DataInfoSection> dataInfoSections) {
        // The stream's data, which its classification otherwise comes with, may not have been shown
        for (final DataInfoSection section : dataInfoSections) {
            if (DataInfoSection.STREAM.equals(section.getTitle())) {
                for (final DataInfoSection.Entry entry : section.getEntries()) {
                    if (DataInfoSection.CLASSIFICATION.equals(entry.getKey())) {
                        dataView.setClassification(entry.getValue());
                    }
                }
            }
        }

        final HtmlTableBuilder tableBuilder = new HtmlTableBuilder();
        for (final DataInfoSection section : dataInfoSections) {
            // Add the section header.
            tableBuilder.row(HtmlTableCell.header(section.getTitle(), 2));

            // Add rows.
            section.getEntries()
                    .forEach(entry -> {
                        final String key = entry.getKey();
                        final String value = entry.getValue();

                        tableBuilder.row(SafeHtmlUtils.fromString(key), toHtmlLineBreaks(key, value));
                    });
        }

        final HtmlBuilder htmlBuilder = new HtmlBuilder();
        htmlBuilder.div(tableBuilder::write, Attribute.className("info-table"));
        htmlPane.setHtml(htmlBuilder.toSafeHtml().asString());
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
        showTextController();
    }

    private void showSimpleError(final String errorText) {
        dataView.setSourceLinkVisible(false, false);
        setErrorText("Unable to display source", errorText);
        showTextController();
    }

    public void setInitDataViewType(final DataViewType initDataViewType) {
        this.initDataViewType = initDataViewType;
    }

    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    // A fetch of what is shown: its id, and whether its request says to fire events
    private record Fetch(int id, boolean fireEvents) {

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
            // The user may be paging
            update(false, true);
        }

        @Override
        public Count<Long> getTotalItemsCount() {
            return totalItemCount;
        }

        @Override
        public boolean isLastPage() {
            // With no idea how many there are, as the first fetch failed, there are none to move to
            return (!totalItemCount.isExact() && totalItemCount.getCount() == 0)
                   || HasItems.super.isLastPage();
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
            update(false);
        }
    }
}
