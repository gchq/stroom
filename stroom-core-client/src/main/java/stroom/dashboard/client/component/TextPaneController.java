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

package stroom.dashboard.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.main.PaneRegistry.ComponentType;
import stroom.dashboard.client.main.PaneRegistry.ComponentUse;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.data.client.component.ClassificationLabelController;
import stroom.data.shared.DataResource;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.component.CodeEditor;
import stroom.editor.client.component.CodeEditorController;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.query.api.ColumnRef;
import stroom.query.api.SpecialColumns;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TextRange;
import stroom.util.shared.Version;
import stroom.widget.hyperlink.client.HyperlinkEvent;
import stroom.widget.hyperlink.client.LinkValue;
import stroom.widget.text.client.HtmlPane;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// A dashboard's Text pane ([TextPane]): fetches and shows the source of the row selected in a table
/// (or in any table), as text with its matches highlighted or as HTML, and steps it.
public class TextPaneController extends DashboardPaneController {

    /// The tab type of the pane.
    public static final String TAB_TYPE = "text-component";
    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

    /// The pane's type, as the dashboard's Add menu offers it.
    public static final ComponentType TYPE = new ComponentType(
            2, "text", "Text", ComponentUse.PANEL);

    private static final Version CURRENT_MODEL_VERSION = new Version(7, 8, 0);
    private static final ColumnRef DEFAULT_STREAM_ID_COLUMN = new ColumnRef(
            SpecialColumns.RESERVED_STREAM_ID,
            SpecialColumns.RESERVED_STREAM_ID);
    private static final ColumnRef DEFAULT_EVENT_ID_COLUMN = new ColumnRef(
            SpecialColumns.RESERVED_EVENT_ID,
            SpecialColumns.RESERVED_EVENT_ID);
    private static final String OLD_STREAM_ID = "StreamId";
    private static final String OLD_EVENT_ID = "EventId";

    private final TextPane view;
    private final Provider<CodeEditorController> rawEditorProvider;
    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private List<FetchDataRequest> fetchDataQueue;
    private Timer delayedFetchDataTimer;
    private Long currentStreamId;
    private Long currentPartIndex;
    private Long currentRecordIndex;
    private Set<String> currentHighlightStrings;
    private boolean playButtonVisible;

    private HasComponentSelection currentTableController;

    private CodeEditorController rawEditor;
    private HtmlPane htmlPane;

    private boolean isHtml;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The content.
    /// @param settingsProvider              Makes the pane's settings dialog.
    /// @param rawEditorProvider             Makes the text's editor.
    /// @param restFactory                   Fetches the source.
    /// @param securityContext               Says whether the user may step.
    /// @param classificationLabelController Shows the data's classification.
    @Inject
    public TextPaneController(final EventBus eventBus,
                              final TextPane view,
                              final Provider<TextSettingsController> settingsProvider,
                              final Provider<CodeEditorController> rawEditorProvider,
                              final RestFactory restFactory,
                              final ClientSecurityContext securityContext,
                              final ClassificationLabelController classificationLabelController) {
        super(eventBus, settingsProvider);
        this.view = view;
        initWidget(view);
        this.rawEditorProvider = rawEditorProvider;
        this.restFactory = restFactory;
        this.securityContext = securityContext;
        own(classificationLabelController).attach(view.getClassificationLabel());

        view.setOnStep(this::beginStepping);
    }

