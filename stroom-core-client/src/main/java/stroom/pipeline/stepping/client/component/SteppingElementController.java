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

package stroom.pipeline.stepping.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.view.ClassificationLabelController;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.document.client.DocumentPlugin;
import stroom.document.client.DocumentPluginRegistry;
import stroom.editor.client.component.CodeEditor;
import stroom.editor.client.component.CodeEditorController;
import stroom.editor.client.view.IndicatorLines;
import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineElementType;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.stepping.FindElementDocRequest;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.shared.stepping.SteppingResource;
import stroom.pipeline.structure.client.presenter.PipelineModel;
import stroom.util.shared.Document;
import stroom.util.shared.Embeddable;
import stroom.util.shared.ErrorType;
import stroom.util.shared.HasData;
import stroom.util.shared.Indicators;
import stroom.util.shared.Location;
import stroom.util.shared.NullSafe;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.component.client.Controller;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/// One pipeline element's panes on the stepping screen ([SteppingElement]): loads the element's
/// code (e.g. its XSLT) when first shown, saves it, and shows the element's input, output and log
/// for each step. Its editors are attached as the element needs them.
public class SteppingElementController extends Controller {

    private static final SteppingResource STEPPING_RESOURCE = GWT.create(SteppingResource.class);

    private final SteppingElement view;
    private final Provider<ClassificationLabelController> classificationLabelControllerProvider;
    private final Provider<CodeEditorController> codeEditorProvider;
    private final DocumentPluginRegistry documentPluginRegistry;
    private final RestFactory restFactory;

    private PipelineModel pipelineModel;
    private PipelineElement element;
    private List<PipelineProperty> properties;
    private String feedName;
    private String pipelineName;
    private boolean refreshRequired = true;
    private boolean loaded;
    private boolean dirty;
    private DocRef docRef;
    private Document document;
    private final EnumMap<IndicatorType, CodeEditorController> editorMap = new EnumMap<>(IndicatorType.class);

    private Indicators indicators;
    private CodeEditorController codeEditor;
    private CodeEditorController inputEditor;
    private CodeEditorController outputEditor;
    private CodeEditorController logEditor;
    private boolean desiredLogPanVisibility = true;

    private Consumer<StepType> stepRequestHandler = null;

    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                              The application's event bus.
    /// @param view                                  The content.
    /// @param classificationLabelControllerProvider Colours the input's and output's
    ///                                              classification banners.
    /// @param codeEditorProvider                    Makes the controllers of the editors.
    /// @param documentPluginRegistry                Loads and saves the element's code.
    /// @param restFactory                           Finds the document holding the element's code.
    @Inject
    public SteppingElementController(
            final EventBus eventBus,
            final SteppingElement view,
            final Provider<ClassificationLabelController> classificationLabelControllerProvider,
            final Provider<CodeEditorController> codeEditorProvider,
            final DocumentPluginRegistry documentPluginRegistry,
            final RestFactory restFactory) {
        super(eventBus);
        this.view = view;
        this.classificationLabelControllerProvider = classificationLabelControllerProvider;
        this.codeEditorProvider = codeEditorProvider;
        this.documentPluginRegistry = documentPluginRegistry;
        this.restFactory = restFactory;
        initWidget(view);
    }

