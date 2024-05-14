package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.QueryResource;
import stroom.task.client.HasTaskListener;
import stroom.task.client.TaskListener;
import stroom.task.client.TaskListenerImpl;
import stroom.util.shared.PageRequest;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.gwt.safehtml.shared.SafeHtml;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionValue;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;

import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;


public class QueryHelpAceCompletionProvider
        implements AceCompletionProvider, HasTaskListener, HasHandlers {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final EventBus eventBus;
    private final RestFactory restFactory;
    private final MarkdownConverter markdownConverter;
    private final TaskListenerImpl taskListener = new TaskListenerImpl(this);

    private DocRef dataSourceRef;
    private boolean showAll = true;

    @Inject
    public QueryHelpAceCompletionProvider(final EventBus eventBus,
                                          final RestFactory restFactory,
                                          final MarkdownConverter markdownConverter) {
        this.eventBus = eventBus;
        this.restFactory = restFactory;
        this.markdownConverter = markdownConverter;
    }

    @Override
    public void getProposals(final AceEditor editor,
                             final AceEditorCursorPosition pos,
                             final String prefix,
                             final AceCompletionCallback callback) {
        final CompletionsRequest completionsRequest =
                new CompletionsRequest(
                        new PageRequest(0, 100),
                        null,
                        dataSourceRef,
                        editor.getText(),
                        pos.getRow(),
                        pos.getColumn(),
                        new StringMatch(MatchType.STARTS_WITH, false, prefix),
                        showAll);
        restFactory
                .create(QUERY_RESOURCE)
                .method(res -> res.fetchCompletions(completionsRequest))
                .onSuccess(result -> {
                    final List<AceCompletion> aceCompletions = result
                            .getValues()
                            .stream()
                            .map(completion -> {
                                final SafeHtml markDownSafeHtml = markdownConverter.convertMarkdownToHtmlInFrame(
                                        completion.getTooltip());
                                return new AceCompletionValue(
                                        completion.getCaption(),
                                        completion.getValue(),
                                        completion.getMeta(),
                                        markDownSafeHtml.asString(),
                                        completion.getScore());
                            })
                            .collect(Collectors.toList());
                    callback.invokeWithCompletions(aceCompletions.toArray(new AceCompletion[0]));
                })
                .taskListener(taskListener)
                .exec();
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public void setShowAll(final boolean showAll) {
        this.showAll = showAll;
    }

    @Override
    public void setTaskListener(final TaskListener taskListener) {
        this.taskListener.setTaskListener(taskListener);
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
