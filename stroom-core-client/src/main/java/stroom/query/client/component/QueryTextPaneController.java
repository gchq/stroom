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
import stroom.pipeline.shared.FetchDataRequest;
import stroom.pipeline.shared.FetchDataResult;
import stroom.pipeline.shared.SourceLocation;
import stroom.query.client.result.TextHighlights;
import stroom.security.client.api.ClientSecurityContext;
import stroom.widget.component.client.Controller;
import stroom.widget.hyperlink.client.HyperlinkEvent;
import stroom.widget.hyperlink.client.LinkValue;

import com.google.gwt.core.client.GWT;
import com.google.gwt.core.client.Scheduler;
import com.google.gwt.user.client.Timer;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/// The source of a query result's row, beside the results ([QueryTextPane]): fetches the stream's
/// record and shows it as text, formatted and with the result's highlights, or as HTML, whose
/// links it follows.
public class QueryTextPaneController extends Controller {

    private static final DataResource DATA_RESOURCE = GWT.create(DataResource.class);

    private final Provider<CodeEditorController> codeEditorControllerProvider;
    private final ClassificationLabelController classificationLabelController;
    private final RestFactory restFactory;
    private final ClientSecurityContext securityContext;
    private QueryTextPane view;
    private CodeEditorController editor;
    private List<FetchDataRequest> fetchDataQueue;
    private Timer delayedFetchDataTimer;
    private Set<String> currentHighlightStrings;

    private boolean isHtml;

    private Runnable showFunction;
    private Runnable hideFunction;

    /// Made by GIN; [#attach(QueryTextPane)] gives it its pane.
    ///
    /// @param eventBus                      The application's event bus.
    /// @param codeEditorControllerProvider  Makes the code editor's controller.
    /// @param restFactory                   Fetches the source.
    /// @param securityContext               The user's permissions.
    /// @param classificationLabelController Colours the classification.
    @Inject
    public QueryTextPaneController(final EventBus eventBus,
                                   final Provider<CodeEditorController> codeEditorControllerProvider,
                                   final RestFactory restFactory,
                                   final ClientSecurityContext securityContext,
                                   final ClassificationLabelController classificationLabelController) {
        super(eventBus);
        this.codeEditorControllerProvider = codeEditorControllerProvider;
        this.classificationLabelController = own(classificationLabelController);
        this.restFactory = restFactory;
        this.securityContext = securityContext;
    }

    /// Attaches this to a pane, e.g. one a template declares, attaching its read-only editor and its
    /// classification. The pane is then this controller's widget.
    ///
    /// @param view The pane.
    /// @return This, for its owner to keep.
    public QueryTextPaneController attach(final QueryTextPane view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        classificationLabelController.attach(view.getClassificationLabel());

        editor = make(codeEditorControllerProvider).attach(view.getEditor());
        editor.setReadOnly(true);
        editor.getOptions().getLineNumbersOption().setOn(false);
        editor.getOptions().getLineWrapOption().setOn(true);

        view.setOnLink(link -> {
            final LinkValue hyperlink = LinkValue.create(link);
            if (hyperlink != null) {
                HyperlinkEvent.fire(this, hyperlink, view);
            }
        });

        view.setOnClose(this::close);
        return this;
    }

    private void showData(final String data,
                          final String classification,
                          final Set<String> highlightStrings,
                          final boolean isHtml) {
        this.isHtml = isHtml;

        // Defer showing data to be sure that the data display has been made
        // visible first.
        Scheduler.get().scheduleDeferred(() -> {
            // Closed (e.g. the component was removed) before it ran
            if (isDisposed()) {
                return;
            }

            view.setClassification(classification);
            if (isHtml) {
                view.showHtml(data);
            } else {
                view.showEditor();

                if (highlightStrings != null && !highlightStrings.isEmpty()) {
                    editor.setFormattedHighlights(formattedText ->
                            TextHighlights.find(formattedText, highlightStrings));
                } else {
                    editor.setFormattedHighlights(null);
                }

                editor.setText(data, true);
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

}