    /// Shows the panes the element needs and loads its code, if it has any, the first time it is
    /// called (or the first since [#setLoaded(boolean)] was given `false`).
    ///
    /// @param consumer Given `true` when it is loaded, or `false` if the code couldn't be.
    public void load(final Consumer<Boolean> consumer) {
        if (!loaded) {
            loaded = true;
            boolean loading = false;

            final boolean hasCode = pipelineModel.hasRole(element, PipelineElementType.ROLE_HAS_CODE);
            view.setCodeVisible(hasCode);
            if (hasCode) {
                attachCodeEditor();

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
                                setCode(caught.getMessage());
                                clearAllIndicators();
                                consumer.accept(false);
                            })
                            .owner(this)
                            .taskMonitorFactory(taskMonitor())
                            .exec();

                    loading = true;
                } catch (final RuntimeException e) {
                    AlertEvent.fireErrorFromException(this, e, null);
                }
            }

            // We only care about seeing input if the element mutates the input
            // some how.
            final boolean isMutator = pipelineModel.hasRole(element, PipelineElementType.ROLE_MUTATOR);
            view.setInputVisible(isMutator);
            if (isMutator) {
                attachInputEditor();
            }

            // We always want to see the output of the element.
            attachOutputEditor();

            updateLogView();

            if (!loading) {
                Scheduler.get().scheduleDeferred(() -> consumer.accept(true));
            }
        } else {
            consumer.accept(true);
        }
    }

    /// @param desiredLogPanVisibility Whether the user wants the log shown when there is one.
    public void setDesiredLogPanVisibility(final boolean desiredLogPanVisibility) {
        this.desiredLogPanVisibility = desiredLogPanVisibility;
    }

    /// @param isVisible Whether the log pane is shown.
    public void setLogPaneVisibility(final boolean isVisible) {
        view.setLogVisible(isVisible);
    }

    /// @return Whether the user wants the log shown when there is one.
    public boolean getDesiredLogPanVisibility() {
        return desiredLogPanVisibility;
    }

    private void updateLogView() {
        final CodeEditorController logEditor = getLogEditor();
        if (indicators != null && !indicators.isEmpty()) {
            final String content = buildLogContent();
            if (content != null && !content.isEmpty()) {
                logEditor.setText(content);
                view.setLogVisible(true);
            } else {
                logEditor.setText("");
                view.setLogVisible(false);
            }
        } else {
            logEditor.setText("");
            view.setLogVisible(false);
        }
    }

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
            final DocumentPlugin<Document> documentPlugin = documentPluginRegistry.get(entityRef.getType(),
                    Document.class);
            documentPlugin.load(entityRef,
                    guard(result -> {
                        docRef = entityRef;
                        document = result;
                        read();

                        future.accept(true);
                    }),
                    caught -> {
                        if (!isDisposed()) {
                            setCode(caught.getMessage());
                            clearAllIndicators();
                            future.accept(false);
                        }
                    },
                    taskMonitor());
        } else {
            Scheduler.get().scheduleDeferred(() -> future.accept(true));
        }
    }

    /// Saves the element's code if it is loaded and has been changed.
    ///
    /// @param onComplete Run when it is saved, has failed to save or had nothing to save.
    public void save(final Runnable onComplete) {
        if (loaded && isDirty()) {
            final Document toSave = write();

            final DocumentPlugin<Document> documentPlugin = documentPluginRegistry.get(docRef.getType(),
                    Document.class);
            documentPlugin.save(docRef, toSave,
                    result -> {
                        document = result;
                        refreshDirty();
                        onComplete.run();
                    },
                    throwable -> {
                        AlertEvent.fireError(
                                this,
                                "Unable to save document " + docRef,
                                throwable.getMessage(), null);
                        onComplete.run();
                    },
                    taskMonitor());
        } else {
            onComplete.run();
        }
    }

    private void read() {
        if (document instanceof final HasData hasData) {
            setCode(hasData.getData());
        } else {
            setCode("");
        }
        refreshDirty();
    }

    private void onCodeChange() {
        refreshDirty();
        onChange.run();
    }

    private Document write() {
        // Build the document to save from the current editor content without clobbering the loaded
        // baseline. The baseline is only advanced on a successful save so that a failed save leaves
        // the element dirty.
        if (document instanceof final HasData hasData) {
            return (Document) hasData.copyWithData(getCode());
        }
        return document;
    }

    /// @return The element's code as it is in its editor, or `null` if it has none.
    public String getCode() {
        if (codeEditor == null) {
            return null;
        }
        return codeEditor.getText();
    }

    private void setCode(final String code) {
        if (codeEditor != null) {
            if (!codeEditor.getText().equals(code)) {
                codeEditor.setText(code);
            }

            // Done here to ensure the editor is attached
            codeEditor.getOptions().getBasicAutoCompletionOption().setAvailable();
            codeEditor.getOptions().getBasicAutoCompletionOption().setOn();
            codeEditor.getOptions().getSnippetsOption().setAvailable();
            codeEditor.getOptions().getSnippetsOption().setOn();
            codeEditor.getOptions().getLiveAutoCompletionOption().setAvailable();
            codeEditor.getOptions().getLiveAutoCompletionOption().setOff();

            codeEditor.setMode(getMode(element));

            // Code embedded in another pipeline can't be changed here
            if (document instanceof final Embeddable embeddable && embeddable.getEmbeddedIn() != null) {
                final DocRef pipelineDocRef = pipelineModel.getPipelineLayer().getSourcePipeline();
                if (!pipelineDocRef.equals(embeddable.getEmbeddedIn())) {
                    codeEditor.setReadOnly(true);
                }
            }
        }
    }

    /// Shows the step's errors and warnings against the lines of the panes they are about, and in
    /// the log.
    ///
    /// @param indicators The errors and warnings, or `null` for none.
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

        final CodeEditorController editor = editorMap.get(indicatorType);
        if (editor != null) {
            editor.setIndicators(indicatorLines);
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

    /// Shows the element's input for the step.
    ///
    /// @param input            The input.
    /// @param inputStartLineNo The number of its first line.
    /// @param formatInput      Whether to format it.
    public void setInput(final String input,
                         final int inputStartLineNo,
                         final boolean formatInput) {
        setInputOutput(inputEditor, input, inputStartLineNo, formatInput);
    }

    /// Shows the element's output for the step.
    ///
    /// @param output            The output.
    /// @param outputStartLineNo The number of its first line.
    /// @param formatOutput      Whether to format it.
    public void setOutput(final String output,
                          final int outputStartLineNo,
                          final boolean formatOutput) {
        setInputOutput(outputEditor, output, outputStartLineNo, formatOutput);
    }

    private void setInputOutput(final CodeEditorController editor,
                                final String text,
                                final int outputStartLineNo,
                                final boolean formatOutput) {
        if (editor != null) {
            editor.getOptions().getStylesOption().setOn(formatOutput);

            if (!editor.getText().equals(text)) {
                editor.setText(text, formatOutput);
            }

            editor.setFirstLineNumber(outputStartLineNo);

            editor.getOptions().getBasicAutoCompletionOption().setUnavailable();
            editor.getOptions().getBasicAutoCompletionOption().setOff();
            editor.getOptions().getSnippetsOption().setUnavailable();
            editor.getOptions().getSnippetsOption().setOff();
            editor.getOptions().getLiveAutoCompletionOption().setUnavailable();
            editor.getOptions().getLiveAutoCompletionOption().setOff();
        }
    }

    /// @param onChange What to run when the user changes the element's code or settings.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    /// @return The element.
    public PipelineElement getElement() {
        return element;
    }

    /// @return Whether the element has code, so a code pane.
    public boolean hasCodePane() {
        return NullSafe.test(
                element,
                elm -> pipelineModel.hasRole(elm, PipelineElementType.ROLE_HAS_CODE));
    }

    /// @param element The element shown.
    public void setElement(final PipelineElement element) {
        this.element = element;
    }

    /// @param properties The element's properties, used to find its code.
    public void setProperties(final List<PipelineProperty> properties) {
        this.properties = properties;
    }

    /// @return The element's properties.
    public List<PipelineProperty> getProperties() {
        return properties;
    }

    /// @param feedName The name of the feed being stepped, used to find the element's code.
    public void setFeedName(final String feedName) {
        this.feedName = feedName;
    }

    /// @param pipelineName The name of the pipeline, used to find the element's code.
    public void setPipelineName(final String pipelineName) {
        this.pipelineName = pipelineName;
    }

    /// @return Whether the element's panes are to be refreshed at the next step.
    public boolean isRefreshRequired() {
        return refreshRequired;
    }

    /// @param refreshRequired Whether the element's panes are to be refreshed at the next step.
    public void setRefreshRequired(final boolean refreshRequired) {
        this.refreshRequired = refreshRequired;
    }

    /// @return Whether the element's code has been changed since it was loaded or saved.
    public boolean isDirty() {
        // Returns the verdict cached by refreshDirty(). The enclosing pipeline asks every open
        // element whether it is dirty on each keypress, and reading the editor content to answer is
        // expensive for a large document, so the comparison is only made when the editor changes.
        return dirty;
    }

    // Recomputes dirtiness by comparing the current editor content against the loaded/last saved
    // baseline, mirroring DocumentEditor.onChange(). Comparing rather than latching a flag means
    // reverting an edit (e.g. typing a letter then deleting it) returns to a clean state.
    private void refreshDirty() {
        dirty = loaded
                && document instanceof final HasData hasData
                && !Objects.equals(hasData.getData(), getCode());
    }

    /// @param loaded Whether the element's code is loaded; `false` loads it again when next shown.
    public void setLoaded(final boolean loaded) {
        this.loaded = loaded;
        refreshDirty();
    }

    /// @return The document holding the element's code, or `null` if it has none.
    public DocRef getDocRef() {
        return docRef;
    }

    /// Clears the errors and warnings from every pane and the log.
    public void clearAllIndicators() {
        this.indicators = null;
        for (final IndicatorType indicatorType : IndicatorType.values()) {
            setIndicatorsOnEditor(indicatorType, null);
        }
        updateLogView();
    }

    private void attachCodeEditor() {
        if (codeEditor == null) {
            codeEditor = attachEditor(view.getCode());
            editorMap.put(IndicatorType.CODE, codeEditor);
            setCommonEditorOptions(codeEditor);

            codeEditor.setMode(getMode(element));
            codeEditor.getFormatAction().setAvailable(true);

            // Fire a change event on any edit so the enclosing presenter re-evaluates dirty state via
            // onChange(). This is a "something changed" signal, not a dirty assertion - the actual
            // dirtiness is recomputed by comparison in refreshDirty(), so a reverted edit returns to
            // clean. Note that element code is not part of the pipeline structure, so this must not
            // touch the pipeline model: rebuilding it on each keypress makes the editor lag.
            own(codeEditor.addValueChangeHandler(event -> onCodeChange()));
            own(codeEditor.addFormatListener(event -> onCodeChange()));

            // Ctrl+Enter or Shift+Enter in the code refreshes the step (added once, not each time
            // the code is loaded)
            own(view.getCode().addDomHandler(e -> {
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
        }
        return mode;
    }

    /// Shows the data's classification below the element's input and output.
    ///
    /// @param classification The classification, or null for none.
    public void setClassification(final String classification) {
        view.setClassification(classification);
    }

    private void attachInputEditor() {
        if (inputEditor == null) {
            inputEditor = attachEditor(view.getInput());
            editorMap.put(IndicatorType.INPUT, inputEditor);
            setCommonEditorOptions(inputEditor);
            setReadOnlyEditorOptions(inputEditor);
            make(classificationLabelControllerProvider).attach(view.getInputClassificationLabel());
        }
    }

    private void attachOutputEditor() {
        if (outputEditor == null) {
            outputEditor = attachEditor(view.getOutput());
            editorMap.put(IndicatorType.OUTPUT, outputEditor);
            setCommonEditorOptions(outputEditor);
            setReadOnlyEditorOptions(outputEditor);

            // Turn on line numbers for the output if this is a validation step as the output needs to
            // show validation errors in the gutter.
            if (element != null && pipelineModel.hasRole(element, PipelineElementType.ROLE_VALIDATOR)) {
                outputEditor.getOptions().getLineNumbersOption().setOn(true);
            }

            make(classificationLabelControllerProvider).attach(view.getOutputClassificationLabel());
        }
    }

    private CodeEditorController getLogEditor() {
        if (logEditor == null) {
            logEditor = attachEditor(view.getLog());
            setCommonEditorOptions(logEditor);
            setReadOnlyEditorOptions(logEditor);
            logEditor.getOptions().getLineNumbersOption().setOff();
            logEditor.getOptions().getLineWrapOption().setOff();
            logEditor.setMode(AceEditorMode.STROOM_STEPPER);
        }
        return logEditor;
    }

    private CodeEditorController attachEditor(final CodeEditor editor) {
        return make(codeEditorProvider).attach(editor);
    }

    private void setReadOnlyEditorOptions(final CodeEditorController editor) {
        editor.setReadOnly(true);
        // Default to wrapped lines as a lot of output is un-formatted xml
        editor.getOptions().getLineWrapOption().setOn();

        editor.getFormatAction().setUnavailable();
    }

    private void setCommonEditorOptions(final CodeEditorController editor) {
        editor.getOptions().getIndicatorsOption().setAvailable(true);
        editor.getOptions().getIndicatorsOption().setOn();

        editor.getOptions().getLineNumbersOption().setAvailable(true);
        editor.getOptions().getLineNumbersOption().setOn();

        editor.getOptions().getLineWrapOption().setAvailable(true);
        editor.getOptions().getLineWrapOption().setOff();

        editor.getOptions().getShowInvisiblesOption().setAvailable(true);
        editor.getOptions().getShowInvisiblesOption().setOff();

        editor.getOptions().getUseVimBindingsOption().setAvailable();
    }

    /// @param onStepRefreshRequest Given [StepType#REFRESH] when the user asks for the step to be
    ///                             refreshed from the code (Ctrl+Enter or Shift+Enter).
    public void setStepRequestHandler(final Consumer<StepType> onStepRefreshRequest) {
        this.stepRequestHandler = onStepRefreshRequest;
    }

    /// @param pipelineModel The pipeline the element is in.
    public void setPipelineModel(final PipelineModel pipelineModel) {
        this.pipelineModel = pipelineModel;
    }



    // --------------------------------------------------------------------------------


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
