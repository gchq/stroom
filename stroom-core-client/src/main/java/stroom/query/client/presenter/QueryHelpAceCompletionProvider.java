package stroom.query.client.presenter;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.entity.client.presenter.MarkdownConverter;
import stroom.query.shared.CompletionItem;
import stroom.query.shared.CompletionSnippet;
import stroom.query.shared.CompletionValue;
import stroom.query.shared.CompletionsRequest;
import stroom.query.shared.QueryResource;
import stroom.util.shared.PageRequest;

import com.google.gwt.core.client.GWT;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletion;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionCallback;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionSnippet;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionValue;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditor;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorCursorPosition;

import javax.inject.Inject;


public class QueryHelpAceCompletionProvider implements AceCompletionProvider {

    private static final QueryResource QUERY_RESOURCE = GWT.create(QueryResource.class);

    private final RestFactory restFactory;
    private final MarkdownConverter markdownConverter;

    private DocRef dataSourceRef;
    private boolean showAll = true;

    @Inject
    public QueryHelpAceCompletionProvider(final RestFactory restFactory,
                                          final MarkdownConverter markdownConverter) {
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
                    final AceCompletion[] aceCompletions = result
                            .getValues()
                            .stream()
                            .map(this::convertCompletion)
                            .toArray(AceCompletion[]::new);

                    callback.invokeWithCompletions(aceCompletions);
                })
                .exec();
    }

    @SuppressWarnings("PatternVariableCanBeUsed") // cos GWT
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

            if (completionItem instanceof CompletionValue) {
                final CompletionValue completionValue = (CompletionValue) completionItem;
                aceCompletion = new AceCompletionValue(
                        caption,
                        completionValue.getValue(),
                        meta,
                        tooltipHtml,
                        score);

            } else if (completionItem instanceof CompletionSnippet) {
                final CompletionSnippet completionSnippet = (CompletionSnippet) completionItem;
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

    public void setShowAll(final boolean showAll) {
        this.showAll = showAll;
    }
}
