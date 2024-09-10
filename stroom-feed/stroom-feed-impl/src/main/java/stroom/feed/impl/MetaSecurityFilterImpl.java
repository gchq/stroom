package stroom.feed.impl;

import stroom.docref.DocRef;
import stroom.feed.api.FeedStore;
import stroom.meta.api.MetaSecurityFilter;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.Clearable;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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

    private static ExpressionTerm buildFeedTerm(final String field, final String filteredFeeds) {
        return ExpressionTerm.builder()
                .field(field)
                .condition(Condition.IN)
                .value(filteredFeeds)
                .build();
    }

    private String getFilteredFeeds(final DocumentPermission permission) {
        // Get all feeds as seen by the processing user.
        List<DocRef> feeds = getCachedFeeds();
        // Filter feeds that the current user has the requested permission on.
        return feeds
                .stream()
                .filter(docRef -> securityContext.hasDocumentPermission(docRef, permission))
                .map(DocRef::getName)
                .collect(Collectors.joining(","));
    }

    @Override
    public Optional<ExpressionOperator> getExpression(final DocumentPermission permission,
                                                      final List<String> fields) {
        Objects.requireNonNull(permission);
        Objects.requireNonNull(fields);

        if (fields.size() == 0) {
            throw new IllegalArgumentException("No fields provided");
        }

        if (!securityContext.isAdmin()) {
            final String filteredFeeds = getFilteredFeeds(permission);

            final Builder builder = ExpressionOperator.builder();
            for (final String field : fields) {
                final ExpressionTerm expressionTerm = buildFeedTerm(field, filteredFeeds);
                builder.addTerm(expressionTerm);
            }
            return Optional.of(builder.build());
        } else {
            return Optional.empty();
        }
    }

    private List<DocRef> getCachedFeeds() {
        // Don't need to synchronise as it doesn't really matter if two threads both update at
        // the same time
        List<DocRef> feeds = feedCache;
        final long now = System.currentTimeMillis();
        if (feeds == null || lastUpdate < now - THIRTY_SECONDS) {
            securityContext.asProcessingUser(() -> feedCache = feedStore.list());
            feeds = feedCache;
            lastUpdate = now;
        }
        return feeds;
    }

    @Override
    public void clear() {
        feedCache = null;
        lastUpdate = 0;
    }
}
