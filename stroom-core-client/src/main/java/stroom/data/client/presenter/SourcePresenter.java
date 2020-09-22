package stroom.data.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.SourcePresenter.SourceView;
import stroom.data.client.presenter.TextPresenter.TextView;
import stroom.data.shared.DataRange;
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
import stroom.util.shared.HasCharacterData;
import stroom.util.shared.OffsetRange;
import stroom.util.shared.RowCount;
import stroom.widget.button.client.ButtonView;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

public class SourcePresenter extends MyPresenterWidget<SourceView> implements TextUiHandlers {

    private static final ViewDataResource VIEW_DATA_RESOURCE = GWT.create(ViewDataResource.class);

    private final TextPresenter textPresenter;
    private final Provider<SourceLocationPresenter> sourceLocationPresenterProvider;
    private final UiConfigCache uiConfigCache;
    private final RestFactory restFactory;
    private final ClientSecurityContext clientSecurityContext;
    private final DataNavigatorData dataNavigatorData;

    private SourceLocationPresenter sourceLocationPresenter = null;
    private SourceLocation requestedSourceLocation = null;
    private SourceLocation receivedSourceLocation = null;
    private FetchDataResult lastResult = null;
    private boolean isSteppingSource = false;

//    private SourceKey sourceKey;

    @Inject
    public SourcePresenter(final EventBus eventBus,
                           final SourceView view,
                           final TextPresenter textPresenter,
                           final Provider<SourceLocationPresenter> sourceLocationPresenterProvider,
                           final UiConfigCache uiConfigCache,
                           final RestFactory restFactory,
                           final ClientSecurityContext clientSecurityContext) {
        super(eventBus, view);
        this.textPresenter = textPresenter;
        this.sourceLocationPresenterProvider = sourceLocationPresenterProvider;
        this.uiConfigCache = uiConfigCache;
        this.restFactory = restFactory;
        this.clientSecurityContext = clientSecurityContext;
        this.dataNavigatorData = new DataNavigatorData();

        setEditorOptions(textPresenter);

        view.setTextView(textPresenter.getView());
        view.setNavigatorData(dataNavigatorData);
        view.setNavigatorClickHandler(this::showSourceLocationPopup);
        textPresenter.setUiHandlers(this);
    }

    private void setEditorOptions(final TextPresenter textPresenter) {
        textPresenter.setReadOnly(true);

        // Default to wrapped lines
        textPresenter.getLineWrapOption().setOn(true);
        textPresenter.getLineNumbersOption().setOn(true);
        textPresenter.getStylesOption().setOn(true);

        textPresenter.getCodeCompletionOption().setAvailable(false);
        textPresenter.getUseVimBindingsOption().setAvailable(true);
        textPresenter.getFormatAction().setAvailable(false);
    }

    public void setSourceLocation(final SourceLocation sourceLocation, final boolean force) {
        final boolean hasStepPermission = clientSecurityContext.hasAppPermission(
                PermissionNames.STEPPING_PERMISSION);

        if (hasStepPermission && !isSteppingSource) {
            textPresenter.setControlsVisible(true);
        } else {
            textPresenter.setControlsVisible(false);
        }

        if (force || !Objects.equals(sourceLocation, requestedSourceLocation)) {
            // Keep a record of what data was asked for, which may differ from what we get back
            requestedSourceLocation = sourceLocation;

            doWithConfig(sourceConfig -> {
                fetchSource(sourceLocation, sourceConfig);
            });
        }
    }

    public void setSourceLocation(final SourceLocation sourceLocation) {
        setSourceLocation(sourceLocation, false);
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

            textPresenter.setText(lastResult.getData());
            textPresenter.setFirstLineNumber(receivedSourceLocation.getDataRange().getLocationFrom().getLineNo());

            setEditorMode(lastResult);

            setTitle(lastResult);

            if (DataType.SEGMENTED.equals(lastResult.getDataType())) {
                dataNavigatorData.segmentsCount = result.getTotalItemCount();
            } else {
                dataNavigatorData.partsCount = result.getTotalItemCount();
            }

            getView().refreshNavigator();
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
        textPresenter.setMode(mode);
    }

//    public void setSourceKey(final SourceKey sourceKey) {
//        this.sourceKey = sourceKey;
//    }

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

