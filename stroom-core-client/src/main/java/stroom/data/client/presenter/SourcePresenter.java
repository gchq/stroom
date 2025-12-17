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

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.shared.DataResource;
import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.RestFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.AppPermission;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.shared.Count;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.HasCharacterData;
import stroom.util.shared.HasCharacterData.NavigationMode;
import stroom.util.shared.Location;
import stroom.util.shared.TextRange;
import stroom.util.shared.string.HexDump;
import stroom.widget.progress.client.presenter.Progress;
import stroom.widget.progress.client.presenter.ProgressPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SourcePresenter extends MyPresenterWidget<SourceView> implements
        TextUiHandlers,
        ClassificationUiHandlers,
        Focus {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);
    private static final int HIGHLIGHT_CONTEXT_CHARS_BEFORE = 1_500;
    private static final int HIGHLIGHT_CONTEXT_LINES_BEFORE = 4;

    private final ClassificationWrapperView classificationWrapperView;
    private final ProgressPresenter progressPresenter;
    private final TextPresenter textPresenter;
    private final CharacterNavigatorPresenter characterNavigatorPresenter;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private final ClientSecurityContext clientSecurityContext;
    private HasCharacterData dataNavigatorData;

    private SourceLocation requestedSourceLocation = null;
    private SourceLocation receivedSourceLocation = null;
    private FetchDataResult lastResult = null;
    // Need to hold the highlight for text and hex as the same highlighted section in byte terms
    // will have different line/col start/end points depending on whether it is rendered as text
    // or a hex dump.
    private DataRange currentTextHighlight = null;
    private boolean isSteppingSource = false;
    private Count<Long> exactCharCount = null;

    private ClassificationUiHandlers classificationUiHandlers;

    @Inject
    public SourcePresenter(final EventBus eventBus,
                           final SourceView view,
                           final ClassificationWrapperView classificationWrapperView,
                           final ProgressPresenter progressPresenter,
                           final TextPresenter textPresenter,
                           final CharacterNavigatorPresenter characterNavigatorPresenter,
                           final UiConfigCache uiConfigCache,
                           final RestFactory restFactory,
                           final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.classificationWrapperView = classificationWrapperView;
        this.progressPresenter = progressPresenter;
        this.textPresenter = textPresenter;
        this.characterNavigatorPresenter = characterNavigatorPresenter;
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;
        this.clientSecurityContext = clientSecurityContext;

        setEditorOptions();

        classificationWrapperView.setContent(textPresenter.getView());
        view.setTextView(classificationWrapperView);
        view.setNavigatorView(characterNavigatorPresenter.getView());

        setupProgressBar(view, progressPresenter);

        textPresenter.setUiHandlers(this);

        setDataNavigator(textPresenter.getViewAsHexOption().isOnAndAvailable());

        textPresenter.getViewAsHexOption().setChangeHandler(this::onHexModeChange);
    }

    private void onHexModeChange(final boolean isHexModeOn) {
        setDataNavigator(isHexModeOn);
        SourceLocation sourceLocation = lastResult != null && lastResult.getSourceLocation() != null
                ? lastResult.getSourceLocation()
                : requestedSourceLocation;

        // We always need to request with the text mode highlight as the hex view produces multiple highlights
        // to cover the various parts of the hex dump.
        sourceLocation = sourceLocation.copy()
                .withHighlight(currentTextHighlight)
                .build();

        // Can either take the user to the same location they are currently at, regardless of highlight
        setSourceLocation(sourceLocation);
        // or always take them to the approx location of the highlight
//        setSourceLocationUsingHighlight(sourceLocation);
    }

    /**
     * Set how data navigation is managed.
     */
    private void setDataNavigator(final boolean isViewAsHex) {
        // Only want to change the navigator if we don't already have the right one
        if (isViewAsHex
                && (dataNavigatorData == null || !NavigationMode.BYTES.equals(dataNavigatorData.getNavigationMode()))) {
            dataNavigatorData = new HexDumpNavigatorData();
            characterNavigatorPresenter.setDisplay(dataNavigatorData);
        } else if (!isViewAsHex
                && (dataNavigatorData == null || !NavigationMode.CHARS.equals(dataNavigatorData.getNavigationMode()))) {
            dataNavigatorData = new DataNavigatorData();
            characterNavigatorPresenter.setDisplay(dataNavigatorData);
        }
    }

    @Override
    public void focus() {
        textPresenter.focus();
    }

    private void setupProgressBar(final SourceView view,
                                  final ProgressPresenter progressPresenter) {
        view.setProgressView(progressPresenter.getView());
        progressPresenter.setVisible(false);
    }

    private void setEditorOptions() {
        textPresenter.setReadOnly(true);

        // Default to wrapped lines
        textPresenter.getLineWrapOption().setOn();
        textPresenter.getLineNumbersOption().setOn();
        textPresenter.getStylesOption().setOn();

        textPresenter.getUseVimBindingsOption().setAvailable();

        textPresenter.getBasicAutoCompletionOption().setUnavailable();
        textPresenter.getFormatAction().setUnavailable();
        // Allowing hex view while stepping is fiddly so not doing it for now
        textPresenter.getViewAsHexOption().setAvailable(!isSteppingSource);
    }

    private void updateStepControlVisibility() {
        final boolean hasStepPermission = clientSecurityContext.hasAppPermission(
                AppPermission.STEPPING_PERMISSION);

        textPresenter.setControlsVisible(hasStepPermission && !isSteppingSource);
    }

    /**
     * Sets the source location/range according to the passed {@link SourceLocation}
     * If there is a highlight and it is outside the visible range then so be it.
     * Only re-fetches the data if the location/range has changed
     */
    public void setSourceLocation(final SourceLocation sourceLocation) {
        setSourceLocation(sourceLocation, false);
    }

    /**
     * Sets the source location/range according to the passed {@link SourceLocation}
     * If there is a highlight and it is outside the visible range then so be it.
     *
     * @param force If true forces a re-fetch of the data even if the location/range is
     *              the same as last time.
     */
    public void setSourceLocation(final SourceLocation sourceLocation, final boolean force) {
        updateStepControlVisibility();

        if (force || !Objects.equals(sourceLocation, requestedSourceLocation)) {
            // Keep a record of what data was asked for, which may differ from what we get back
            requestedSourceLocation = sourceLocation;

            doWithConfig(sourceConfig -> {
                fetchSource(sourceLocation);
            });
        }
    }

    /**
     * Will attempt to set the source range using the passed highlight, i.e. if the highlight
     * is towards the end of the data then it will set the range to enclose the highlight.
     */
    public void setSourceLocationUsingHighlight(final SourceLocation sourceLocation) {
        // Don't need to set currentHexHighlight as hex mode will always be off at this point
        currentTextHighlight = sourceLocation.getFirstHighlight();

        final DataRange highlight = sourceLocation.getFirstHighlight();
        if (highlight == null) {
            // no highlight so just get the requested data.
            setSourceLocation(sourceLocation);
        } else {
            updateStepControlVisibility();
            if (receivedSourceLocation != null && isCurrentSourceSuitable(sourceLocation)) {
                // The requested highlight is inside the currently held data so just update
                // the highlight in the editor
//                GWT.log("Using existing source");

                // Update the highlight in case refresh is called
                requestedSourceLocation = receivedSourceLocation.copy()
                        .withHighlight(highlight)
                        .build();

                updateEditorHighlights(Collections.singletonList(currentTextHighlight));
            } else {
                // Highlight is outside the currently held data so we need to fetch data
                // that contains the highlight.
                doWithConfig(sourceConfig -> {
                    final DataRange newDataRange;
                    if (textPresenter.getViewAsHexOption().isOnAndAvailable()) {
                        newDataRange = buildNewSourceLocationFromHighlightForHexDump(
                                sourceLocation, highlight, sourceConfig);
                    } else {
                        final Location newSourceStart = buildNewSourceLocationFromHighlight(
                                sourceLocation, highlight, sourceConfig);
                        newDataRange = DataRange.fromLocation(newSourceStart);
                    }
                    final SourceLocation newSourceLocation = sourceLocation.copy()
                            .withDataRange(newDataRange)
                            .build();

                    // Now fetch the required range
                    setSourceLocation(newSourceLocation, true);
                });
            }
        }
    }

    private DataRange buildNewSourceLocationFromHighlightForHexDump(final SourceLocation sourceLocation,
                                                                    final DataRange highlight,
                                                                    final SourceConfig sourceConfig) {
        // Don't have to worry about highlights during stepping as hex is not available
        final long newByteOffsetFrom = highlight.getOptByteOffsetFrom().map(
                        byteOffsetFrom -> Math.max(
                                0,
                                byteOffsetFrom -
                                        (HIGHLIGHT_CONTEXT_LINES_BEFORE * HexDump.MAX_BYTES_PER_LINE)))
                .orElse(0L);

//        GWT.log("old hl byteOffset: " + highlight.getOptByteOffsetFrom().get()
//                + " newByteOffset: " + newByteOffsetFrom);

        return DataRange.fromByteOffset(newByteOffsetFrom);
    }

    private Location buildNewSourceLocationFromHighlight(final SourceLocation sourceLocation,
                                                         final DataRange highlight,
                                                         final SourceConfig sourceConfig) {
        final Location newSourceStart;
        final Location highlightStart = highlight.getLocationFrom();

        // If we are stepping backwards then this highlight will be before the last one we
        // requested. If we don't have previous data then treat it like stepping forward.
        final boolean isHighlightMovingBackwards = isHighlightMovingBackwards(
                sourceLocation,
                requestedSourceLocation);

        final Optional<Integer> optCurrLineCount = Optional.ofNullable(receivedSourceLocation)
                .flatMap(SourceLocation::getOptDataRange)
                .flatMap(DataRange::getLineCount);

        if (optCurrLineCount
                .filter(i -> i == 1)
                .isPresent()
                && highlight.getAsTextRange().filter(TextRange::isOnOneLine).isPresent()
                && highlightStart.getColNo() > HIGHLIGHT_CONTEXT_CHARS_BEFORE) {

            // single line data and highlight
            final int newColNo;
            if (isHighlightMovingBackwards
                    && receivedSourceLocation.getDataRange() != null
                    && receivedSourceLocation.getDataRange().getOptLength().isPresent()) {
                // try and show just under a fetch's worth of data before
                final int highlightLen = highlight.getLocationTo().getColNo()
                        - highlight.getLocationFrom().getColNo()
                        + 1;
                newColNo = (int) (highlightStart.getColNo()
                        - sourceConfig.getMaxCharactersPerFetch()
                        + highlightLen
//                        - receivedSourceLocation.getDataRange().getLength()
                        + HIGHLIGHT_CONTEXT_CHARS_BEFORE);
            } else {
                // we need to change the visible range
                // to be some chars before the highlight to provide the user some context
                newColNo = highlightStart.getColNo() - HIGHLIGHT_CONTEXT_CHARS_BEFORE;
            }
            newSourceStart = DefaultLocation.of(1, Math.max(1, newColNo));
        } else if (highlightStart.getLineNo() > HIGHLIGHT_CONTEXT_LINES_BEFORE) {
            final int newLineNo;
            if (isHighlightMovingBackwards && optCurrLineCount.isPresent()) {
                // try and show just under a fetch's worth of data before
                newLineNo = highlightStart.getLineNo()
                        - optCurrLineCount.get()
                        + HIGHLIGHT_CONTEXT_LINES_BEFORE;
            } else {
                // Adjust the visible data range to be a few lines before the highlight
                // so the user has some context
                newLineNo = highlightStart.getLineNo() - HIGHLIGHT_CONTEXT_LINES_BEFORE;
            }
            newSourceStart = DefaultLocation.of(Math.max(1, newLineNo), 1);
        } else {
            // Shouldn't really come in here but just display from the start just in case
            newSourceStart = DefaultLocation.of(1, 1);
        }

//        GWT.log("Highlight: " + highlight.toString()
//                + " new start: " + newSourceStart.toString());
        return newSourceStart;
    }

    private boolean isHighlightMovingBackwards(final SourceLocation newSourceLocation,
                                               final SourceLocation oldSourceLocation) {
        if (newSourceLocation != null
                && newSourceLocation.getFirstHighlight() != null
                && oldSourceLocation != null
                && oldSourceLocation.getFirstHighlight() != null) {
            return newSourceLocation.getFirstHighlight().isBefore(oldSourceLocation.getFirstHighlight());
        } else {
            return false;
        }
    }

    private boolean hasDisplayModeDifferentToLastRequest() {
        // See if the hex mode differs from what we got back last time
        return lastResult != null
                && (
                (FetchDataRequest.DisplayMode.HEX.equals(lastResult.getDisplayMode())
                        && !textPresenter.getViewAsHexOption().isOnAndAvailable()
                ) || (FetchDataRequest.DisplayMode.TEXT.equals(lastResult.getDisplayMode())
                        && textPresenter.getViewAsHexOption().isOnAndAvailable()));
    }

    private boolean isCurrentSourceSuitable(final SourceLocation sourceLocation) {
        final boolean result;
        if (receivedSourceLocation == null
                || receivedSourceLocation.getDataRange() == null
                || hasDisplayModeDifferentToLastRequest()) {
            result = false;
        } else {
            result = receivedSourceLocation.isSameSource(sourceLocation)
                    && sourceLocation.getFirstHighlight().isInsideRange(
                    receivedSourceLocation.getDataRange().getLocationFrom(),
                    receivedSourceLocation.getDataRange().getLocationTo());

//            GWT.log("Highlight: " + sourceLocation.getHighlight().toString()
//                    + " isSameSource: " + receivedSourceLocation.isSameSource(sourceLocation)
//                    + " isInsideRange: " + sourceLocation.getHighlight().isInsideRange(
//                        receivedSourceLocation.getDataRange().getLocationFrom(),
//                        receivedSourceLocation.getDataRange().getLocationTo())
//                    + " received data: " + receivedSourceLocation.getDataRange().getLocationFrom().toString()
//                    + " => " + receivedSourceLocation.getDataRange().getLocationTo().toString()
//                    + " result: " + result);
        }
        return result;
    }

    public void setNavigatorControlsVisible(final boolean isVisible) {
        if (isVisible) {
            getView().setNavigatorView(characterNavigatorPresenter.getView());
        } else {
            getView().setNavigatorView(null);
        }
    }

    public void setSteppingSource(final boolean isSteppingSource) {
        this.isSteppingSource = isSteppingSource;
        // Allowing hex view while stepping is fiddly so not doing it for now. The hex view has totally
        // different line/col for the same source byte, so position and highlights need to be translated
        // into hex dump terms.
        textPresenter.getViewAsHexOption().setAvailable(!isSteppingSource);
    }

    private void doWithConfig(final Consumer<SourceConfig> action) {
        uiConfigCache.get(uiConfig -> {
            if (uiConfig != null) {
                action.accept(uiConfig.getSource());
            }
        }, getView());
    }

    private void fetchSource(final SourceLocation sourceLocation) {

        final FetchDataRequest request = new FetchDataRequest(sourceLocation);

        if (textPresenter.getViewAsHexOption().isOnAndAvailable()) {
            request.setDisplayMode(FetchDataRequest.DisplayMode.HEX);
        } else {
            request.setDisplayMode(FetchDataRequest.DisplayMode.TEXT);
        }

        restFactory
                .create(DATA_RESOURCE)
                .method(res -> res.fetch(request))
                .onSuccess(this::handleResponse)
                .onFailure(caught -> AlertEvent.fireError(
                        SourcePresenter.this,
                        caught.getMessage(),
                        null))
                .taskMonitorFactory(getView())
                .exec();
    }

    private void handleResponse(final AbstractFetchDataResult result) {

        if (result instanceof FetchDataResult) {
            final FetchDataResult fetchDataResult = (FetchDataResult) result;
            receivedSourceLocation = result.getSourceLocation();

            if (receivedSourceLocation != null
                    && lastResult != null
                    && receivedSourceLocation.isSameSource(lastResult.getSourceLocation())) {
                // If we encounter an exact char count for this source then hold onto it
                // so we can still show it if we page backwards
                if (fetchDataResult.getTotalCharacterCount().isExact()
                        || exactCharCount == null) {
                    exactCharCount = fetchDataResult.getTotalCharacterCount();
                }
            } else {
                exactCharCount = null;
                currentTextHighlight = null;
            }

            if (!textPresenter.getViewAsHexOption().isOnAndAvailable()) {
                recordDecoratedHighlight(receivedSourceLocation);
            }

            lastResult = fetchDataResult;
            setTitle(lastResult);
            setClassification(result.getClassification());

            updateEditor();
            updateNavigator(result);
            refreshProgressBar(true);
        } else {
            AlertEvent.fireError(
                    SourcePresenter.this,
                    "Unexpected type " + result.getClass().getName(),
                    null);
        }
    }

    private void recordDecoratedHighlight(final SourceLocation sourceLocation) {
        final DataRange receivedHighlight = sourceLocation != null
                ? sourceLocation.getFirstHighlight()
                : null;

        // When a request is first sent for TEXT data with a highlight, the highlight will
        // get decorated with the byte offsets of that highlight. Subsequent requests may have
        // the same highlight but the server may be unable to decorate it if it doesn't encounter the highlight.
        // Therefore, we mustn't overwrite the byte offsets we hold locally unless the line/col locations
        // have changed.
        if (currentTextHighlight == null
                || !currentTextHighlight.getOptByteOffsetFrom().isPresent()
                || !currentTextHighlight.getOptByteOffsetTo().isPresent()
                || !areSameLocations(currentTextHighlight, receivedHighlight)) {
            currentTextHighlight = receivedHighlight;
        }
    }

    /**
     * Compare the {@link DataRange}s using on the from/to {@link Location};
     */
    private boolean areSameLocations(final DataRange highlight1, final DataRange highlight2) {
        if (highlight1 == null && highlight2 == null) {
            return true;
        } else if (highlight1 != null && highlight2 == null) {
            return false;
        } else if (highlight1 == null) {
            return false;
        } else {
            return Objects.equals(highlight1.getLocationFrom(), highlight2.getLocationFrom())
                    && Objects.equals(highlight1.getLocationTo(), highlight2.getLocationTo());
        }
    }

    private void refreshProgressBar(final boolean isVisible) {
        Progress progress = null;
        if (dataNavigatorData.isSegmented()
                && dataNavigatorData.getCharOffsetFrom().isPresent()
                && dataNavigatorData.getCharOffsetTo().isPresent()) {

            if (dataNavigatorData.getTotalChars().isExact()) {
                progress = Progress.boundedRange(
                        dataNavigatorData.getTotalChars().getCount() - 1, // count to zero based bound
                        dataNavigatorData.getCharOffsetFrom().get(),
                        dataNavigatorData.getCharOffsetTo().get());
            } else {
                progress = Progress.unboundedRange(
                        dataNavigatorData.getCharOffsetFrom().get(),
                        dataNavigatorData.getCharOffsetTo().get());
            }
        } else if (dataNavigatorData.getByteOffsetFrom().isPresent()
                && dataNavigatorData.getByteOffsetTo().isPresent()) {

            if (dataNavigatorData.getTotalBytes().isPresent()) {
                progress = Progress.boundedRange(
                        dataNavigatorData.getTotalBytes().get() - 1, // count to zero based bound
                        dataNavigatorData.getByteOffsetFrom().get(),
                        dataNavigatorData.getByteOffsetTo().get());
            } else {
                progress = Progress.unboundedRange(
                        dataNavigatorData.getByteOffsetFrom().get(),
                        dataNavigatorData.getByteOffsetTo().get());
            }
        }

        if (progress != null) {
            progressPresenter.setVisible(true);
            progressPresenter.setProgress(progress);

            if (progress.isComplete()) {
                // Don't want users clicking if we are showing everything
                progressPresenter.setClickHandler(null);
            } else {
                progressPresenter.setClickHandler(byteOffsetDbl -> {
                    final long byteOffset = (long) Math.floor(byteOffsetDbl);
                    // update the location with the new range
                    doWithConfig(sourceConfig -> {
                        final long maxChars = sourceConfig.getMaxCharactersPerFetch();
                        dataNavigatorData.setDataRange(DataRange.fromByteOffset(byteOffset, maxChars));
                    });
                });
            }
        } else {
            progressPresenter.setVisible(false);
        }
    }

    private void updateEditor() {
        if (lastResult.hasErrors()) {
            showErrors(lastResult);
        } else {
            textPresenter.setText(lastResult.getData());
            final int firstLineNo = receivedSourceLocation.getOptDataRange()
                    .flatMap(DataRange::getOptLocationFrom)
                    .map(Location::getLineNo)
                    .orElse(1);

            textPresenter.setFirstLineNumber(firstLineNo);
            setEditorMode(lastResult);
            updateEditorHighlights(lastResult.getSourceLocation().getHighlights());
        }
    }

    private void showErrors(final FetchDataResult result) {
        final String childStreamText = lastResult.getSourceLocation().getOptChildType()
                .map(childType -> " (" + childType + ")")
                .orElse("");
        final String title = "Unable to display source ["
                + lastResult.getSourceLocation().getIdentifierString()
                + "]"
                + childStreamText;

        final String errorText = String.join("\n", lastResult.getErrors());
        textPresenter.setErrorText(title, errorText);
    }

    private void updateEditorHighlights(final List<DataRange> highlights) {
        if (highlights != null) {
            final BooleanSupplier isSingleLineData = () -> receivedSourceLocation.getOptDataRange()
                    .flatMap(DataRange::getLineCount)
                    .filter(lineCount -> lineCount == 1)
                    .isPresent();

            final BooleanSupplier isNonZeroCharOffset = () -> receivedSourceLocation.getOptDataRange()
                    .flatMap(DataRange::getOptCharOffsetFrom)
                    .filter(charOffset -> charOffset > 0)
                    .isPresent();

            // This is the highlight range for the editor, not the source data. For single line
            // data they will differ if the editor is not displaying from offset one.
            // It is only an issue for single line data because for multi-line we adjust the editor's
            // starting line no to suit the data.
            final List<TextRange> textRanges = new ArrayList<>();
            for (final DataRange highlight : highlights) {
                TextRange editorHighlight = highlight.getAsTextRange().orElse(null);

                if (isSingleLineData.getAsBoolean() && isNonZeroCharOffset.getAsBoolean()) {
                    final long startOffset = receivedSourceLocation.getDataRange().getCharOffsetFrom();

                    if (startOffset != 1) {
                        final int highlightDelta = (int) (highlight.getLocationFrom().getColNo() - startOffset);
                        editorHighlight = highlight.getAsTextRange()
                                .map(textRange ->
                                        textRange.withNewStartPosition(DefaultLocation.of(1, highlightDelta)))
                                .orElse(null);
                    }
                }
                textRanges.add(editorHighlight);
            }
            textPresenter.setHighlights(textRanges);
        } else {
            textPresenter.setHighlights(null);
        }
    }

    private void updateNavigator(final AbstractFetchDataResult result) {
        characterNavigatorPresenter.refreshNavigator();
    }

    private void setTitle(final FetchDataResult fetchDataResult) {
        final String streamType = fetchDataResult.getStreamTypeName();
        final SourceLocation sourceLocation = fetchDataResult.getSourceLocation();
        if (DataType.NON_SEGMENTED.equals(fetchDataResult.getDataType())) {
            getView().setNonSegmentedTitle(
                    fetchDataResult.getFeedName(),
                    sourceLocation.getMetaId(),
                    sourceLocation.getPartIndex() + 1,
                    streamType);
        } else {
            getView().setSegmentedTitle(
                    fetchDataResult.getFeedName(),
                    sourceLocation.getMetaId(),
                    sourceLocation.getRecordIndex() + 1,
                    streamType);
        }
    }

    private void setEditorMode(final FetchDataResult fetchDataResult) {
        final AceEditorMode mode;

        if (FetchDataRequest.DisplayMode.HEX.equals(fetchDataResult.getDisplayMode())) {
            mode = AceEditorMode.STROOM_HEX_DUMP;
        } else if (fetchDataResult.getSourceLocation() != null
                && StreamTypeNames.META.equals(fetchDataResult.getSourceLocation().getChildType())) {
            mode = AceEditorMode.PROPERTIES;
        } else { // We have no way of knowing what type the data is (could be csv, json, xml) so assume XML
            mode = AceEditorMode.XML;
        }
        textPresenter.setMode(mode);
    }

    @Override
    protected void onBind() {

    }

    private boolean isCurrentDataSegmented() {
        return lastResult != null
                && (DataType.SEGMENTED.equals(lastResult.getDataType())
                || DataType.MARKER.equals(lastResult.getDataType()));
    }

    private boolean isCurrentDataMultiPart() {
        // For now assume segmented and multi-part are mutually exclusive
        return lastResult != null
                && DataType.NON_SEGMENTED.equals(lastResult.getDataType());
    }

    private DataType getCurDataType() {
        return lastResult != null
                ? lastResult.getDataType()
                : null;
    }

    private void beginStepping(final ClickEvent clickEvent) {
        beginStepping();
    }

    @Override
    public void setClassification(final String classification) {
        classificationWrapperView.setClassification(classification);
        if (this.classificationUiHandlers != null) {
            this.classificationUiHandlers.setClassification(classification);
        }
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        this.classificationUiHandlers = classificationUiHandlers;
    }

    @Override
    public void clear() {

        // TODO @AT Not sure if I need to implement this
    }

    @Override
    public void beginStepping() {
        BeginPipelineSteppingEvent.fire(
                this,
                null,
                receivedSourceLocation.getOptChildType().orElse(null),
                StepType.REFRESH,
                new StepLocation(
                        receivedSourceLocation.getMetaId(),
                        receivedSourceLocation.getPartIndex(),
                        receivedSourceLocation.getRecordIndex()),
                null);
    }


    // ===================================================================


    /**
     * Used for navigating standard char based data using char offsets.
     */
    private class DataNavigatorData implements HasCharacterData {

        @Override
        public boolean areNavigationControlsVisible() {
            return !isSteppingSource;
        }

        @Override
        public NavigationMode getNavigationMode() {
            return NavigationMode.CHARS;
        }

        @Override
        public DataRange getDataRange() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .orElse(DataRange.fromCharOffset(0));
        }

        @Override
        public void setDataRange(final DataRange dataRange) {
            final SourceLocation newSourceLocation = requestedSourceLocation.copy()
                    .withDataRange(dataRange)
                    .build();

            setSourceLocation(newSourceLocation);
        }

        @Override
        public boolean isSegmented() {
            return DataType.SEGMENTED.equals(lastResult.getDataType());
        }

        @Override
        public Count<Long> getTotalChars() {
            if (lastResult != null && lastResult.getTotalCharacterCount() != null) {
                return lastResult.getTotalCharacterCount();
            } else {
                return Count.approximately(0L);
            }
        }

        @Override
        public Optional<Long> getTotalBytes() {
            return Optional.ofNullable(lastResult)
                    .flatMap(FetchDataResult::getOptTotalBytes);
        }

        @Override
        public void showHeadCharacters() {
            doWithConfig(sourceConfig ->
                    setDataRange(DataRange.fromCharOffset(
                            0,
                            sourceConfig.getMaxCharactersPerFetch())));
        }

        @Override
        public void advanceCharactersForward() {
            doWithConfig(sourceConfig ->
                    setDataRange(DataRange.fromCharOffset(
                            receivedSourceLocation.getDataRange().getCharOffsetTo() + 1,
                            sourceConfig.getMaxCharactersPerFetch())));
        }

        @Override
        public void advanceCharactersBackwards() {
            doWithConfig(sourceConfig -> {
                final long maxChars = sourceConfig.getMaxCharactersPerFetch();
                final long newCharOffset = Math.max(0,
                        receivedSourceLocation.getDataRange().getCharOffsetFrom() - maxChars - 1);
                setDataRange(DataRange.fromCharOffset(newCharOffset, maxChars));
            });
        }

        @Override
        public void refresh() {
            setSourceLocation(requestedSourceLocation, true);
        }
    }


    // ===================================================================


    /**
     * Used for navigating HexDump data which has to navigate using byte offsets,
     * not char offsets.
     */
    private class HexDumpNavigatorData extends DataNavigatorData {

        @Override
        public NavigationMode getNavigationMode() {
            return NavigationMode.BYTES;
        }

        @Override
        public DataRange getDataRange() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .orElse(DataRange.fromByteOffset(0));
        }

        @Override
        public void setDataRange(final DataRange dataRange) {
            final SourceLocation newSourceLocation = requestedSourceLocation.copy()
                    .withDataRange(dataRange)
                    .build();
            setSourceLocation(newSourceLocation);
        }

        @Override
        public boolean isSegmented() {
            return DataType.SEGMENTED.equals(lastResult.getDataType());
        }

        @Override
        public void showHeadCharacters() {
            setDataRange(DataRange.fromByteOffset(0));
        }

        @Override
        public void advanceCharactersForward() {
            final long newByteOffset = Math.max(0, receivedSourceLocation.getDataRange().getByteOffsetTo() + 1);
            setDataRange(DataRange.fromByteOffset(newByteOffset));
        }

        @Override
        public void advanceCharactersBackwards() {
            doWithConfig(sourceConfig -> {
                final long maxLines = sourceConfig.getMaxHexDumpLines();
                final long maxBytesPerFetch = HexDump.MAX_BYTES_PER_LINE * maxLines;
                final long newByteOffsetFrom = Math.max(
                        0,
                        receivedSourceLocation.getDataRange().getByteOffsetFrom() - maxBytesPerFetch);
                setDataRange(DataRange.fromByteOffset(newByteOffsetFrom));
            });
        }
    }


    // ===================================================================


    public interface SourceView extends View, TaskMonitorFactory {

        void setProgressView(final View view);

        void setTextView(final View view);

        void setNavigatorView(final View view);

        void setNonSegmentedTitle(final String feedName,
                                  final long id,
                                  final long partNo,
                                  final String type);

        void setSegmentedTitle(final String feedName,
                               final long id,
                               final long segmentNo,
                               final String type);
    }
}
