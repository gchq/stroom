package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.data.shared.DataType;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.ViewDataResource;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.client.event.BeginPipelineSteppingEvent;
import stroom.security.client.api.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.svg.client.SvgPreset;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.util.shared.DataRange;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.HasCharacterData;
import stroom.util.shared.Location;
import stroom.util.shared.RowCount;
import stroom.util.shared.TextRange;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class SourcePresenter extends MyPresenterWidget<SourceView> implements TextUiHandlers {

    private static final ViewDataResource VIEW_DATA_RESOURCE = GWT.create(ViewDataResource.class);
    private static final int HIGHLIGHT_CONTEXT_CHARS_BEFORE = 1_000;
    private static final int HIGHLIGHT_CONTEXT_LINES_BEFORE = 3;

    private final TextPresenter textPresenter;
    private final CharacterNavigatorPresenter characterNavigatorPresenter;
//    private final Provider<SourceLocationPresenter> sourceLocationPresenterProvider;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private final ClientSecurityContext clientSecurityContext;
    private final DataNavigatorData dataNavigatorData;

    private SourceLocation requestedSourceLocation = null;
    private SourceLocation receivedSourceLocation = null;
    private FetchDataResult lastResult = null;
    // This is the highlight range for the editor, not the source data. For single line
    // data they will differ if the editor is not displaying from offset zero.
    private TextRange currentHighlight = null;
    private int highlightDelta = 0;
    private ClassificationUiHandlers classificationUiHandlers;
    private boolean isSteppingSource = false;


    @Inject
    public SourcePresenter(final EventBus eventBus,
                           final SourceView view,
                           final TextPresenter textPresenter,
                           final CharacterNavigatorPresenter characterNavigatorPresenter,
                           final UiConfigCache uiConfigCache,
                           final RestFactory restFactory,
                           final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.textPresenter = textPresenter;
        this.characterNavigatorPresenter = characterNavigatorPresenter;
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;
        this.clientSecurityContext = clientSecurityContext;
        this.dataNavigatorData = new DataNavigatorData();

        setEditorOptions(textPresenter);

        view.setTextView(textPresenter.getView());
        view.setNavigatorView(characterNavigatorPresenter.getView());

        textPresenter.setUiHandlers(this);

        characterNavigatorPresenter.setDisplay(dataNavigatorData);
    }

    private void setEditorOptions(final TextPresenter textPresenter) {
        textPresenter.setReadOnly(true);

        // Default to wrapped lines
        textPresenter.getLineWrapOption().setOn(true);
        textPresenter.getLineNumbersOption().setOn(true);
        textPresenter.getStylesOption().setOn(true);

        textPresenter.getBasicAutoCompletionOption().setAvailable(false);
        textPresenter.getUseVimBindingsOption().setAvailable(true);
        textPresenter.getFormatAction().setAvailable(false);
    }

    public void setSourceLocation(final SourceLocation sourceLocation, final boolean force) {
        updateStepControlVisibility();

        if (force || !Objects.equals(sourceLocation, requestedSourceLocation)) {
            // Keep a record of what data was asked for, which may differ from what we get back
            requestedSourceLocation = sourceLocation;

            doWithConfig(sourceConfig -> {
                fetchSource(sourceLocation, sourceConfig);
            });
        }
    }

    private void updateStepControlVisibility() {
        final boolean hasStepPermission = clientSecurityContext.hasAppPermission(
                PermissionNames.STEPPING_PERMISSION);

        if (hasStepPermission && !isSteppingSource) {
            textPresenter.setControlsVisible(true);
        } else {
            textPresenter.setControlsVisible(false);
        }
    }

    public void setSourceLocationUsingHighlight(final SourceLocation sourceLocation) {
        if (sourceLocation.getHighlight() == null || receivedSourceLocation == null) {
            // no highlight or no previous data so just get the requested data.
            setSourceLocation(sourceLocation);
        } else {
            updateStepControlVisibility();
            final TextRange highlight = sourceLocation.getHighlight();
            if (isCurrentSourceSuitable(sourceLocation)) {
                // The requested highlight is inside the currently held data so just update
                // the highlight in the editor
                GWT.log("Using existing source");

                // Update the highlight in case refresh is called
                requestedSourceLocation = receivedSourceLocation.clone()
                        .withHighlight(highlight)
                        .build();

                currentHighlight = highlight;
                updateEditorHighlights();
            } else {
                // Highlight is outside the currently held data so we need to fetch data
                // that contains the highlight.
                final Location newSourceStart;
                final Location highlightStart = highlight.getFrom();

                // Define the new range as starting at the beginning of the highlight
//                newSourceStart = highlightStart;

                if (receivedSourceLocation.getDataRange().getLineCount().filter(i -> i == 1).isPresent()
                    && highlight.isOnOneLine()
                    && highlightStart.getColNo() > HIGHLIGHT_CONTEXT_CHARS_BEFORE) {
                    // single line data and highligt
                    newSourceStart = DefaultLocation.of(
                            1,
                            highlightStart.getColNo() - HIGHLIGHT_CONTEXT_CHARS_BEFORE);
                } else if (highlightStart.getLineNo() > HIGHLIGHT_CONTEXT_LINES_BEFORE) {
                    newSourceStart = DefaultLocation.of(
                            highlightStart.getLineNo() - HIGHLIGHT_CONTEXT_LINES_BEFORE,
                            1);
                } else {
                    newSourceStart = DefaultLocation.of(1,1);
                }

                GWT.log("Highlight: " + highlight.toString()
                        + " new start: " + newSourceStart.toString());

                final SourceLocation newSourceLocation = sourceLocation.clone()
                        .withDataRange(DataRange.from(newSourceStart))
                        .build();

                setSourceLocation(newSourceLocation, true);
            }
        }
    }

    private boolean isCurrentSourceSuitable(final SourceLocation sourceLocation) {
        final boolean result;
        if (receivedSourceLocation == null) {
            result = false;
        } else {

            result = receivedSourceLocation.isSameSource(sourceLocation)
                    && sourceLocation.getHighlight().isInsideRange(
                        receivedSourceLocation.getDataRange().getLocationFrom(),
                        receivedSourceLocation.getDataRange().getLocationTo());

            GWT.log("Highlight: " + sourceLocation.getHighlight().toString()
                    + " received data: " + receivedSourceLocation.getDataRange().getLocationFrom().toString()
                    + " => " + receivedSourceLocation.getDataRange().getLocationTo().toString()
                    + " result: " + result);
        }
        return result;
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        setSourceLocation(sourceLocation, false);
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
    }

    private void doWithConfig(final Consumer<SourceConfig> action) {
        uiConfigCache.get()
                .onSuccess(uiConfig ->
                        action.accept(uiConfig.getSource()))
                .onFailure(caught -> AlertEvent.fireError(
                        SourcePresenter.this,
                        caught.getMessage(),
                        null));
    }

    private void fetchSource(final SourceLocation sourceLocation,
                             final SourceConfig sourceConfig) {


        final FetchDataRequest request = new FetchDataRequest(sourceLocation.getId(), builder -> builder
                .withPartNo(sourceLocation.getPartNo())
                .withSegmentNumber(sourceLocation.getSegmentNo())
                .withDataRange(sourceLocation.getDataRange())
                .withHighlight(sourceLocation.getHighlight())
                .withChildStreamType(sourceLocation.getChildType()));

        final Rest<AbstractFetchDataResult> rest = restFactory.create();

        rest
                .onSuccess(this::handleResponse)
                .onFailure(caught -> AlertEvent.fireError(
                        SourcePresenter.this,
                        caught.getMessage(),
                        null))
                .call(VIEW_DATA_RESOURCE)
                .fetch(request);
    }

    private void handleResponse(final AbstractFetchDataResult result) {

        if (result instanceof FetchDataResult) {
            lastResult = (FetchDataResult) result;
            receivedSourceLocation = lastResult.getSourceLocation();
            // hold this separately as we may change the highlight without fetching new data
            currentHighlight = receivedSourceLocation.getHighlight();

            setTitle(lastResult);
            classificationUiHandlers.setClassification(result.getClassification());

            updateEditor();

            updateNavigator(result);
        } else {

            // TODO @AT Fire alert, should never get this
        }
    }

    private void updateEditor() {
        textPresenter.setText(lastResult.getData());
        int firstLineNo = receivedSourceLocation.getOptDataRange()
                .flatMap(DataRange::getOptLocationFrom)
                .map(Location::getLineNo)
                .orElse(1);

        textPresenter.setFirstLineNumber(firstLineNo);

        setEditorMode(lastResult);

        updateEditorHighlights();
    }

    private void updateEditorHighlights() {
        if (currentHighlight != null) {
            final BooleanSupplier isSingleLineData = () -> receivedSourceLocation.getOptDataRange()
                    .flatMap(DataRange::getLineCount)
                    .filter(lineCount -> lineCount == 1)
                    .isPresent();

            final BooleanSupplier isNonZeroCharOffset = () -> receivedSourceLocation.getOptDataRange()
                    .flatMap(DataRange::getOptCharOffsetFrom)
                    .filter(charOffset -> charOffset > 0)
                    .isPresent();

            final TextRange editorHighlight;
            if (isSingleLineData.getAsBoolean() && isNonZeroCharOffset.getAsBoolean()) {
                final long startOffset = receivedSourceLocation.getDataRange().getCharOffsetFrom();

                editorHighlight = currentHighlight.withNewStartPosition(DefaultLocation.of(
                        1,
                        (int) (currentHighlight.getTo().getColNo() - startOffset)));
            } else {
                editorHighlight = currentHighlight;
            }
            textPresenter.setHighlights(Collections.singletonList(editorHighlight));
        } else {
            textPresenter.setHighlights(null);
        }
    }

    private void updateNavigator(final AbstractFetchDataResult result) {
        if (DataType.SEGMENTED.equals(lastResult.getDataType())) {
            dataNavigatorData.segmentsCount = result.getTotalItemCount();
        } else {
            dataNavigatorData.partsCount = result.getTotalItemCount();
        }

        DataRange dataRange = Optional.ofNullable(result.getSourceLocation())
                .map(SourceLocation::getDataRange)
                .orElse(null);

        characterNavigatorPresenter.refreshNavigator();
    }

    private void setTitle(final FetchDataResult fetchDataResult) {
        final String streamType = fetchDataResult.getStreamTypeName();
        final SourceLocation sourceLocation = fetchDataResult.getSourceLocation();
        getView().setTitle(
                fetchDataResult.getFeedName(),
                sourceLocation.getId(),
                sourceLocation.getPartNo(),
                sourceLocation.getSegmentNo(),
                streamType);
    }

    private void setEditorMode(final FetchDataResult fetchDataResult) {
        final AceEditorMode mode;

        if (StreamTypeNames.META.equals(fetchDataResult.getStreamTypeName())) {
            mode = AceEditorMode.PROPERTIES;
        } else {// We have no way of knowing what type the data is (could be csv, json, xml) so assume XML
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

    private void beginStepping(ClickEvent clickEvent) {
        beginStepping();
    }

    @Override
    public void clear() {

        // TODO @AT Not sure if I need to implement this
    }

    @Override
    public void beginStepping() {
        BeginPipelineSteppingEvent.fire(
                this,
                receivedSourceLocation.getId(),
                null,
                receivedSourceLocation.getOptChildType().orElse(null),
                new StepLocation(
                        receivedSourceLocation.getId(),
                        receivedSourceLocation.getPartNo(),
                        receivedSourceLocation.getSegmentNo()),
                null);
    }

    public void setClassificationUiHandlers(final ClassificationUiHandlers classificationUiHandlers) {
        this.classificationUiHandlers = classificationUiHandlers;
    }


    // ===================================================================


    private class DataNavigatorData implements HasCharacterData {
        private RowCount<Long> partsCount = RowCount.of(0L, false);
        private RowCount<Long> segmentsCount = RowCount.of(0L, false);

        @Override
        public boolean areNavigationControlsVisible() {
            return !isSteppingSource;
        }

        @Override
        public DataRange getDataRange() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .orElse(DataRange.from(0));
        }

        @Override
        public void setDataRange(final DataRange dataRange) {
            doWithConfig(sourceConfig -> {
                final SourceLocation newSourceLocation = requestedSourceLocation.clone()
                        .withDataRange(dataRange)
                        .build();

                setSourceLocation(newSourceLocation);
            });
        }

        @Override
        public Optional<Long> getTotalLines() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .filter(dataRange -> dataRange.getOptLocationFrom().isPresent()
                            && dataRange.getOptLocationTo().isPresent())
                    .map(dataRange -> dataRange.getLocationTo().getLineNo()
                            - dataRange.getLocationFrom().getLineNo()
                            + 1L); // line nos are inclusive, so add 1
        }

        @Override
        public Optional<Long> getCharFrom() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .flatMap(DataRange::getOptCharOffsetFrom);
        }

        @Override
        public Optional<Long> getCharTo() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .flatMap(DataRange::getOptCharOffsetTo);
        }

        @Override
        public Optional<Integer> getLineFrom() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .flatMap(DataRange::getOptLocationFrom)
                    .map(Location::getLineNo);
        }

        @Override
        public Optional<Integer> getLineTo() {
            return Optional.ofNullable(lastResult)
                    .map(AbstractFetchDataResult::getSourceLocation)
                    .flatMap(SourceLocation::getOptDataRange)
                    .flatMap(DataRange::getOptLocationTo)
                    .map(Location::getLineNo);
        }

        @Override
        public Optional<Long> getTotalChars() {
            return Optional.ofNullable(lastResult)
                    .flatMap(result -> Optional.ofNullable(result.getTotalCharacterCount()))
                    .filter(RowCount::isExact)
                    .map(RowCount::getCount);
        }

        @Override
        public void showHeadCharacters() {
            doWithConfig(sourceConfig -> {
                final SourceLocation newSourceLocation = requestedSourceLocation.clone()
                        .withDataRange(DataRange.from(
                                0,
                                sourceConfig.getMaxCharactersPerFetch()))
                        .build();

                setSourceLocation(newSourceLocation);
            });
        }

        @Override
        public void advanceCharactersForward() {
            doWithConfig(sourceConfig -> {
                final SourceLocation newSourceLocation = requestedSourceLocation.clone()
                        .withDataRange(DataRange.from(
                                receivedSourceLocation.getDataRange().getCharOffsetTo() + 1,
                                sourceConfig.getMaxCharactersPerFetch()))
                        .build();

                setSourceLocation(newSourceLocation);
            });
        }

        @Override
        public void advanceCharactersBackwards() {
            doWithConfig(sourceConfig -> {
                final long maxChars = sourceConfig.getMaxCharactersPerFetch();
                final SourceLocation newSourceLocation = requestedSourceLocation.clone()
                        .withDataRange(DataRange.from(
                                receivedSourceLocation.getDataRange().getCharOffsetFrom() - maxChars,
                                maxChars))
                        .build();

                setSourceLocation(newSourceLocation);
            });
        }

        @Override
        public void refresh() {
            setSourceLocation(requestedSourceLocation, true);
        }
    }


    // ===================================================================


    public interface SourceView extends View {

//        void setSourceLocation(final SourceLocation sourceLocation);

        void setTextView(final TextView textView);

        void setNavigatorView(final CharacterNavigatorView characterNavigatorView);

        ButtonView addButton(final SvgPreset preset);

        void setTitle(final String feedName,
                      final long id,
                      final long partNo,
                      final long segmentNo,
                      final String type);

//        void setNavigatorClickHandler(final Runnable clickHandler);

//        void setNavigatorData(final HasCharacterData dataNavigatorData);

//        void refreshNavigator();
    }
}
