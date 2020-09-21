package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.shared.StreamTypeNames;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.presenter.EditorView;
import stroom.pipeline.shared.AbstractFetchDataResult;
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.ViewDataResource;
import stroom.svg.client.SvgPreset;
import stroom.ui.config.client.UiConfigCache;
import stroom.ui.config.shared.SourceConfig;
import stroom.widget.button.client.ButtonView;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;

public class SourcePresenter extends MyPresenterWidget<SourceView> {

    private static final ViewDataResource VIEW_DATA_RESOURCE = GWT.create(ViewDataResource.class);

    private final EditorPresenter editorPresenter;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private SourceLocation requestedSourceLocation = null;
    private SourceLocation receivedSourceLocation = null;
//    private SourceKey sourceKey;

    @Inject
    public SourcePresenter(final EventBus eventBus,
                           final SourceView view,
                           final EditorPresenter editorPresenter,
                           final UiConfigCache uiConfigCache,
                           final RestFactory restFactory) {
        super(eventBus, view);
        this.editorPresenter = editorPresenter;
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;

        setEditorOptions(editorPresenter);

        getView().setEditorView(editorPresenter.getView());
    }

    private void setEditorOptions(final EditorPresenter editorPresenter) {
        editorPresenter.setReadOnly(true);

        // Default to wrapped lines
        editorPresenter.getLineWrapOption().setOn(true);
        editorPresenter.getLineNumbersOption().setOn(true);
        editorPresenter.getStylesOption().setOn(true);

        editorPresenter.getCodeCompletionOption().setAvailable(false);
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        if (!Objects.equals(sourceLocation, this.requestedSourceLocation)) {

            uiConfigCache.get()
                    .onSuccess(uiConfig -> {
                        fetchSource(sourceLocation, uiConfig.getSource());
                    })
                    .onFailure(caught -> AlertEvent.fireError(
                            SourcePresenter.this,
                            caught.getMessage(),
                            null));

            this.requestedSourceLocation = sourceLocation;
//            getView().setSourceLocation(sourceLocation);
        }
    }

    private void fetchSource(final SourceLocation sourceLocation,
                             final SourceConfig sourceConfig) {


        final FetchDataRequest request = new FetchDataRequest(sourceLocation.getId(), builder -> builder
                .withPartNo(sourceLocation.getPartNo())
                .withSegmentNumber(sourceLocation.getSegmentNo())
                .withDataRange(sourceLocation.getDataRange())
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
            FetchDataResult fetchDataResult = (FetchDataResult) result;

            // TODO @AT
            editorPresenter.setText(fetchDataResult.getData());

            setEditorMode(fetchDataResult);

            setTitle(fetchDataResult);

        } else {

           // TODO @AT Fire alert, should never get this
        }
    }

    private void setTitle(final FetchDataResult fetchDataResult) {
        final String streamType = StreamTypeNames.asUiName(fetchDataResult.getStreamTypeName());
        getView().setTitle(String.valueOf(fetchDataResult.getSourceLocation().getId()), streamType);
    }
    private void setEditorMode(final FetchDataResult fetchDataResult) {
        final AceEditorMode mode;

        if (StreamTypeNames.META.equals(fetchDataResult.getStreamTypeName())) {
            mode = AceEditorMode.PROPERTIES;
        } else {// We have no way of knowing what type the data is (could be csv, json, xml) so assume XML
            mode = AceEditorMode.XML;
        }
        editorPresenter.setMode(mode);
    }

//    public void setSourceKey(final SourceKey sourceKey) {
//        this.sourceKey = sourceKey;
//    }

    @Override
    protected void onBind() {

    }