    private void showData(final String data,
                          final String classification,
                          final Set<String> highlightStrings,
                          final boolean isHtml) {
        TextPaneController.this.isHtml = isHtml;

        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Closed (e.g. the component was removed) before it ran
            if (isDisposed()) {
                return;
            }
            // The play button applies to text (not HTML) when stepping is shown and allowed. It
            // needs a source location, which the text has only once a row is selected
            // (beginStepping needs the stream id), so until then it is shown disabled
            final boolean steppingApplies = !isHtml
                                            && getTextSettings().isShowStepping()
                                            && securityContext.hasAppPermission(
                                                    AppPermission.STEPPING_PERMISSION);
            playButtonVisible = steppingApplies && currentStreamId != null;
            view.setSteppingVisible(steppingApplies);
            view.setSteppingEnabled(currentStreamId != null);

            view.setClassification(classification);
            if (isHtml) {
                initHtmlPane();

                view.setContent(htmlPane);
                htmlPane.setHtml(data);
            } else {
                initRawEditor();

                view.setContent(rawEditor);

                if (highlightStrings != null && !highlightStrings.isEmpty()) {
                    rawEditor.setFormattedHighlights(formattedText ->
                            getHighlights(formattedText, highlightStrings));
                } else {
                    rawEditor.setFormattedHighlights(null);
                }

                rawEditor.setText(data, true);
                rawEditor.setControlsVisible(playButtonVisible);
            }
        });
    }

    private void showError(final FetchDataRequest request,
                           final FetchDataResult fetchDataResult) {
        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Closed (e.g. the component was removed) before it ran
            if (isDisposed()) {
                return;
            }
            // Determine if we should show tha play button.
            // Show the play button if we have fetched input data.
            view.setClassification(fetchDataResult.getClassification());

            initRawEditor();
            view.setContent(rawEditor);

            final String title = "Unable to display stream ["
                                 + fetchDataResult.getSourceLocation().getIdentifierString()
                                 + "]";

            final String errorText = String.join("\n", fetchDataResult.getErrors());

            rawEditor.setErrorText(title, errorText);
        });
    }

    private void initRawEditor() {
        if (rawEditor == null) {
            rawEditor = make(rawEditorProvider).attach(new CodeEditor());
            rawEditor.setReadOnly(true);
            rawEditor.getOptions().getLineNumbersOption().setOn(false);
            rawEditor.getOptions().getLineWrapOption().setOn(true);
        }
    }

    private void initHtmlPane() {
        if (htmlPane == null) {
            htmlPane = new HtmlPane();
            own(htmlPane.addDomHandler(event -> {
                final Element target = event.getNativeEvent().getEventTarget().cast();
                final String link = target.getAttribute("link");
                if (link != null) {
                    final LinkValue hyperlink = LinkValue.create(link);
                    if (hyperlink != null) {
                        HyperlinkEvent.fire(TextPaneController.this, hyperlink, view.getTaskMonitor());
                    }
                }
            }, ClickEvent.getType()));
        }
    }

    // The ranges returned are line/col positions in the input text. Thus the input text should
    // not be changed/formatted after the highlight ranges have been generated.
    private List<TextRange> getHighlights(final String input, final Set<String> highlightStrings) {
        final List<TextRange> highlights = new ArrayList<>();

        // See if we are going to add highlights.
        if (input != null && NullSafe.hasItems(highlightStrings)) {
            final char[] inputChars = input.toLowerCase().toCharArray();
            final int inputLength = inputChars.length;

            // Find out where the highlight elements need to be placed.
            for (final String highlight : highlightStrings) {
                if (highlight == null) {
                    continue;
                }

                final char[] highlightChars = highlight.toLowerCase().toCharArray();
                final int highlightLength = highlightChars.length;

                boolean inElementTag = false;
                boolean inEscapedElement = false;
                int lineNo = 1;
                int colNo = 1;
                char lastInputChar = 0;
                for (int i = 0; i < inputLength; i++) {
                    final char inputChar = inputChars[i];

                    if (lastInputChar == '\n') {
                        lineNo++;
                        colNo = 1;
                    }
                    lastInputChar = inputChar;

                    if (!inElementTag && !inEscapedElement) {
                        if (inputChar == '<') {
                            inElementTag = true;
                        } else if (inputChar == '&'
                                   && i + 3 < inputLength
                                   && inputChars[i + 1] == 'l'
                                   && inputChars[i + 2] == 't'
                                   && inputChars[i + 3] == ';') {
                            inEscapedElement = true;
                        } else {
                            // If we aren't in an element or escaped element
                            // then try to match.
                            boolean found = false;
                            for (int j = 0; j < highlightLength && i + j < inputLength; j++) {
                                final char highlightChar = highlightChars[j];
                                if (inputChars[i + j] != highlightChar) {
                                    break;
                                } else if (j == highlightLength - 1) {
                                    found = true;
                                }
                            }

                            if (found) {
                                // All one based and inclusive
                                final TextRange hl = new TextRange(
                                        new DefaultLocation(lineNo, colNo),
                                        new DefaultLocation(lineNo, colNo + highlightLength - 1)); // inclusive
                                highlights.add(hl);

                                // Skip over the found highlight term
                                i += highlightLength;
                                colNo += highlightLength;
                            }
                        }
                    } else if (inElementTag && inputChar == '>') {
                        inElementTag = false;

                    } else if (inEscapedElement
                               && inputChar == '&'
                               && i + 3 < inputLength
                               && inputChars[i + 1] == 'g'
                               && inputChars[i + 2] == 't'
                               && inputChars[i + 3] == ';') {
                        inEscapedElement = false;
                    }

                    colNo++;
                }
            }
        }

        Collections.sort(highlights);

        return highlights;
    }

    @Override
    public void setDashboardContext(final DashboardContext dashboardContext) {
        super.setDashboardContext(dashboardContext);
        own(dashboardContext.addComponentChangeListener(component -> {
            if (getTextSettings() != null) {
                if (getTextSettings().getTableId() == null) {
                    if (component instanceof HasComponentSelection) {
                        currentTableController = (HasComponentSelection) component;
                        update(currentTableController);
                    }
                } else if (Objects.equals(getTextSettings().getTableId(), component.getId())) {
                    if (component instanceof HasComponentSelection) {
                        currentTableController = (HasComponentSelection) component;
                        update(currentTableController);
                    }
                }
            }
        }));
    }

    private void update(final HasComponentSelection hasSelectedRows) {
        boolean updating = false;
        String message = "";

        final String permissionCheck = checkPermissions();
        if (permissionCheck != null) {
            isHtml = false;
            showData(permissionCheck, null, null, isHtml);
            updating = true;

        } else {
            currentStreamId = null;
            currentPartIndex = null;
            currentRecordIndex = null;
            currentHighlightStrings = null;

            if (hasSelectedRows != null) {
                final List<ComponentSelection> selection = hasSelectedRows.getSelection();
                if (selection != null && selection.size() == 1) {
                    // Just use the first row.
                    final ComponentSelection selected = selection.get(0);
                    currentStreamId = getLong(getTextSettings().getStreamIdColumn(), selected);
                    if (currentStreamId == null) {
                        currentStreamId = getLong(selected.getParamValue(SpecialColumns.RESERVED_STREAM_ID));
                        if (currentStreamId == null) {
                            currentStreamId = getLong(selected.getParamValue(OLD_STREAM_ID));
                        }
                    }
                    currentPartIndex = convertToIndex(getLong(getTextSettings().getPartNoColumn(), selected));
                    currentRecordIndex = convertToIndex(getLong(getTextSettings().getRecordNoColumn(), selected));
                    if (currentRecordIndex == null) {
                        currentRecordIndex = getLong(selected.getParamValue(SpecialColumns.RESERVED_EVENT_ID));
                        if (currentRecordIndex == null) {
                            currentRecordIndex = getLong(selected.getParamValue(OLD_EVENT_ID));
                        }
                    }
                    final Long currentLineFrom = getLong(getTextSettings().getLineFromColumn(), selected);
                    final Long currentColFrom = getLong(getTextSettings().getColFromColumn(), selected);
                    final Long currentLineTo = getLong(getTextSettings().getLineToColumn(), selected);
                    final Long currentColTo = getLong(getTextSettings().getColToColumn(), selected);

                    // Validate settings.
                    if (getTextSettings().getStreamIdColumn() == null) {
                        message = "No stream id field is configured";

                    } else if (getTextSettings().getStreamIdColumn() != null && currentStreamId == null) {
                        message = "No stream id found in selection";

                    } else if (getTextSettings().getRecordNoColumn() == null
                               && !(
                            getTextSettings().getLineFromColumn() != null
                            && getTextSettings().getLineToColumn() != null)) { // Allow just line positions to
                        //                                                               be used rather than record no.
                        message = "No record number field is configured";

                    } else if (getTextSettings().getRecordNoColumn() != null && currentRecordIndex == null) {
                        message = "No record number field found in selection";

                    } else {
                        DataRange dataRange = null;
                        TextRange highlight = null;
                        if (currentLineFrom != null
                            && currentColFrom != null
                            && currentLineTo != null
                            && currentColTo != null) {
                            dataRange = DataRange.between(
                                    new DefaultLocation(currentLineFrom.intValue(), currentColFrom.intValue()),
                                    new DefaultLocation(currentLineTo.intValue(), currentColTo.intValue()));

                            highlight = new TextRange(
                                    new DefaultLocation(currentLineFrom.intValue(), currentColFrom.intValue()),
                                    new DefaultLocation(currentLineTo.intValue(), currentColTo.intValue()));
                        }

                        final SourceLocation sourceLocation = SourceLocation.builder(currentStreamId)
                                .withPartIndex(currentPartIndex != null
                                        ? currentPartIndex
                                        : 0)
                                .withRecordIndex(currentRecordIndex != null
                                        ? currentRecordIndex
                                        : 0)
                                .withDataRange(dataRange)
                                .withHighlight(highlight)
                                .build();

                        currentHighlightStrings = hasSelectedRows.getHighlights();

                        final FetchDataRequest request = new FetchDataRequest(sourceLocation);
                        request.setPipeline(getTextSettings().getPipeline());
                        request.setShowAsHtml(getTextSettings().isShowAsHtml());

                        ensureFetchDataQueue();
                        fetchDataQueue.add(request);
                        delayedFetchDataTimer.cancel();
                        delayedFetchDataTimer.schedule(250);
                        updating = true;
                    }
                }
            }
        }

        // If we aren't updating the data display then clear it.
        if (!updating) {
            showData(message, null, null, isHtml);
        }
    }

    private long getStartLine(final TextRange highlight) {
        final int lineNoFrom = highlight.getFrom().getLineNo();
        if (lineNoFrom == 1) {
            // Content starts on first line so convert to an offset as the server code
            // works in zero based line numbers
            return lineNoFrom - 1;
        } else {
            return lineNoFrom;
        }
    }

    private long getLineCount(final TextRange highlight) {
        final int lineNoFrom = highlight.getFrom().getLineNo();
        if (lineNoFrom == 1) {
            return highlight.getTo().getLineNo() - highlight.getFrom().getLineNo() + 1;
        } else {
            return highlight.getTo().getLineNo() - highlight.getFrom().getLineNo();
        }
    }

    private Long convertToIndex(final Long no) {
        if (no != null) {
            return no - 1;
        }
        return null;
    }

    private Long getLong(final ColumnRef column, final ComponentSelection row) {
        if (column != null && row != null) {
            String val = row.getParamValue(column.getId());
            if (val == null) {
                val = row.getParamValue(column.getName());
            }
            return getLong(val);
        }
        return null;
    }

    private Long getLong(final String string) {
        if (string != null) {
            try {
                return Long.valueOf(string);
            } catch (final NumberFormatException e) {
                // Ignore.
            }
        }

        return null;
    }

    private String checkPermissions() {
        if (!securityContext.hasAppPermission(AppPermission.VIEW_DATA_PERMISSION)) {
            if (!securityContext.hasAppPermission(AppPermission.VIEW_DATA_WITH_PIPELINE_PERMISSION)) {
                return "You do not have permission to display this item";
            } else if (getTextSettings().getPipeline() == null) {
                return "You must choose a pipeline to display this item";
            }
        }

        return null;
    }

    private void ensureFetchDataQueue() {
        if (fetchDataQueue == null) {
            fetchDataQueue = new ArrayList<>();
            delayedFetchDataTimer = new Timer() {
                @Override
                public void run() {
                    // Closed (e.g. the component was removed) while it waited
                    if (isDisposed()) {
                        return;
                    }
                    final FetchDataRequest request = fetchDataQueue.get(fetchDataQueue.size() - 1);
                    fetchDataQueue.clear();

                    restFactory
                            .create(DATA_RESOURCE)
                            .method(res -> res.fetch(request))
                            .onSuccess(result -> {
                                // If we are queueing more actions then don't update
                                // the text.
                                if (fetchDataQueue.isEmpty()) {
                                    final String data = "The data has been deleted or reprocessed since this " +
                                                        "index was built";
                                    boolean isHtml = false;
                                    if (result != null) {
                                        if (result instanceof final FetchDataResult fetchDataResult) {
                                            isHtml = fetchDataResult.isHtml();
                                            if (fetchDataResult.hasErrors()) {
                                                showError(request, fetchDataResult);
                                            } else {
                                                showData(fetchDataResult.getData(),
                                                        fetchDataResult.getClassification(),
                                                        currentHighlightStrings,
                                                        fetchDataResult.isHtml());
                                            }
                                        } else {
                                            isHtml = false;
                                            showData("", null, currentHighlightStrings, false);
                                        }
                                    } else {
                                        showData("", null, currentHighlightStrings, false);
                                    }
                                }
                            })
                            // A reply that arrives once this is disposed is dropped
                            .owner(TextPaneController.this)
                            .taskMonitorFactory(view.getTaskMonitor())
                            .exec();
                }
            };
        }
    }

    @Override
    public void read(final ComponentConfig componentConfig) {
        super.read(componentConfig);

        final TextComponentSettings.Builder builder;
        final ComponentSettings settings = componentConfig.getSettings();

        if (settings instanceof final TextComponentSettings textComponentSettings) {
            builder = textComponentSettings.copy();

            final Version version = Version.parse(textComponentSettings.getModelVersion());
            final boolean old = version.lt(CURRENT_MODEL_VERSION);

            // special field names have changed from EventId to __event_id__ so we need to deal
            // with those and replace them, also rebuild existing special fields just in case
            if (textComponentSettings.getStreamIdColumn() == null
                || (old && OLD_STREAM_ID.equals(textComponentSettings.getStreamIdColumn().getName()))) {
                builder.streamIdField(DEFAULT_STREAM_ID_COLUMN);
            }
            if (textComponentSettings.getRecordNoColumn() == null
                || (old && OLD_EVENT_ID.equals(textComponentSettings.getRecordNoColumn().getName()))) {
                builder.recordNoField(DEFAULT_EVENT_ID_COLUMN);
            }

        } else {
            builder = TextComponentSettings.builder();
            builder.streamIdField(DEFAULT_STREAM_ID_COLUMN);
            builder.recordNoField(DEFAULT_EVENT_ID_COLUMN);
        }

        builder.modelVersion(CURRENT_MODEL_VERSION.toString());
        setSettings(builder.build());
    }

    private TextComponentSettings getTextSettings() {
        return (TextComponentSettings) getSettings();
    }

    @Override
    public void link() {
        final String tableId = getTextSettings().getTableId();
        String newTableId = getDashboardContext()
                .getComponents().validateOrGetLastComponentId(tableId, TableController.TYPE.getId());

        // If we can't get the same table id then set to null so that changes to any table can be listened to.
        if (!Objects.equals(tableId, newTableId)) {
            newTableId = null;
        }

        setSettings(getTextSettings()
                .copy()
                .tableId(newTableId)
                .build());
        update(currentTableController);
    }

    @Override
    protected void changeSettings() {
        super.changeSettings();
        update(currentTableController);
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    private void beginStepping() {
        if (currentStreamId != null) {
            final StepLocation stepLocation = new StepLocation(
                    currentStreamId,
                    currentPartIndex != null
                            ? currentPartIndex
                            : 0,
                    currentRecordIndex != null
                            ? currentRecordIndex
                            : 0);

            BeginPipelineSteppingEvent.fire(
                    this,
                    null,
                    null,
                    StepType.REFRESH,
                    stepLocation,
                    null);
        } else {
            AlertEvent.fireError(this, "No stream id", null);
        }
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
