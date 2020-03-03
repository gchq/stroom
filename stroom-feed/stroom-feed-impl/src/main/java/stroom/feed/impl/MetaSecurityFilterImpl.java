package stroom.feed.impl;

import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaSecurityFilter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Singleton
class MetaSecurityFilterImpl implements MetaSecurityFilter {
    private final FeedStore feedStore;
    private final SecurityContext securityContext;

    @Inject
    MetaSecurityFilterImpl(final FeedStore feedStore,
                           final SecurityContext securityContext) {
        this.feedStore = feedStore;
        this.securityContext = securityContext;
    }

    @Override
    public Optional<ExpressionOperator> getExpression(final String permission) {
        if (securityContext.isAdmin()) {
            return Optional.empty();
        }

        final List<DocRef> feeds = feedStore.list();
        final List<ExpressionTerm> terms = feeds.stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef.getType(), docRef.getUuid(), permission))
                .map(docRef -> new ExpressionTerm.Builder().field("Feed").condition(Condition.EQUALS).value(docRef.getName()).build())
                .collect(Collectors.toList());

        if (terms.size() == 0) {
            final ExpressionTerm expressionTerm = new ExpressionTerm.Builder().field("Feed").condition(Condition.IS_NULL).build();
            final ExpressionOperator expressionOperator = new ExpressionOperator.Builder().addTerm(expressionTerm).build();
            return Optional.of(expressionOperator);
        }

        final ExpressionOperator expressionOperator = new ExpressionOperator.Builder(Op.OR).addTerms(terms).build();
        return Optional.of(expressionOperator);
    }
}
