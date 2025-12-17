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

package stroom.dashboard.client.text;

import stroom.alert.client.event.AlertEvent;
import stroom.dashboard.client.main.AbstractComponentPresenter;
import stroom.dashboard.client.main.Component;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.main.ComponentRegistry.ComponentUse;
import stroom.dashboard.client.main.DashboardContext;
import stroom.dashboard.client.table.ComponentSelection;
import stroom.dashboard.client.table.HasComponentSelection;
import stroom.dashboard.client.table.TablePresenter;
import stroom.dashboard.shared.ComponentConfig;
import stroom.dashboard.shared.ComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.data.shared.DataResource;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.HtmlPresenter;
import stroom.hyperlink.client.Hyperlink;
import stroom.hyperlink.client.HyperlinkEvent;
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
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.NullSafe;
import stroom.util.shared.TextRange;
import stroom.util.shared.Version;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class TextPresenter
        extends AbstractComponentPresenter<TextPresenter.TextView>
        implements TextUiHandlers {

    public static final String TAB_TYPE = "text-component";
    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

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

    private final Provider<EditorPresenter> rawPresenterProvider;
    private final Provider<HtmlPresenter> htmlPresenterProvider;
    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private List<FetchDataRequest> fetchDataQueue;
    private Timer delayedFetchDataTimer;
    private Long currentStreamId;
    private Long currentPartIndex;
    private Long currentRecordIndex;
    private Set<String> currentHighlightStrings;
    private boolean playButtonVisible;

    private HasComponentSelection currentTablePresenter;

    private EditorPresenter rawPresenter;
    private HtmlPresenter htmlPresenter;

    private boolean isHtml;

    @Inject
    public TextPresenter(final EventBus eventBus,
                         final TextView view,
                         final Provider<TextSettingsPresenter> settingsPresenterProvider,
                         final Provider<EditorPresenter> rawPresenterProvider,
                         final Provider<HtmlPresenter> htmlPresenterProvider,
                         final RestFactory restFactory,
                         final ClientSecurityContext securityContext) {
        super(eventBus, view, settingsPresenterProvider);
        this.rawPresenterProvider = rawPresenterProvider;
        this.htmlPresenterProvider = htmlPresenterProvider;
        this.restFactory = restFactory;
        this.securityContext = securityContext;

        view.setUiHandlers(this);
    }

    private void showData(final String data,
                          final String classification,
                          final Set<String> highlightStrings,
                          final boolean isHtml) {
        TextPresenter.this.isHtml = isHtml;
//        final List<TextRange> highlights = getHighlights(data, highlightStrings);

        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Determine if we should show tha play button.
            playButtonVisible = !isHtml
                                && getTextSettings().isShowStepping()
                                && securityContext.hasAppPermission(AppPermission.STEPPING_PERMISSION);

            // Show the play button if we have fetched input data.
            getView().setSteppingVisible(playButtonVisible);

            getView().setClassification(classification);
            if (isHtml) {
                initHtmlPresenter();

                getView().setContent(htmlPresenter.getView());
                htmlPresenter.setHtml(data);
            } else {
                initRawPresenter();

                getView().setContent(rawPresenter.getView());

                if (highlightStrings != null && !highlightStrings.isEmpty()) {
                    rawPresenter.setFormattedHighlights(formattedText ->
                            getHighlights(formattedText, highlightStrings));
                } else {
                    rawPresenter.setFormattedHighlights(null);
                }

                rawPresenter.setText(data, true);
//                rawPresenter.setHighlights(highlights);
                rawPresenter.setControlsVisible(playButtonVisible);
            }
        });
    }

    private void showError(final FetchDataRequest request,
                           final FetchDataResult fetchDataResult) {
        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Determine if we should show tha play button.
            // Show the play button if we have fetched input data.
            getView().setClassification(fetchDataResult.getClassification());

            initRawPresenter();
            getView().setContent(rawPresenter.getView());

            final String title = "Unable to display stream ["
                                 + fetchDataResult.getSourceLocation().getIdentifierString()
                                 + "]";

            final String errorText = String.join("\n", fetchDataResult.getErrors());

            rawPresenter.setErrorText(title, errorText);
        });
    }

    private void initRawPresenter() {
        if (rawPresenter == null) {
            rawPresenter = rawPresenterProvider.get();
            rawPresenter.setReadOnly(true);
            rawPresenter.getLineNumbersOption().setOn(false);
            rawPresenter.getLineWrapOption().setOn(true);
        }
    }

    private void initHtmlPresenter() {
        if (htmlPresenter == null) {
            htmlPresenter = htmlPresenterProvider.get();
            htmlPresenter.getWidget().addDomHandler(event -> {
                final Element target = event.getNativeEvent().getEventTarget().cast();
                final String link = target.getAttribute("link");
                if (link != null) {
                    final Hyperlink hyperlink = Hyperlink.create(link);
                    if (hyperlink != null) {
                        HyperlinkEvent.fire(TextPresenter.this, hyperlink, getView());
                    }
                }
            }, ClickEvent.getType());
        }
    }

    /**
     * The ranges returned are line/col positions in the input text. Thus the input text should
     * not be changed/formatted after the highlight ranges have been generated.
     */
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
        registerHandler(dashboardContext.addComponentChangeHandler(event -> {
            if (getTextSettings() != null) {
                final Component component = event.getComponent();
                if (getTextSettings().getTableId() == null) {
                    if (component instanceof HasComponentSelection) {
                        currentTablePresenter = (HasComponentSelection) component;
                        update(currentTablePresenter);
                    }
                } else if (Objects.equals(getTextSettings().getTableId(), event.getComponentId())) {
                    if (component instanceof HasComponentSelection) {
                        currentTablePresenter = (HasComponentSelection) component;
                        update(currentTablePresenter);
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

//                    GWT.log("TextPresenter - selected table row = " + selected);
//                    GWT.log("TextPresenter - " +
//                            "streamIdField=" +
//                            streamIdField +
//                            "partNoField=" +
//                            partNoField +
//                            "recordNoField=" +
//                            recordNoField +
//                            "currentStreamId=" +
//                            currentStreamId +
//                            ", currentPartIndex=" +
//                            currentPartIndex +
//                            ", currentRecordIndex=" +
//                            currentRecordIndex);

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

//                        OffsetRange currentStreamRange = new OffsetRange(sourceLocation.getPartNo() - 1, 1L);

//                        Builder dataRangeBuilder = DataRange.builder(currentStreamId)
//                                .withPartNumber(sourceLocation.getPartNo() - 1) // make zero based
//                                .withChildStreamType(null);

//                        request.setStreamId(currentStreamId);
//                        request.setStreamRange(currentStreamRange);
//                        request.setChildStreamType(null);

                        // If we have a source highlight then use it.
//                        if (highlight != null) {
//                            // lines 2=>3 means lines 2 & 3, lines 4=>4 means line 4
//                            // -1 to offset to make zero based
//                            // +1 to length to make inclusive
//                            currentPageRange = new OffsetRange(
//                                    highlight.getFrom().getLineNo() - 1L,
//                                    (long) highlight.getTo().getLineNo() - highlight.getFrom().getLineNo() + 1);
//                        } else {
//                            currentPageRange = new OffsetRange(sourceLocation.getRecordNo() - 1L, 1L);
//                        }

                        // TODO @AT Fix/implement
                        // If we have a source highlight then use it.
//                        if (highlight != null) {
////                            dataRangeBuilder
////                                    .fromLocation(highlight.getFrom())
////                                    .toLocation(highlight.getTo());
//                        } else {
////                            // TODO assume this is segmented data
//////                            currentPageRange = new OffsetRange(sourceLocation.getRecordNo() - 0L, 1L);
//////                            request.setPageRange(new OffsetRange(sourceLocation.getRecordNo() - 1L, 1L));
////
////                            // Convert it to zero based
////                            dataRangeBuilder.withSegmentNumber(sourceLocation.getSegmentNo() - 1L);
//                        }

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
            //
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
                            .taskMonitorFactory(getView())
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
                .getComponents().validateOrGetLastComponentId(tableId, TablePresenter.TYPE.getId());

        // If we can't get the same table id then set to null so that changes to any table can be listened to.
        if (!Objects.equals(tableId, newTableId)) {
            newTableId = null;
        }

        setSettings(getTextSettings()
                .copy()
                .tableId(newTableId)
                .build());
        update(currentTablePresenter);
    }

    @Override
    public void changeSettings() {
        super.changeSettings();
        update(currentTablePresenter);
    }

    @Override
    public ComponentType getComponentType() {
        return TYPE;
    }

    @Override
    public void beginStepping() {
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


    // --------------------------------------------------------------------------------


    public interface TextView extends View, HasUiHandlers<TextUiHandlers>, TaskMonitorFactory {

        void setContent(View view);

        void setClassification(String classification);

        void setSteppingVisible(boolean visible);
    }
}
