package stroom.feed.impl;

import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaSecurityFilter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;
import java.util.Objects;
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
    public Optional<ExpressionOperator> getExpression(final String permission,
                                                      final List<String> fields) {
        Objects.requireNonNull(permission);
        Objects.requireNonNull(fields);

        if (fields.size() == 0) {
            throw new IllegalArgumentException("No fields provided");
        }

        ExpressionOperator expressionOperator = null;
        if (!securityContext.isAdmin()) {

            // Get all feeds.
            final List<DocRef> feeds = feedStore.list();

            // Filter feeds that the current user has the requested permission on.
            final String filteredFeeds = feeds.stream()
                    .filter(docRef -> securityContext.hasDocumentPermission(docRef.getUuid(), permission))
                    .map(DocRef::getName)
                    .collect(Collectors.joining(","));

            final Builder builder = new Builder(Op.AND);
            for (final String field : fields) {
                builder.addTerm(field, Condition.IN, filteredFeeds);
            }
            expressionOperator = builder.build();
        }

        return Optional.ofNullable(expressionOperator);
    }
}
