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

package stroom.pipeline.stepping.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.ClassificationUiHandlers;
import stroom.data.client.presenter.ClassificationWrapperView;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;
import stroom.pipeline.structure.client.presenter.PipelineElementTypesFactory;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.util.shared.ErrorType;
import stroom.util.shared.HasData;
import stroom.util.shared.Indicators;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ElementPresenter
        extends MyPresenterWidget<ElementView>
        implements HasDirtyHandlers, ClassificationUiHandlers {

    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final Provider<ClassificationWrapperView> classificationWrapperViewProvider;
    private final Provider<EditorPresenter> editorProvider;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final RestFactory restFactory;
    private final PipelineElementTypesFactory pipelineElementTypesFactory;

    private PipelineModel pipelineModel;
    private PipelineElement element;
    private List<PipelineProperty> properties;
    private String feedName;
    private String pipelineName;
    private boolean refreshRequired = true;
    private boolean loaded;
    private boolean dirtyCode;
    private DocRef loadedDoc;
    private HasData hasData;
    private final EnumMap<IndicatorType, EditorPresenter> presenterMap = new EnumMap<>(IndicatorType.class);

    private Indicators indicators;
    private EditorPresenter codePresenter;
    private EditorPresenter inputPresenter;
    private EditorPresenter outputPresenter;
    private EditorPresenter logPresenter;
    private boolean desiredLogPanVisibility = true;

    private String classification;
    private ClassificationWrapperView inputView;
    private ClassificationWrapperView outputView;
    private View logView;
    private Consumer<StepType> stepRequestHandler = null;

    @Inject
    public ElementPresenter(final EventBus eventBus,
                            final ElementView view,
                            final Provider<ClassificationWrapperView> classificationWrapperViewProvider,
                            final Provider<EditorPresenter> editorProvider,
                            final DocumentPluginRegistry documentPluginRegistry,
                            final RestFactory restFactory,
                            final PipelineElementTypesFactory pipelineElementTypesFactory) {
        super(eventBus, view);
        this.classificationWrapperViewProvider = classificationWrapperViewProvider;
        this.editorProvider = editorProvider;
        this.documentPluginRegistry = documentPluginRegistry;
        this.restFactory = restFactory;
        this.pipelineElementTypesFactory = pipelineElementTypesFactory;
    }

    public void load(final Consumer<Boolean> consumer) {
        if (!loaded) {
            loaded = true;
            boolean loading = false;

            if (pipelineModel.hasRole(element, PipelineElementType.ROLE_HAS_CODE)) {
                getView().setCodeView(getCodePresenter(element).getView());

                try {
                    final FindElementDocRequest findElementDocRequest = FindElementDocRequest.builder()
                            .pipelineElement(element)
                            .properties(properties)
                            .feedName(feedName)
                            .pipelineName(pipelineName)
                            .build();

                    restFactory
                            .create(STEPPING_RESOURCE)
                            .method(res -> res.findElementDoc(findElementDocRequest))
                            .onSuccess(result -> loadEntityRef(result, consumer))
                            .onFailure(caught -> {
                                dirtyCode = false;
                                setCode(caught.getMessage());
                                clearAllIndicators();
                                consumer.accept(false);
                            })
                            .taskMonitorFactory(this)
                            .exec();

                    loading = true;
                } catch (final RuntimeException e) {
                    AlertEvent.fireErrorFromException(this, e, null);
                }
            }

            // We only care about seeing input if the element mutates the input
            // some how.
            if (pipelineModel.hasRole(element, PipelineElementType.ROLE_MUTATOR)) {
                getView().setInputView(getInputView());
            }

            // We always want to see the output of the element.
            getView().setOutputView(getOutputView());

//            updateLogView(codeIndicators);
            updateLogView();

            if (!loading) {
                Scheduler.get().scheduleDeferred(() -> consumer.accept(true));
            }
        } else {
            consumer.accept(true);
        }
    }

    public void setDesiredLogPanVisibility(final boolean desiredLogPanVisibility) {
        this.desiredLogPanVisibility = desiredLogPanVisibility;
    }

    public void setLogPaneVisibility(final boolean isVisible) {
        getView().setLogVisible(isVisible);
    }

    public boolean getDesiredLogPanVisibility() {
        return desiredLogPanVisibility;
    }

    private void updateLogView() {
//        GWT.log("updateLogView");
        getView().setLogView(getLogView());
        if (logPresenter != null) {
            if (indicators != null && !indicators.isEmpty()) {
                final String content = buildLogContent();
//                GWT.log("content:\n" + content);
                if (content != null && !content.isEmpty()) {
//                    GWT.log("updateLogView - found content");
                    logPresenter.setText(content);
                    getView().setLogVisible(true);
                } else {
//                    GWT.log("updateLogView - empty content");
                    logPresenter.setText("");
                    getView().setLogVisible(false);
                }
            } else {
//                GWT.log("updateLogView - no indicators");
                logPresenter.setText("");
                getView().setLogVisible(false);
            }
        }
    }

//    private IndicatorType determineIndicatorType()

    private String buildLogContent() {
        //noinspection SimplifyStreamApiCallChains // Cos GWT
        final List<LogPaneEntry> logPaneEntries = indicators.getErrorList()
                .stream()
                .filter(Objects::nonNull)
                .map(storedError -> {
                    // Don't need to qualify the paneType if there is only one pane in use
                    final IndicatorType paneType = deriveIndicatorType(storedError);

                    return new LogPaneEntry(
                            paneType,
                            storedError.getSeverity(),
                            NullSafe.get(storedError.getLocation(), Location::getLineNo),
                            storedError.getLocation(),
                            storedError.getMessage());
                })
                .collect(Collectors.toList());

        return logPaneEntries.stream()
                .sorted()
                .map(LogPaneEntry::toString)
                .collect(Collectors.joining("\n"));
    }

    private void loadEntityRef(final DocRef entityRef,
                               final Consumer<Boolean> future) {
        if (entityRef != null) {
            final DocumentPlugin<?> documentPlugin = documentPluginRegistry.get(entityRef.getType());
            documentPlugin.load(entityRef,
                    result -> {
                        loadedDoc = entityRef;
                        hasData = (HasData) result;
                        dirtyCode = false;
                        read();

                        future.accept(true);
                    },
                    caught -> {
                        dirtyCode = false;
                        setCode(caught.getMessage());
                        clearAllIndicators();
                        future.accept(false);
                    },
                    this);
        } else {
            Scheduler.get().scheduleDeferred(() -> future.accept(true));
        }
    }

    public void save() {
        if (loaded && hasData != null && dirtyCode) {
            write();
            final DocumentPlugin documentPlugin = documentPluginRegistry.get(loadedDoc.getType());
            documentPlugin.save(loadedDoc, hasData,
                    result -> {
                        hasData = (HasData) result;
                        dirtyCode = false;
                    },
                    throwable -> {
                        AlertEvent.fireError(
                                this,
                                "Unable to save document " + loadedDoc,
                                throwable.getMessage(), null);
                    },
                    this);
        }
    }

    private void read() {
        if (hasData != null) {
            setCode(hasData.getData());
        } else {
            setCode("");
        }
    }

    private void write() {
        hasData.setData(getCode());
    }

    public String getCode() {
        if (codePresenter == null) {
            return null;
        }
        return codePresenter.getText();
    }

    public void setCode(final String code) {
        if (codePresenter != null) {
            if (!codePresenter.getText().equals(code)) {
                codePresenter.setText(code);
            }

            // Done here to ensure the editor is attached
            codePresenter.getBasicAutoCompletionOption().setAvailable();
            codePresenter.getBasicAutoCompletionOption().setOn();
            codePresenter.getSnippetsOption().setAvailable();
            codePresenter.getSnippetsOption().setOn();
            codePresenter.getLiveAutoCompletionOption().setAvailable();
            codePresenter.getLiveAutoCompletionOption().setOff();

            codePresenter.setMode(getMode(element));


            registerHandler(codePresenter.getView().asWidget().addDomHandler(e -> {
                if (KeyCodes.KEY_ENTER == e.getNativeKeyCode() &&
                    (e.isShiftKeyDown() || e.isControlKeyDown())) {
                    e.preventDefault();
                    if (stepRequestHandler != null) {
                        stepRequestHandler.accept(StepType.REFRESH);
                    }
                }
            }, KeyDownEvent.getType()));
        }
    }

    public void setIndicators(final Indicators indicators) {
        this.indicators = indicators;
        if (hasCodePane()) {
            setIndicatorsOnEditor(IndicatorType.CODE, indicators);
        }
        setIndicatorsOnEditor(IndicatorType.INPUT, indicators);
        setIndicatorsOnEditor(IndicatorType.OUTPUT, indicators);

        updateLogView();
    }

    private void setIndicatorsOnEditor(final IndicatorType indicatorType,
                                       final Indicators indicators) {
        final ErrorType[] types = getErrorTypesForPane(indicatorType);
        final IndicatorLines indicatorLines = NullSafe.get(
                indicators,
                indicators2 -> IndicatorLines.filter(indicators2, false, types));

        final EditorPresenter editorPresenter = presenterMap.get(indicatorType);
        if (editorPresenter != null) {
            editorPresenter.setIndicators(indicatorLines);
        }
    }

    private IndicatorType deriveIndicatorType(final StoredError storedError) {
        if (storedError == null) {
            return IndicatorType.GENERIC;
        } else {
            final ErrorType errorType = storedError.getErrorType();
            //noinspection EnhancedSwitchMigration // Cos GWT
            switch (errorType) {
                case CODE:
                    return IndicatorType.CODE;
                case INPUT:
                    return IndicatorType.INPUT;
                case OUTPUT:
                    return IndicatorType.OUTPUT;
                case GENERIC:
                    return IndicatorType.GENERIC;
                case UNKNOWN:
                    return hasCodePane()
                            ? IndicatorType.CODE
                            : IndicatorType.INPUT;
                default:
                    throw new RuntimeException("Unknown type: " + errorType);
            }
        }
    }

    private ErrorType[] getErrorTypesForPane(final IndicatorType indicatorType) {

        // Decide what to do with unknown errors based on whether we have a code pane or not.
        // If no code pane, we assume unknown errors belong to input pane
        //noinspection EnhancedSwitchMigration // Not on GWT
        switch (indicatorType) {
            case CODE:
                return hasCodePane()
                        ? new ErrorType[]{ErrorType.CODE, ErrorType.UNKNOWN}
                        : new ErrorType[]{ErrorType.CODE};
            case INPUT:
                return hasCodePane()
                        ? new ErrorType[]{ErrorType.INPUT}
                        : new ErrorType[]{ErrorType.INPUT, ErrorType.UNKNOWN};
            case OUTPUT:
                return new ErrorType[]{ErrorType.OUTPUT};
            case GENERIC:
                return new ErrorType[]{ErrorType.GENERIC};
            default:
                throw new RuntimeException("Unknown type: " + indicatorType);
        }
    }

    public void setInput(final String input,
                         final int inputStartLineNo,
                         final boolean formatInput) {
        setInputOutput(inputPresenter, input, inputStartLineNo, formatInput);
    }

    public void setOutput(final String output,
                          final int outputStartLineNo,
                          final boolean formatOutput) {
        setInputOutput(outputPresenter, output, outputStartLineNo, formatOutput);
    }

    private void setInputOutput(final EditorPresenter editorPresenter,
                                final String text,
                                final int outputStartLineNo,
                                final boolean formatOutput) {
        if (editorPresenter != null) {
            editorPresenter.getStylesOption().setOn(formatOutput);

            if (!editorPresenter.getText().equals(text)) {
                editorPresenter.setText(text, formatOutput);
            }

            editorPresenter.setFirstLineNumber(outputStartLineNo);

            editorPresenter.getBasicAutoCompletionOption().setUnavailable();
            editorPresenter.getBasicAutoCompletionOption().setOff();
            editorPresenter.getSnippetsOption().setUnavailable();
            editorPresenter.getSnippetsOption().setOff();
            editorPresenter.getLiveAutoCompletionOption().setUnavailable();
            editorPresenter.getLiveAutoCompletionOption().setOff();
        }
    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public PipelineElement getElement() {
        return element;
    }

    public boolean hasCodePane() {
        return NullSafe.test(
                element,
                elm -> pipelineModel.hasRole(elm, PipelineElementType.ROLE_HAS_CODE));
    }

    public void setElement(final PipelineElement element) {
        this.element = element;
    }

    public void setProperties(final List<PipelineProperty> properties) {
        this.properties = properties;
    }

    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
    }

    public boolean isRefreshRequired() {
        return refreshRequired;
    }

    public void setRefreshRequired(final boolean refreshRequired) {
        this.refreshRequired = refreshRequired;
    }

    public boolean isDirtyCode() {
        return dirtyCode;
    }

    public void clearAllIndicators() {
        this.indicators = null;
        for (final IndicatorType indicatorType : IndicatorType.values()) {
            setIndicatorsOnEditor(indicatorType, null);
        }
        updateLogView();
    }

    private EditorPresenter getCodePresenter(final PipelineElement element) {
        GWT.log("id: " + element.getId() + ", type: " + element.getType());
        if (codePresenter == null) {
            codePresenter = editorProvider.get();
            presenterMap.put(IndicatorType.CODE, codePresenter);
            setCommonEditorOptions(codePresenter);

            codePresenter.setMode(getMode(element));
            codePresenter.getFormatAction().setAvailable(true);

            registerHandler(codePresenter.addValueChangeHandler(event -> {
                dirtyCode = true;
                DirtyEvent.fire(ElementPresenter.this, true);
            }));
            registerHandler(codePresenter.addFormatHandler(event -> {
                dirtyCode = true;
                DirtyEvent.fire(ElementPresenter.this, true);
            }));
        }
        return codePresenter;
    }

    private AceEditorMode getMode(final PipelineElement element) {
        // The default
        AceEditorMode mode = AceEditorMode.XML;
        if (element != null) {
            final String elementType = element.getType();
            if (PipelineElementType.TYPE_DS_PARSER.equals(elementType)) {
                mode = AceEditorMode.STROOM_DATA_SPLITTER;
            } else if (PipelineElementType.TYPE_XML_FRAGMENT_PARSER.equals(elementType)) {
                mode = AceEditorMode.STROOM_FRAGMENT_PARSER;
            } else if (PipelineElementType.TYPE_COMBINED_PARSER.equals(elementType)) {
                // Bit hacky, but we have no access to the type of the text converter
                // CombinedParser ought to be deprecated
                final String code = getCode();
                if (!NullSafe.isBlankString(code)) {
                    if (code.contains("dataSplitter")) {
                        mode = AceEditorMode.STROOM_DATA_SPLITTER;
                    } else if (code.contains("!ENTITY")) {
                        mode = AceEditorMode.STROOM_FRAGMENT_PARSER;
                    }
                } else {
                    // Fallback mode that contains snippets for both
                    mode = AceEditorMode.STROOM_COMBINED_PARSER;
                }
            }
            GWT.log("id: " + element.getId()
                    + ", type: " + element.getType()
                    + ", mode: " + mode);
        }
        return mode;
    }

    @Override
    public void setClassification(final String classification) {
        this.classification = classification;
        if (inputView != null) {
            inputView.setClassification(classification);
        }
        if (outputView != null) {
            outputView.setClassification(classification);
        }
    }

    private View getInputView() {
        if (inputPresenter == null) {
            inputPresenter = editorProvider.get();
            presenterMap.put(IndicatorType.INPUT, inputPresenter);
            setCommonEditorOptions(inputPresenter);
            setReadOnlyEditorOptions(inputPresenter);
            inputView = classificationWrapperViewProvider.get();
            inputView.setContent(inputPresenter.getView());
            inputView.setClassification(classification);
        }
        return inputView;
    }

    private View getOutputView() {
        if (outputPresenter == null) {
            outputPresenter = editorProvider.get();
            presenterMap.put(IndicatorType.OUTPUT, outputPresenter);
            setCommonEditorOptions(outputPresenter);
            setReadOnlyEditorOptions(outputPresenter);

            // Turn on line numbers for the output presenter if this is a validation step as the output needs to show
            // validation errors in the gutter.
            if (element != null && pipelineModel.hasRole(element, PipelineElementType.ROLE_VALIDATOR)) {
                outputPresenter.getLineNumbersOption().setOn(true);
            }

            outputView = classificationWrapperViewProvider.get();
            outputView.setContent(outputPresenter.getView());
            outputView.setClassification(classification);
        }
        return outputView;
    }

    private View getLogView() {
//        GWT.log("Getting console view");
        if (logPresenter == null) {
//            GWT.log("Creating consolePresenter");
            logPresenter = editorProvider.get();
            setCommonEditorOptions(logPresenter);
            setReadOnlyEditorOptions(logPresenter);
            logPresenter.getLineNumbersOption().setOff();
            logPresenter.getLineWrapOption().setOff();
            logPresenter.setMode(AceEditorMode.STROOM_STEPPER);
            logView = logPresenter.getView();
        }
        return logView;
    }

    private void setReadOnlyEditorOptions(final EditorPresenter editorPresenter) {
        editorPresenter.setReadOnly(true);
        // Default to wrapped lines as a lot of output is un-formatted xml
        editorPresenter.getLineWrapOption().setOn();

        editorPresenter.getFormatAction().setUnavailable();
    }

    private void setCommonEditorOptions(final EditorPresenter editorPresenter) {
        editorPresenter.getIndicatorsOption().setAvailable(true);
        editorPresenter.getIndicatorsOption().setOn();

        editorPresenter.getLineNumbersOption().setAvailable(true);
        editorPresenter.getLineNumbersOption().setOn();

        editorPresenter.getLineWrapOption().setAvailable(true);
        editorPresenter.getLineWrapOption().setOff();

        editorPresenter.getShowInvisiblesOption().setAvailable(true);
        editorPresenter.getShowInvisiblesOption().setOff();

        editorPresenter.getUseVimBindingsOption().setAvailable();
    }

    public void setStepRequestHandler(final Consumer<StepType> onStepRefreshRequest) {
        this.stepRequestHandler = onStepRefreshRequest;
    }

    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }

    public interface ElementView extends View {

        void setCodeView(View view);

        void setInputView(View view);

        void setOutputView(View view);

        void setLogView(View view);

        void setLogVisible(final boolean isVisible);
    }

    enum IndicatorType {
        CODE("Code"),
        INPUT("Input"),
        OUTPUT("Output"),
        GENERIC("Generic"),
        ;

        private static final Comparator<IndicatorType> COMPARATOR = Comparator.nullsFirst(
                Comparator.comparing(Function.identity()));

        private final String displayName;

        IndicatorType(final String displayName) {
            this.displayName = displayName;
        }
    }


    // --------------------------------------------------------------------------------


    @SuppressWarnings("ClassCanBeRecord")
    static class LogPaneEntry implements Comparable<LogPaneEntry> {

        private final IndicatorType paneType;
        private final Severity severity;
        private final Integer lineNumber;
        private final Location location;
        private final String message;

        private static final Comparator<LogPaneEntry> SEVERITY_COMPARATOR = Comparator.nullsLast(
                Comparator.comparing(LogPaneEntry::getSeverity, Severity.HIGH_TO_LOW_COMPARATOR));
        private static final Comparator<LogPaneEntry> TYPE_COMPARATOR = Comparator.comparing(
                LogPaneEntry::getPaneType, IndicatorType.COMPARATOR);
        private static final Comparator<LogPaneEntry> COMPARATOR = SEVERITY_COMPARATOR
                .thenComparing(TYPE_COMPARATOR)
                .thenComparing(LogPaneEntry::getLineNumber,
                        Comparator.nullsFirst(Integer::compareTo));

        LogPaneEntry(final IndicatorType paneType,
                     final Severity severity,
                     final Integer lineNumber,
                     final Location location,
                     final String message) {
            this.paneType = paneType;
            this.severity = severity;
            this.lineNumber = lineNumber;
            this.location = location;
            this.message = message;
        }

        Integer getLineNumber() {
            return lineNumber;
        }

        IndicatorType getPaneType() {
            return paneType;
        }

        Severity getSeverity() {
            return severity;
        }

        Location getLocation() {
            return location;
        }

        String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            // Some msgs have no location or line/col numbers of -1 so replace with '?'
            // to make it clear to user that location is unknown
            final String locationStr;
            if (location != null && !location.isUnknown()) {
                // Some step elements can have msgs from multiple panes so include the pane name
                // if there is a location, so the user can tell which pane the location relates to
                final String typeStr = paneType != null && paneType != IndicatorType.GENERIC
                        ? "(" + paneType.displayName + " pane) - "
                        : "";
                locationStr = typeStr
                              + "["
                              + location.toString().replace(String.valueOf(Location.UNKNOWN_VALUE), "?")
                              + "] ";
            } else {
                locationStr = "";
            }
            return severity + ": "
                   + locationStr
                   + message;
        }

        @Override
        public int compareTo(final LogPaneEntry o) {
            return COMPARATOR.compare(this, o);
        }
    }
}