    // ===================================================================


//    private class DataNavigatorData implements HasCharacterData {
//
//        private RowCount<Long> partsCount = RowCount.of(0L, false);
//        private RowCount<Long> segmentsCount = RowCount.of(0L, false);
//
//        public void updatePartsCount(final RowCount<Long> partsCount) {
//            this.partsCount = partsCount;
//        }
//
//        public void updateSegmentsCount(final RowCount<Long> segmentsCount) {
//            this.segmentsCount = segmentsCount;
//        }
//
//        @Override
//        public boolean isMultiPart() {
//            // For now assume segmented and multi-part are mutually exclusive
//            return isCurrentDataMultiPart();
//        }
//
//        @Override
//        public Optional<Long> getPartNo() {
//            return Optional.of(getCurrentPartNo());
////            return Optional.ofNullable(currentSourceLocation)
////                    .map(SourceLocation::getPartNo);
//        }
//
//        @Override
//        public Optional<Long> getTotalParts() {
//            return Optional.ofNullable(partsCount)
//                    .filter(RowCount::isExact)
//                    .map(RowCount::getCount);
//        }
//
//        @Override
//        public void setPartNo(final long partNo) {
//            currentPartNo = partNo;
//            showHeadCharacters();
//        }
//
//        @Override
//        public boolean isSegmented() {
//            return isCurrentDataSegmented();
//        }
//
//        @Override
//        public boolean canDisplayMultipleSegments() {
//            return isCurrentDataSegmented()
//                    && getCurDataType().equals(DataType.MARKER);
//        }
//
//        @Override
//        public Optional<Long> getSegmentNoFrom() {
//            final AbstractFetchDataResult lastResult = getLastResult();
//
//            if (lastResult != null && isSegmented()) {
//                return Optional.ofNullable(lastResult)
//                        .map(AbstractFetchDataResult::getItemRange)
//                        .map(OffsetRange::getOffset);
//            } else {
//                return Optional.empty();
//            }
//        }
//
//        @Override
//        public Optional<Long> getSegmentNoTo() {
//            final AbstractFetchDataResult lastResult = getLastResult();
//
//            if (lastResult != null && isSegmented()) {
//                return Optional.ofNullable(lastResult)
//                        .map(AbstractFetchDataResult::getItemRange)
//                        .map(range -> range.getOffset() + range.getLength() - 1);
//            } else {
//                return Optional.empty();
//            }
//        }
//
//        @Override
//        public Optional<Long> getTotalSegments() {
//            return Optional.ofNullable(segmentsCount)
//                    .filter(RowCount::isExact)
//                    .map(RowCount::getCount);
//        }
//
//        @Override
//        public Optional<String> getSegmentName() {
//            final AbstractFetchDataResult lastResult = getLastResult();
//            if (lastResult == null) {
//                return Optional.empty();
//            } else if (DataType.MARKER.equals(getCurDataType())) {
//                return Optional.of(ERROR);
//            } else if (DataType.SEGMENTED.equals(getCurDataType())) {
//                return Optional.of("Record");
//            } else {
//                return Optional.empty();
//            }
//        }
//
//        @Override
//        public void setSegmentNoFrom(final long segmentNo) {
//            currentSegmentNo = segmentNo;
//            update(false);
//        }
//
//        @Override
//        public Optional<Long> getCharFrom() {
//            return Optional.ofNullable(getLastResult())
//                    .map(AbstractFetchDataResult::getSourceLocation)
//                    .flatMap(SourceLocation::getOptDataRange)
//                    .flatMap(DataRange::getOptCharOffsetFrom);
//        }
//
//        @Override
//        public Optional<Long> getCharTo() {
//            return Optional.ofNullable(getLastResult())
//                    .map(AbstractFetchDataResult::getSourceLocation)
//                    .flatMap(SourceLocation::getOptDataRange)
//                    .flatMap(DataRange::getOptCharOffsetTo);
//        }
//
//        @Override
//        public Optional<Long> getTotalChars() {
//            return Optional.ofNullable(getLastResult())
//                    .flatMap(result -> Optional.ofNullable(result.getTotalCharacterCount()))
//                    .filter(RowCount::isExact)
//                    .map(RowCount::getCount);
//        }
//
//        @Override
//        public Optional<Long> getTotalLines() {
//            return Optional.ofNullable(getLastResult())
//                    .map(AbstractFetchDataResult::getSourceLocation)
//                    .flatMap(SourceLocation::getOptDataRange)
//                    .filter(dataRange -> dataRange.getOptLocationFrom().isPresent()
//                            && dataRange.getOptLocationTo().isPresent())
//                    .map(dataRange -> dataRange.getLocationTo().getLineNo()
//                            - dataRange.getLocationFrom().getLineNo()
//                            + 1L); // line nos are inclusive, so add 1
//        }
//
//        @Override
//        public void showHeadCharacters() {
//            currentDataRange = DataRange.from(0, MAX_INITIAL_CHARS);
//            update(false);
//        }
//
//        @Override
//        public void advanceCharactersForward() {
//            currentDataRange = DataRange.from(
//                    getCurrentDataRange().getCharOffsetTo() + 1,
//                    MAX_CHARS_PER_FETCH);
////            if (Long.valueOf(0).equals(getCurrentDataRange().getCharOffsetFrom())) {
////                currentDataRange = DataRange.from(
////                        getCurrentDataRange().getCharOffsetFrom() + MAX_INITIAL_CHARS,
////                        MAX_CHARS_PER_FETCH);
////            } else {
////                currentDataRange = DataRange.from(
////                        getCurrentDataRange().getCharOffsetFrom() + MAX_CHARS_PER_FETCH,
////                        MAX_CHARS_PER_FETCH);
////            }
//            update(false);
//        }
//
//        @Override
//        public void advanceCharactersBackwards() {
//            currentDataRange = DataRange.from(
//                    getCurrentDataRange().getCharOffsetFrom() - MAX_CHARS_PER_FETCH,
//                    MAX_CHARS_PER_FETCH);
//            update(false);
//        }
//
//        @Override
//        public void refresh() {
//            update(false);
//        }
//    }

    // ===================================================================


    public interface SourceView extends View {

//        void setSourceLocation(final SourceLocation sourceLocation);

        void setEditorView(final EditorView editorView);

        ButtonView addButton(final SvgPreset preset);

        void setTitle(final String id, final String type);
    }
}