    private void showSourceLocationPopup() {
        if (lastResult != null && lastResult.getSourceLocation() != null) {
            final SourceLocationPresenter sourceLocationPresenter = getSourceLocationPresenter();
            sourceLocationPresenter.setSourceLocation(lastResult.getSourceLocation());

            sourceLocationPresenter.setPartNoVisible(isCurrentDataMultiPart());
            sourceLocationPresenter.setSegmentNoVisible(isCurrentDataSegmented());

            if (isCurrentDataMultiPart()) {
                sourceLocationPresenter.setPartsCount(lastResult.getTotalItemCount());
            } else {
                sourceLocationPresenter.setPartsCount(RowCount.of(0L, false));
            }

            if (isCurrentDataSegmented()) {
                sourceLocationPresenter.setSegmentsCount(lastResult.getTotalItemCount());
            } else {
                sourceLocationPresenter.setSegmentsCount(RowCount.of(0L, false));
            }
            sourceLocationPresenter.setTotalCharsCount(lastResult.getTotalCharacterCount());
            sourceLocationPresenter.setCharacterControlsVisible(true);
        }

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                sourceLocationPresenter.hide(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final SourceLocation newSourceLocation = sourceLocationPresenter.getSourceLocation();
//                    currentPartNo = newSourceLocation.getPartNo();
//                    currentSegmentNo = newSourceLocation.getSegmentNo();
//                    currentDataRange = newSourceLocation.getOptDataRange().orElse(DEFAULT_DATA_RANGE);

                    setSourceLocation(newSourceLocation);
                }
            }
        };
        sourceLocationPresenter.show(popupUiHandlers);
    }

    private SourceLocationPresenter getSourceLocationPresenter() {
        if (sourceLocationPresenter == null) {
            sourceLocationPresenter = sourceLocationPresenterProvider.get();
        }
        return sourceLocationPresenter;
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


    // ===================================================================


    private class DataNavigatorData implements HasCharacterData {
        private RowCount<Long> partsCount = RowCount.of(0L, false);
        private RowCount<Long> segmentsCount = RowCount.of(0L, false);

        @Override
        public boolean isMultiPart() {
            return isCurrentDataMultiPart();
        }

        @Override
        public Optional<Long> getPartNo() {
            return Optional.ofNullable(receivedSourceLocation)
                    .map(SourceLocation::getPartNo);
        }

        @Override
        public Optional<Long> getTotalParts() {
            return Optional.ofNullable(partsCount)
                    .filter(RowCount::isExact)
                    .map(RowCount::getCount);
        }

        @Override
        public void setPartNo(final long partNo) {
            final SourceLocation newSourceLocation = receivedSourceLocation.clone()
                    .withPartNo(partNo)
                    .withDataRange(null) // different part so clear previous location range
                    .build();

            setSourceLocation(newSourceLocation);
        }

        @Override
        public boolean isSegmented() {
            return isCurrentDataSegmented();
        }

        @Override
        public boolean canDisplayMultipleSegments() {
            return isCurrentDataSegmented()
                    && lastResult != null
                    && DataType.MARKER.equals(lastResult.getDataType());
        }

        @Override
        public Optional<Long> getSegmentNoFrom() {
            if (lastResult != null && isSegmented()) {
                return Optional.ofNullable(lastResult)
                        .map(AbstractFetchDataResult::getItemRange)
                        .map(OffsetRange::getOffset);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Long> getSegmentNoTo() {
            if (lastResult != null && isSegmented()) {
                return Optional.ofNullable(lastResult)
                        .map(AbstractFetchDataResult::getItemRange)
                        .map(range -> range.getOffset() + range.getLength() - 1);
            } else {
                return Optional.empty();
            }
        }

        @Override
        public Optional<Long> getTotalSegments() {
            return Optional.ofNullable(segmentsCount)
                    .filter(RowCount::isExact)
                    .map(RowCount::getCount);
        }

        @Override
        public Optional<String> getSegmentName() {
            if (lastResult == null) {
                return Optional.empty();
            } else if (DataType.MARKER.equals(getCurDataType())) {
                return Optional.of("Error");
            } else if (DataType.SEGMENTED.equals(getCurDataType())) {
                return Optional.of("Record");
            } else {
                return Optional.empty();
            }
        }

        @Override
        public void setSegmentNoFrom(final long segmentNoFrom) {
            final SourceLocation newSourceLocation = receivedSourceLocation.clone()
                    .withSegmentNumber(segmentNoFrom)
                    .withDataRange(null) // different segment so clear previous location range
                    .build();

            setSourceLocation(newSourceLocation);
        }

        @Override
        public boolean canNavigateCharacterData() {
            return true;
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

        ButtonView addButton(final SvgPreset preset);

        void setTitle(final String id, final String type);

        void setNavigatorClickHandler(final Runnable clickHandler);

        void setNavigatorData(final HasCharacterData dataNavigatorData);

        void refreshNavigator();
    }
}
