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

package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionSnippet;
import stroom.query.shared.CompletionValue;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.CompletionsRequest.TextType;
import stroom.query.shared.QueryHelpType;
import stroom.query.shared.QueryResource;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.ui.config.client.UiConfigCache;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionValue;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;

import java.util.Set;
import javax.inject.Inject;


public class QueryHelpAceCompletionProvider
        implements AceCompletionProvider, HasTaskMonitorFactory, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final MarkdownConverter markdownConverter;
    private final UiConfigCache uiConfigCache;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    private DocRef dataSourceRef;
    private Set<QueryHelpType> includedTypes = QueryHelpType.ALL_TYPES;
    private TextType textType = TextType.STROOM_QUERY_LANGUAGE;

    @Inject
    public QueryHelpAceCompletionProvider(final EventBus eventBus,
                                          final RestFactory restFactory,
                                          final MarkdownConverter markdownConverter,
                                          final UiConfigCache uiConfigCache) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.markdownConverter = markdownConverter;
        this.uiConfigCache = uiConfigCache;
    }

    @Override
    public void getProposals(final AceEditor editor,
                             final AceEditorCursorPosition pos,
                             final String prefix,
                             final AceCompletionCallback callback) {

        uiConfigCache.get(uiConfig -> {
            final CompletionsRequest completionsRequest =
                    new CompletionsRequest(
                            dataSourceRef,
                            textType,
                            editor.getText(),
                            pos.getRow(),
                            pos.getColumn(),
                            prefix,
                            includedTypes,
                            uiConfig.getMaxEditorCompletionEntries());
            restFactory
                    .create(QUERY_RESOURCE)
                    .method(res -> res.fetchCompletions(completionsRequest))
                    .onSuccess(result -> {
                        final AceCompletion[] aceCompletions = result
                                .getValues()
                                .stream()
                                .map(this::convertCompletion)
                                .toArray(AceCompletion[]::new);

                        callback.invokeWithCompletions(aceCompletions);
                    })
                    .taskMonitorFactory(taskMonitorFactory)
                    .exec();
        });
    }

    private AceCompletion convertCompletion(final CompletionItem completionItem) {
        if (completionItem == null) {
            return null;
        } else {
            final String tooltipHtml = markdownConverter.convertMarkdownToHtmlInFrame(
                            completionItem.getTooltip())
                    .asString();

            final String caption = completionItem.getCaption();
            final String meta = completionItem.getMeta();
            final int score = completionItem.getScore();

            final AceCompletion aceCompletion;

            if (completionItem instanceof final CompletionValue completionValue) {
                aceCompletion = new AceCompletionValue(
                        caption,
                        completionValue.getValue(),
                        meta,
                        tooltipHtml,
                        score);

            } else if (completionItem instanceof final CompletionSnippet completionSnippet) {
                aceCompletion = new AceCompletionSnippet(
                        caption,
                        completionSnippet.getSnippet(),
                        score,
                        meta,
                        tooltipHtml);
            } else {
                throw new RuntimeException("Unknown type " + completionItem.getClass().getName());
            }
            return aceCompletion;
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public void setIncludedTypes(final Set<QueryHelpType> includedTypes) {
        this.includedTypes = includedTypes;
    }

    public void setTextType(final TextType textType) {
        this.textType = textType;
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
