package stroom.feed.impl;

import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaSecurityFilter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.util.shared.Clearable;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class MetaSecurityFilterImpl implements MetaSecurityFilter, Clearable {

    private static final long THIRTY_SECONDS = 30000;

    private final FeedStore feedStore;
    private final SecurityContext securityContext;

    private volatile List<DocRef> feedCache;
    private volatile long lastUpdate;

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

            // Get all feeds as seen by the processing user.
            List<DocRef> feeds = feedCache;
            final long now = System.currentTimeMillis();
            if (feeds == null || lastUpdate < now - THIRTY_SECONDS) {
                securityContext.asProcessingUser(() -> feedCache = feedStore.list());
                feeds = feedCache;
                lastUpdate = now;
            }

            // Filter feeds that the current user has the requested permission on.
            final String filteredFeeds = feeds
                    .stream()
                    .filter(docRef -> securityContext.hasDocumentPermission(docRef.getUuid(), permission))
                    .map(DocRef::getName)
                    .collect(Collectors.joining(","));

            final Builder builder = ExpressionOperator.builder();
            for (final String field : fields) {
                builder.addTerm(field, Condition.IN, filteredFeeds);
            }
            expressionOperator = builder.build();
        }

        return Optional.ofNullable(expressionOperator);
    }

    @Override
    public void clear() {
        feedCache = null;
        lastUpdate = 0;
    }
}
