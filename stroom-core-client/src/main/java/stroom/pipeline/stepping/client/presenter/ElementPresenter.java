/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.pipeline.stepping.client.presenter;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.presenter.ClassificationUiHandlers;
import stroom.data.client.presenter.ClassificationWrapperView;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.document.client.event.DirtyEvent;
import stroom.document.client.event.DirtyEvent.DirtyHandler;
import stroom.document.client.event.HasDirtyHandlers;
import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.Indicator;
import stroom.editor.client.view.IndicatorLines;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.stepping.client.presenter.ElementPresenter.ElementView;
import stroom.util.shared.HasData;
import stroom.util.shared.Location;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.util.client.Future;
import stroom.widget.util.client.FutureImpl;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.HandlerRegistration;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class ElementPresenter extends MyPresenterWidget<ElementView> implements
        HasDirtyHandlers,
        ClassificationUiHandlers {

    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final Provider<ClassificationWrapperView> classificationWrapperViewProvider;
    private final Provider<EditorPresenter> editorProvider;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final RestFactory restFactory;

    private PipelineElement element;
    private List<PipelineProperty> properties;
    private String feedName;
    private String pipelineName;
    private boolean refreshRequired = true;
    private boolean loaded;
    private boolean dirtyCode;
    private DocRef loadedDoc;
    private HasData hasData;
    private final EnumMap<IndicatorType, IndicatorLines> indicatorsMap = new EnumMap<>(IndicatorType.class);
    private final EnumMap<IndicatorType, EditorPresenter> presenterMap = new EnumMap<>(IndicatorType.class);

    private EditorPresenter codePresenter;
    private EditorPresenter inputPresenter;
    private EditorPresenter outputPresenter;
    private EditorPresenter logPresenter;

    private String classification;
    private ClassificationWrapperView inputView;
    private ClassificationWrapperView outputView;
    private View logView;

    @Inject
    public ElementPresenter(final EventBus eventBus,
                            final ElementView view,
                            final Provider<ClassificationWrapperView> classificationWrapperViewProvider,
                            final Provider<EditorPresenter> editorProvider,
                            final DocumentPluginRegistry documentPluginRegistry,
                            final RestFactory restFactory) {
        super(eventBus, view);
        this.classificationWrapperViewProvider = classificationWrapperViewProvider;
        this.editorProvider = editorProvider;
        this.documentPluginRegistry = documentPluginRegistry;
        this.restFactory = restFactory;
    }

    public Future<Boolean> load() {
        final FutureImpl<Boolean> future = new FutureImpl<>();

        if (!loaded) {
            loaded = true;
            boolean loading = false;

            if (element.getElementType().hasRole(PipelineElementType.ROLE_HAS_CODE)) {
                getView().setCodeView(getCodePresenter().getView());

                try {
                    final FindElementDocRequest findElementDocRequest = FindElementDocRequest.builder()
                            .pipelineElement(element)
                            .properties(properties)
                            .feedName(feedName)
                            .pipelineName(pipelineName)
                            .build();

                    final Rest<DocRef> rest = restFactory.create();
                    rest
                            .onSuccess(result -> loadEntityRef(result, future))
                            .onFailure(caught -> {
                                dirtyCode = false;
                                setCode(caught.getMessage(), null);
                                future.setResult(false);
                            })
                            .call(STEPPING_RESOURCE)
                            .findElementDoc(findElementDocRequest);

                    loading = true;
                } catch (final RuntimeException e) {
                    AlertEvent.fireErrorFromException(this, e, null);
                }
            }

            // We only care about seeing input if the element mutates the input
            // some how.
            if (element.getElementType().hasRole(PipelineElementType.ROLE_MUTATOR)) {
                getView().setInputView(getInputView());
            }

            // We always want to see the output of the element.
            getView().setOutputView(getOutputView());

//            updateLogView(codeIndicators);
            updateLogView();

            if (!loading) {
                Scheduler.get().scheduleDeferred(() -> future.setResult(true));
            }
        } else {
            future.setResult(true);
        }

        return future;
    }

    public void toggleLogVisibility() {
        getView().toggleLogVisible();
    }

    private void updateLogView() {
        getView().setLogView(getLogView());
        if (logPresenter != null) {
            if (haveIndicators()) {
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

    private String buildLogContent() {
        final int editorCount = presenterMap.size();

//        if (indicatorsMap.get(IndicatorType.CODE) != null) {
//            GWT.log("buildLogContent() get(code) indicatorLines.getLocationAgnosticIndicator:\n>> "
//                    + indicatorsMap.get(IndicatorType.CODE).getLocationAgnosticIndicator());
//        }
//        if (indicatorsMap.get(IndicatorType.OUTPUT) != null) {
//            GWT.log("buildLogContent() get(output) indicatorLines.getLocationAgnosticIndicator:\n>> "
//                    + indicatorsMap.get(IndicatorType.OUTPUT).getLocationAgnosticIndicator());
//        }

        return indicatorsMap.entrySet()
                .stream()
                .filter(entry -> entry.getValue() != null && !entry.getValue().isEmpty())
                .map(entry -> {
                    final String type = editorCount > 1
                            ? entry.getKey().displayName.toLowerCase()
                            : null;
                    return indicatorLinesToString(
                            type,
                            entry.getValue());
                })
                .collect(Collectors.joining("\n"));
    }

    private String indicatorLinesToString(final String type, final IndicatorLines indicatorLines) {

        Objects.requireNonNull(indicatorLines);
        final List<String> lines = new ArrayList<>();
        final Indicator locationAgnosticIndicator = indicatorLines.getLocationAgnosticIndicator();
//        GWT.log("indicatorLinesToString() " + type + " - " + locationAgnosticIndicator);

        if (locationAgnosticIndicator != null && !locationAgnosticIndicator.isEmpty()) {
//            GWT.log("indicatorLinesToString() Adding locationAgnosticIndicator:\n>> " + locationAgnosticIndicator);
            addIndicator(lines, type, null, locationAgnosticIndicator);
        }

        for (final Integer lineNumber : indicatorLines.getLineNumbers()) {
            final Indicator lineSpecificIndicator = indicatorLines.getIndicator(lineNumber);
            if (lineSpecificIndicator != null && !lineSpecificIndicator.isEmpty()) {
//                GWT.log("indicatorLinesToString() Adding lineSpecificIndicator:\n>> " + lineSpecificIndicator);
                addIndicator(lines, type, lineNumber, lineSpecificIndicator);
            }
        }
        final String str = String.join("\n", lines);
//        GWT.log("indicatorLinesToString() str:\n>> " + str);
        return str;
    }

    private void addIndicator(final List<String> lines,
                              final String type,
                              final Integer lineNo,
                              final Indicator indicator) {
//        GWT.log(type + " " + lineNo + " " + indicator);

        final Map<Severity, Set<StoredError>> errorMap = indicator.getErrorMap();
        for (final Severity severity : Severity.SEVERITIES) {
            final Set<StoredError> storedErrors = errorMap.get(severity);
            if (storedErrors != null && !storedErrors.isEmpty()) {
                storedErrors.stream()
                        .map(storedError -> {
                            final Location location = storedError.getLocation();
                            // Some msgs have no location or line/col numbers of -1 so replace with '?'
                            // to make it clear to user that location is unknown
                            final String locationStr = location != null
                                    ? location.toString().replace("-1", "?")
                                    : "?:?:?";
                            // Some step elements can have msgs from multiple panes so include the pane name
                            final String typeStr = type != null
                                    ? "(" + type + " pane) - "
                                    : "";
                            return storedError.getSeverity() + ": "
                                    + typeStr + "["
                                    + locationStr + "] "
                                    + storedError.getMessage();
                        })
                        .forEach(lines::add);
            }
        }
    }

    private boolean haveIndicators() {
        return indicatorsMap.entrySet()
                .stream()
                .anyMatch(entry -> entry.getValue() != null && !entry.getValue().isEmpty());
    }

//    private void updateLogView(final IndicatorLines indicatorLines) {
////        GWT.log("updateConsoleView " + indicatorLines);
//        if (indicatorLines != null
//                && indicatorLines.getLocationAgnosticIndicator() != null
//                && !indicatorLines.getLocationAgnosticIndicator().isEmpty()) {
//            getView().setLogVisible(true);
//        } else {
//            getView().setLogVisible(false);
//        }
//        getView().setLogView(getLogView());
//    }

    private void loadEntityRef(final DocRef entityRef, final FutureImpl<Boolean> future) {
        if (entityRef != null) {
            final DocumentPlugin<?> documentPlugin = documentPluginRegistry.get(entityRef.getType());
            documentPlugin.load(entityRef,
                    result -> {
                        loadedDoc = entityRef;
                        hasData = (HasData) result;
                        dirtyCode = false;
                        read();

                        future.setResult(true);
                    },
                    caught -> {
                        dirtyCode = false;
                        setCode(caught.getMessage(), null);
                        future.setResult(false);
                    });
        } else {
            Scheduler.get().scheduleDeferred(() -> future.setResult(true));
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
                                ((Throwable) throwable).getMessage(), null);
                    });
        }
    }

    private void read() {
        final IndicatorLines codeIndicators = indicatorsMap.get(IndicatorType.CODE);
        if (hasData != null) {
            setCode(hasData.getData(), codeIndicators);
        } else {
            setCode("", codeIndicators);
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

    public void setCode(final String code,
                        final IndicatorLines codeIndicators) {
        if (codePresenter != null) {
//            this.codeIndicators = codeIndicators;

            if (!codePresenter.getText().equals(code)) {
                codePresenter.setText(code);
            }

//            codePresenter.setIndicators(codeIndicators);
            setCodeIndicators(codeIndicators);

            // Done here to ensure the editor is attached
            codePresenter.getBasicAutoCompletionOption().setAvailable();
            codePresenter.getBasicAutoCompletionOption().setOn();
            codePresenter.getSnippetsOption().setAvailable();
            codePresenter.getSnippetsOption().setOn();
            codePresenter.getLiveAutoCompletionOption().setAvailable();
            codePresenter.getLiveAutoCompletionOption().setOff();
            updateLogView();
        }
    }

    public void setCodeIndicators(final IndicatorLines codeIndicators) {
//        GWT.log("Setting codeIndicators " + codeIndicators);
        if (codePresenter != null) {
            setIndicatorsOnEditor(IndicatorType.CODE, codeIndicators);
        }
        updateLogView();
    }

    private void setIndicatorsOnEditor(final IndicatorType indicatorType,
                                       final IndicatorLines indicatorLines) {
//        GWT.log("------------------vvvvvvvvvvvvv-------------------------");

        if (indicatorLines == null || indicatorLines.isEmpty()) {
//            GWT.log("Removing type " + indicatorType);
            indicatorsMap.remove(indicatorType);
        } else {
            indicatorsMap.put(indicatorType, indicatorLines);
//            GWT.log("setIndicatorsOnEditor() put indicatorLines.getLocationAgnosticIndicator:\n>> "
//                    + indicatorLines.getLocationAgnosticIndicator());
//            GWT.log("setIndicatorsOnEditor() get1 indicatorLines.getLocationAgnosticIndicator:\n>> "
//                    + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator());

//            GWT.log("setIndicatorsOnEditor() indicatorLines1(type: "
//                    + indicatorType + ", count: "
//                    + indicatorLines.getLocationAgnosticIndicator().size() + "):\n>> "
//                    + indicatorLines.getLocationAgnosticIndicator());
        }

//        if (indicatorsMap.get(indicatorType) != null) {
//            GWT.log("setIndicatorsOnEditor() get2 indicatorLines.getLocationAgnosticIndicator:\n>> "
//                    + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator());
//            GWT.log("count: " + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator().size());
//        }
//        if (indicatorLines != null) {
//            GWT.log("setIndicatorsOnEditor() indicatorLines2(type: "
//                    + indicatorType + ", count: "
//                    + indicatorLines.getLocationAgnosticIndicator().size() + "):\n>> "
//                    + indicatorLines.getLocationAgnosticIndicator());
//        }

        final EditorPresenter editorPresenter = presenterMap.get(indicatorType);
//        if (indicatorLines != null) {
//            GWT.log("setIndicatorsOnEditor() indicatorLines3(type: "
//                    + indicatorType + ", count: "
//                    + indicatorLines.getLocationAgnosticIndicator().size() + "):\n>> "
//                    + indicatorLines.getLocationAgnosticIndicator());
//        }
        if (editorPresenter != null) {
//            if (indicatorsMap.get(indicatorType) != null) {
//                GWT.log("setIndicatorsOnEditor() get3 indicatorLines.getLocationAgnosticIndicator:\n>> "
//                        + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator());
//                GWT.log("count: " + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator().size());
//            }

//            if (indicatorLines != null) {
//                GWT.log("setIndicatorsOnEditor() indicatorLines4(type: "
//                        + indicatorType + ", count: "
//                        + indicatorLines.getLocationAgnosticIndicator().size() + "):\n>> "
//                        + indicatorLines.getLocationAgnosticIndicator());
//            }

            editorPresenter.setIndicators(indicatorLines);

//            if (indicatorLines != null) {
//                GWT.log("setIndicatorsOnEditor() indicatorLines5(type: "
//                        + indicatorType + ", count: "
//                        + indicatorLines.getLocationAgnosticIndicator().size() + "):\n>> "
//                        + indicatorLines.getLocationAgnosticIndicator());
//            }

//            if (indicatorsMap.get(indicatorType) != null) {
//                GWT.log("setIndicatorsOnEditor() get4 indicatorLines.getLocationAgnosticIndicator:\n>> "
//                        + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator());
//                GWT.log("count: " + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator().size());
//            }
        }
//        if (indicatorsMap.get(indicatorType) != null) {
//            GWT.log("setIndicatorsOnEditor() get5 indicatorLines.getLocationAgnosticIndicator:\n>> "
//                    + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator());
//            GWT.log("count: " + indicatorsMap.get(indicatorType).getLocationAgnosticIndicator().size());
//        }
//        GWT.log("-------------------^^^^^^^^^^^^------------------------");
        updateLogView();
    }

    public void setInput(final String input,
                         final int inputStartLineNo,
                         final boolean formatInput,
                         final IndicatorLines inputIndicators) {
        if (inputPresenter != null) {

            inputPresenter.getStylesOption().setOn(formatInput);

            if (!inputPresenter.getText().equals(input)) {
                inputPresenter.setText(input, formatInput);
            }

            inputPresenter.setFirstLineNumber(inputStartLineNo);
//            inputPresenter.setIndicators(inputIndicators);

            inputPresenter.getBasicAutoCompletionOption().setUnavailable();
            inputPresenter.getBasicAutoCompletionOption().setOff();
            inputPresenter.getSnippetsOption().setUnavailable();
            inputPresenter.getSnippetsOption().setOff();
            inputPresenter.getLiveAutoCompletionOption().setUnavailable();
            inputPresenter.getLiveAutoCompletionOption().setOff();
            setIndicatorsOnEditor(IndicatorType.INPUT, inputIndicators);
            updateLogView();
        }
    }

    public void setOutput(final String output,
                          final int outputStartLineNo,
                          final boolean formatOutput,
                          final IndicatorLines outputIndicators) {
        if (outputPresenter != null) {
            outputPresenter.getStylesOption().setOn(formatOutput);

            if (!outputPresenter.getText().equals(output)) {
                outputPresenter.setText(output, formatOutput);
            }

            outputPresenter.setFirstLineNumber(outputStartLineNo);
//            outputPresenter.setIndicators(outputIndicators);

            outputPresenter.getBasicAutoCompletionOption().setUnavailable();
            outputPresenter.getBasicAutoCompletionOption().setOff();
            outputPresenter.getSnippetsOption().setUnavailable();
            outputPresenter.getSnippetsOption().setOff();
            outputPresenter.getLiveAutoCompletionOption().setUnavailable();
            outputPresenter.getLiveAutoCompletionOption().setOff();
            setIndicatorsOnEditor(IndicatorType.OUTPUT, outputIndicators);
            updateLogView();
        }
    }

//    public void setConsole(final String consoleText,
//                          final int outputStartLineNo,
//                          final boolean formatOutput,
//                          final IndicatorLines outputIndicators) {
//        if (consolePresenter != null) {
//            consolePresenter.setText(consoleText);
//            consolePresenter.getStylesOption().setOn(formatOutput);
//            consolePresenter.setReadOnly(true);
//            consolePresenter.setFirstLineNumber(outputStartLineNo);
//            consolePresenter.setIndicators(outputIndicators);
//            consolePresenter.getBasicAutoCompletionOption().setUnavailable();
//            consolePresenter.getBasicAutoCompletionOption().setOff();
//            consolePresenter.getSnippetsOption().setUnavailable();
//            consolePresenter.getSnippetsOption().setOff();
//            consolePresenter.getLiveAutoCompletionOption().setUnavailable();
//            consolePresenter.getLiveAutoCompletionOption().setOff();
//        }
//    }

    @Override
    public HandlerRegistration addDirtyHandler(final DirtyHandler handler) {
        return addHandlerToSource(DirtyEvent.getType(), handler);
    }

    public PipelineElement getElement() {
        return element;
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
        for (final IndicatorType indicatorType : IndicatorType.values()) {
            setIndicatorsOnEditor(indicatorType, null);
        }
        updateLogView();
    }

    private EditorPresenter getCodePresenter() {
        if (codePresenter == null) {
            codePresenter = editorProvider.get();
            presenterMap.put(IndicatorType.CODE, codePresenter);
            setCommonEditorOptions(codePresenter);

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
            if (element != null && element.getElementType().hasRole(PipelineElementType.ROLE_VALIDATOR)) {
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


    // --------------------------------------------------------------------------------


    public interface ElementView extends View {

        void setCodeView(View view);

        void setInputView(View view);

        void setOutputView(View view);

        void setLogView(View view);

        void setLogVisible(final boolean isVisible);

        void toggleLogVisible();
    }


    // --------------------------------------------------------------------------------


    private enum IndicatorType {
        CODE("Code"),
        INPUT("Input"),
        OUTPUT("Output"),
        ;

        private final String displayName;

        IndicatorType(final String displayName) {
            this.displayName = displayName;
        }
    }

}
