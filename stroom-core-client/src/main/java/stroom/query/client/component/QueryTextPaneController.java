/*
 * Copyright 2023 Crown Copyright
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

package stroom.query.client.component;

import stroom.data.client.component.ClassificationLabelController;
import stroom.data.shared.DataResource;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.component.CodeEditorController;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.security.client.api.ClientSecurityContext;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.TextRange;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/// The source of a query result's row, beside the results ([QueryTextPane]): fetches the stream's
/// record and shows it as text, formatted and with the result's highlights, or as HTML, whose
/// links it follows.
public class QueryTextPaneController extends Controller {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

//    private static final Version CURRENT_MODEL_VERSION = new Version(6, 1, 26);

    private final QueryTextPane view;
    private final CodeEditorController editor;
    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private List<FetchDataRequest> fetchDataQueue;
    private Timer delayedFetchDataTimer;
    //    private Long currentStreamId;
//    private Long currentPartIndex;
//    private Long currentRecordIndex;
    private Set<String> currentHighlightStrings;
//    private boolean playButtonVisible;

    private boolean isHtml;

    private Runnable showFunction;
    private Runnable hideFunction;

    /// Made by GIN, with its pane.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param view                          The pane.
    /// @param codeEditorControllerProvider  Makes the code editor's controller.
    /// @param restFactory                   Fetches the source.
    /// @param securityContext               The user's permissions.
    /// @param classificationLabelController Colours the classification.
    @Inject
    public QueryTextPaneController(final EventBus eventBus,
                                   final QueryTextPane view,
                                   final Provider<CodeEditorController> codeEditorControllerProvider,
                                   final RestFactory restFactory,
                                   final ClientSecurityContext securityContext,
                                   final ClassificationLabelController classificationLabelController) {
        super(eventBus);
        this.view = view;
        this.restFactory = restFactory;
        this.securityContext = securityContext;
        initWidget(view);
        own(classificationLabelController).attach(view.getClassificationLabel());

        editor = make(codeEditorControllerProvider).attach(view.getEditor());
        editor.setReadOnly(true);
        editor.getOptions().getLineNumbersOption().setOn(false);
        editor.getOptions().getLineWrapOption().setOn(true);

        view.setOnLink(link -> {
            final Hyperlink hyperlink = Hyperlink.create(link);
            if (hyperlink != null) {
                HyperlinkEvent.fire(this, hyperlink, view);
            }
        });

        view.setOnClose(this::close);
    }

    private void showData(final String data,
                          final String classification,
                          final Set<String> highlightStrings,
                          final boolean isHtml) {
        this.isHtml = isHtml;
//        final List<TextRange> highlights = getHighlights(data, highlightStrings);

        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Closed (e.g. the component was removed) before it ran
            if (isDisposed()) {
                return;
            }
//            // Determine if we should show tha play button.
//            playButtonVisible = !isHtml
//                    && getTextSettings().isShowStepping()
//                    && securityContext.hasAppPermission(AppPermissionEnum.STEPPING_PERMISSION);
//
//            // Show the play button if we have fetched input data.
//            getView().setSteppingVisible(playButtonVisible);

            view.setClassification(classification);
            if (isHtml) {
                view.showHtml(data);
            } else {
                view.showEditor();

                if (highlightStrings != null && !highlightStrings.isEmpty()) {
                    editor.setFormattedHighlights(formattedText ->
                            getHighlights(formattedText, highlightStrings));
                } else {
                    editor.setFormattedHighlights(null);
                }

                editor.setText(data, true);
////                rawController.setHighlights(highlights);
//                rawController.setControlsVisible(playButtonVisible);
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
            view.showEditor();

            final String title = "Unable to display stream ["
                                 + fetchDataResult.getSourceLocation().getIdentifierString()
                                 + "]";

            final String errorText = String.join("\n", fetchDataResult.getErrors());

            editor.setErrorText(title, errorText);
        });
    }

    /**
     * The ranges returned are line/col positions in the input text. Thus the input text should
     * not be changed/formatted after the highlight ranges have been generated.
     */
    private List<TextRange> getHighlights(final String input, final Set<String> highlightStrings) {
        final List<TextRange> highlights = new ArrayList<>();

        // See if we are going to add highlights.
        if (input != null && highlightStrings != null && highlightStrings.size() > 0) {
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

    /// Fetches a record's source and shows it, after a short delay in which later requests replace
    /// it.
    ///
    /// @param sourceLocation The record.
    /// @param showFunction   Run once the source has come, to make sure the pane is showing.
    /// @param hideFunction   Run when the user closes the pane.
    public void show(final SourceLocation sourceLocation,
                     final Runnable showFunction,
                     final Runnable hideFunction) {
        this.showFunction = showFunction;
        this.hideFunction = hideFunction;

        final FetchDataRequest request = new FetchDataRequest(sourceLocation);
//        request.setPipeline(getTextSettings().getPipeline());
//        request.setShowAsHtml(getTextSettings().isShowAsHtml());

        ensureFetchDataQueue();
        fetchDataQueue.add(request);
        delayedFetchDataTimer.cancel();
        delayedFetchDataTimer.schedule(250);
    }

    private void close() {
        if (hideFunction != null) {
            hideFunction.run();
        }
    }

//
//    private void update(final TableController tableController) {
//        boolean updating = false;
//        String message = "";
//
//        final String permissionCheck = checkPermissions();
//        if (permissionCheck != null) {
//            isHtml = false;
//            showData(permissionCheck, null, null, isHtml);
//            updating = true;
//
//        } else {
//            currentStreamId = null;
//            currentPartIndex = null;
//            currentRecordIndex = null;
//            currentHighlightStrings = null;
//
//            if (tableController != null) {
//                final List<TableRow> selection = tableController.getSelectedRows();
//                if (selection != null && selection.size() == 1) {
//                  final Field streamIdField = chooseBestField(tableController, getTextSettings().getStreamIdField());
//                    final Field partNoField = chooseBestField(tableController, getTextSettings().getPartNoField());
//                  final Field recordNoField = chooseBestField(tableController, getTextSettings().getRecordNoField());
//                  final Field lineFromField = chooseBestField(tableController, getTextSettings().getLineFromField());
//                    final Field colFromField = chooseBestField(tableController, getTextSettings().getColFromField());
//                    final Field lineToField = chooseBestField(tableController, getTextSettings().getLineToField());
//                    final Field colToField = chooseBestField(tableController, getTextSettings().getColToField());
//
//                    // Just use the first row.
//                    final TableRow selected = selection.get(0);
//                    currentStreamId = getLong(streamIdField, selected);
//                    currentPartIndex = convertToIndex(getLong(partNoField, selected));
//                    currentRecordIndex = convertToIndex(getLong(recordNoField, selected));
//                    final Long currentLineFrom = getLong(lineFromField, selected);
//                    final Long currentColFrom = getLong(colFromField, selected);
//                    final Long currentLineTo = getLong(lineToField, selected);
//                    final Long currentColTo = getLong(colToField, selected);
//
////                    GWT.log("TextPresenter - selected table row = " + selected);
////                    GWT.log("TextPresenter - " +
////                            "streamIdField=" +
////                            streamIdField +
////                            "partNoField=" +
////                            partNoField +
////                            "recordNoField=" +
////                            recordNoField +
////                            "currentStreamId=" +
////                            currentStreamId +
////                            ", currentPartIndex=" +
////                            currentPartIndex +
////                            ", currentRecordIndex=" +
////                            currentRecordIndex);
//
//                    // Validate settings.
//                    if (getTextSettings().getStreamIdField() == null) {
//                        message = "No stream id field is configured";
//
//                    } else if (getTextSettings().getStreamIdField() != null && currentStreamId == null) {
//                        message = "No stream id found in selection";
//
//                    } else if (getTextSettings().getRecordNoField() == null
//                            && !(
//                            getTextSettings().getLineFromField() != null
//                                    && getTextSettings().getLineToField() != null)) { // Allow just line positions to
//                        //                                                              be used rather than record no.
//                        message = "No record number field is configured";
//
//                    } else if (getTextSettings().getRecordNoField() != null && currentRecordIndex == null) {
//                        message = "No record number field found in selection";
//
//                    } else {
//                        DataRange dataRange = null;
//                        TextRange highlight = null;
//                        if (currentLineFrom != null
//                                && currentColFrom != null
//                                && currentLineTo != null
//                                && currentColTo != null) {
//                            dataRange = DataRange.between(
//                                    new DefaultLocation(currentLineFrom.intValue(), currentColFrom.intValue()),
//                                    new DefaultLocation(currentLineTo.intValue(), currentColTo.intValue()));
//
//                            highlight = new TextRange(
//                                    new DefaultLocation(currentLineFrom.intValue(), currentColFrom.intValue()),
//                                    new DefaultLocation(currentLineTo.intValue(), currentColTo.intValue()));
//                        }
//
//                        final SourceLocation sourceLocation = SourceLocation.builder(currentStreamId)
//                                .withPartIndex(currentPartIndex != null
//                                        ? currentPartIndex
//                                        : 0)
//                                .withRecordIndex(currentRecordIndex != null
//                                        ? currentRecordIndex
//                                        : 0)
//                                .withDataRange(dataRange)
//                                .withHighlight(highlight)
//                                .build();
//
//                        currentHighlightStrings = tableController.getHighlights();
//
////                        OffsetRange currentStreamRange = new OffsetRange(sourceLocation.getPartNo() - 1, 1L);
//
////                        Builder dataRangeBuilder = DataRange.builder(currentStreamId)
////                                .withPartNumber(sourceLocation.getPartNo() - 1) // make zero based
////                                .withChildStreamType(null);
//
////                        request.setStreamId(currentStreamId);
////                        request.setStreamRange(currentStreamRange);
////                        request.setChildStreamType(null);
//
//                        // If we have a source highlight then use it.
////                        if (highlight != null) {
////                            // lines 2=>3 means lines 2 & 3, lines 4=>4 means line 4
////                            // -1 to offset to make zero based
////                            // +1 to length to make inclusive
////                            currentPageRange = new OffsetRange(
////                                    highlight.getFrom().getLineNo() - 1L,
////                                    (long) highlight.getTo().getLineNo() - highlight.getFrom().getLineNo() + 1);
////                        } else {
////                            currentPageRange = new OffsetRange(sourceLocation.getRecordNo() - 1L, 1L);
////                        }
//
//                        // TODO @AT Fix/implement
//                        // If we have a source highlight then use it.
////                        if (highlight != null) {
//////                            dataRangeBuilder
//////                                    .fromLocation(highlight.getFrom())
//////                                    .toLocation(highlight.getTo());
////                        } else {
//////                            // TODO assume this is segmented data
////////                            currentPageRange = new OffsetRange(sourceLocation.getRecordNo() - 0L, 1L);
////////                            request.setPageRange(new OffsetRange(sourceLocation.getRecordNo() - 1L, 1L));
//////
//////                            // Convert it to zero based
//////                            dataRangeBuilder.withSegmentNumber(sourceLocation.getSegmentNo() - 1L);
////                        }
//
//                        final FetchDataRequest request = new FetchDataRequest(sourceLocation);
//                        request.setPipeline(getTextSettings().getPipeline());
//                        request.setShowAsHtml(getTextSettings().isShowAsHtml());
//
//                        ensureFetchDataQueue();
//                        fetchDataQueue.add(request);
//                        delayedFetchDataTimer.cancel();
//                        delayedFetchDataTimer.schedule(250);
//                        updating = true;
//                    }
//                }
//            }
//        }
//
//        // If we aren't updating the data display then clear it.
//        if (!updating) {
//            showData(message, null, null, isHtml);
//        }
//    }
//
//    private Field chooseBestField(final TableController tableController, final Field field) {
//        if (field != null) {
//            final TableComponentSettings tableComponentSettings = tableController.getTableSettings();
//            final List<Field> fields = tableComponentSettings.getFields();
//            // Try and choose by id.
//            for (final Field f : fields) {
//                if (f.getId() != null && f.getId().equals(field.getId())) {
//                    return f;
//                }
//            }
//            // Try and choose by name.
//            for (final Field f : fields) {
//                if (f.getName() != null && f.getName().equals(field.getName())) {
//                    return f;
//                }
//            }
//        }
//        return null;
//    }
//
//    private long getStartLine(final TextRange highlight) {
//        int lineNoFrom = highlight.getFrom().getLineNo();
//        if (lineNoFrom == 1) {
//            // Content starts on first line so convert to an offset as the server code
//            // works in zero based line numbers
//            return lineNoFrom - 1;
//        } else {
//            //
//            return lineNoFrom;
//        }
//    }
//
//    private long getLineCount(final TextRange highlight) {
//        int lineNoFrom = highlight.getFrom().getLineNo();
//        if (lineNoFrom == 1) {
//            return highlight.getTo().getLineNo() - highlight.getFrom().getLineNo() + 1;
//        } else {
//            return highlight.getTo().getLineNo() - highlight.getFrom().getLineNo();
//        }
//    }

//    private Long getLong(final Field field, List<Field> fields, final Row row) {
//        if (field != null && fields != null && row != null) {
//            int index = -1;
//
//            if (index == -1 && field.getId() != null) {
//                // Try matching on id alone.
//                for (int i = 0; i < fields.size(); i++) {
//                    if (field.getId().equals(fields.get(i).getId())) {
//                        index = i;
//                        break;
//                    }
//                }
//            }
//
//            if (index == -1 && field.getName() != null) {
//                // Try matching on name alone.
//                for (int i = 0; i < fields.size(); i++) {
//                    if (field.getName().equals(fields.get(i).getName())) {
//                        index = i;
//                        break;
//                    }
//                }
//            }
//
//            if (index != -1) {
//                if (row.getValues().size() > index) {
//                    return getLong(row.getValues().get(index));
//                }
//            }
//        }
//        return null;
//    }

//    private Long convertToIndex(final Long no) {
//        if (no != null) {
//            return no - 1;
//        }
//        return null;
//    }
//
//    private Long getLong(final Column column, final TableRow row) {
//        if (column != null && row != null) {
//            return getLong(row.getText(column.getId()));
//        }
//        return null;
//    }

//    private Long getLong(final String string) {
//        if (string != null) {
//            try {
//                return Long.valueOf(string);
//            } catch (final NumberFormatException e) {
//                // Ignore.
//            }
//        }
//
//        return null;
//    }

//    private String checkPermissions() {
//        if (!securityContext.hasAppPermission(AppPermissionEnum.VIEW_DATA_PERMISSION)) {
//            if (!securityContext.hasAppPermission(AppPermissionEnum.VIEW_DATA_WITH_PIPELINE_PERMISSION)) {
//                return "You do not have permission to display this item";
//            } else if (getTextSettings().getPipeline() == null) {
//                return "You must choose a pipeline to display this item";
//            }
//        }
//
//        return null;
//    }

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
                                if (fetchDataQueue.size() == 0) {
                                    final String data = "The data has been deleted or reprocessed since this " +
                                                        "index was built";
                                    boolean isHtml = false;
                                    if (result != null) {
                                        if (result instanceof FetchDataResult) {
                                            final FetchDataResult fetchDataResult = (FetchDataResult) result;
                                            isHtml = fetchDataResult.isHtml();
                                            if (fetchDataResult.hasErrors()) {
                                                showError(request, fetchDataResult);
                                            } else {
                                                showData(fetchDataResult.getData(),
                                                        fetchDataResult.getClassification(),
                                                        currentHighlightStrings,
                                                        fetchDataResult.isHtml());
                                            }

                                            // Ensure pane is showing.
                                            if (showFunction != null) {
                                                showFunction.run();
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
                            .owner(QueryTextPaneController.this)
                            .taskMonitorFactory(view)
                            .exec();
                }
            };
        }
    }
//
//    @Override
//    public void read(final ComponentConfig componentConfig) {
//        super.read(componentConfig);
//
//        final TextComponentSettings.Builder builder;
//        final ComponentSettings settings = componentConfig.getSettings();
//
//        if (settings instanceof TextComponentSettings) {
//            final TextComponentSettings textComponentSettings = (TextComponentSettings) settings;
//            builder = textComponentSettings.copy();
//
//            final Version version = Version.parse(textComponentSettings.getModelVersion());
//            final boolean old = version.lt(CURRENT_MODEL_VERSION);
//
//            // special field names have changed from EventId to __event_id__ so we need to deal
//            // with those and replace them, also rebuild existing special fields just in case
//            if (textComponentSettings.getStreamIdField() == null
//                    || (old && IndexConstants.STREAM_ID.equals(textComponentSettings.getStreamIdField().getName()))
//                    || (old && textComponentSettings.getStreamIdField().isSpecial())) {
//                builder.streamIdField(TableController.buildSpecialField(IndexConstants.STREAM_ID));
//            }
//            if (textComponentSettings.getRecordNoField() == null
//                    || (old && IndexConstants.EVENT_ID.equals(textComponentSettings.getRecordNoField().getName()))
//                    || (old && textComponentSettings.getRecordNoField().isSpecial())) {
//                builder.recordNoField(TableController.buildSpecialField(IndexConstants.EVENT_ID));
//            }
//
//        } else {
//            builder = TextComponentSettings.builder();
//            builder.streamIdField(TableController.buildSpecialField(IndexConstants.STREAM_ID));
//            builder.recordNoField(TableController.buildSpecialField(IndexConstants.EVENT_ID));
//        }
//
//        builder.modelVersion(CURRENT_MODEL_VERSION.toString());
//        setSettings(builder.build());
//    }
//
//    private TextComponentSettings getTextSettings() {
//        return (TextComponentSettings) getSettings();
//    }
//
//    @Override
//    public void link() {
//        final String tableId = getTextSettings().getTableId();
//        String newTableId = getComponents().validateOrGetLastComponentId(tableId, TableController.TYPE.getId());
//
//        // If we can't get the same table id then set to null so that changes to any table can be listened to.
//        if (!Objects.equals(tableId, newTableId)) {
//            newTableId = null;
//        }
//
//        setSettings(getTextSettings()
//                .copy()
//                .tableId(newTableId)
//                .build());
//        update(currentTableController);
//    }
//
//    @Override
//    public void changeSettings() {
//        super.changeSettings();
//        update(currentTableController);
//    }
//
//    @Override
//    public ComponentType getType() {
//        return TYPE;
//    }
//
//    @Override
//    public void beginStepping() {
//        if (currentStreamId != null) {
//            final StepLocation stepLocation = new StepLocation(
//                    currentStreamId,
//                    currentPartIndex != null
//                            ? currentPartIndex
//                            : 0,
//                    currentRecordIndex != null
//                            ? currentRecordIndex
//                            : 0);
//
//            BeginPipelineSteppingEvent.fire(
//                    this,
//                    null,
//                    null,
//                    StepType.REFRESH,
//                    stepLocation,
//                    null);
//        } else {
//            AlertEvent.fireError(this, "No stream id", null);
//        }
//    }

}
