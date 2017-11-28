package stroom.streamtask.server;

import org.springframework.stereotype.Component;
import stroom.entity.shared.EntityServiceException;
import stroom.feed.server.FeedService;
import stroom.feed.shared.Feed;
import stroom.feed.shared.FindFeedCriteria;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.streamstore.server.StreamTypeService;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.FindStreamDataSource;
import stroom.streamstore.shared.FindStreamTypeCriteria;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamType;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SourceSelectorToFindCriteria {
    private final FeedService feedService;
    private final StreamTypeService streamTypeService;

    @Inject
    public SourceSelectorToFindCriteria(final FeedService feedService, final StreamTypeService streamTypeService) {
        this.feedService = feedService;
        this.streamTypeService = streamTypeService;
    }

    public FindStreamCriteria convert(final QueryData queryData) {
        final FindStreamCriteria newCriteria = new FindStreamCriteria();

        if ((queryData.getDataSource() == null) || !queryData.getDataSource().getType().equals(QueryData.STREAM_STORE_TYPE)) {
            return newCriteria;
        }

        // We only construct valid filtering for the stream store

        // Now dig through the query and copy things into their proper fields
        final ExpressionOperator rootOp = queryData.getExpression();

        final List<String> parentStreamsToInclude = new ArrayList<>();
        final List<String> streamTypeNamesToInclude = new ArrayList<>();
        final List<String> streamIdsToInclude = new ArrayList<>();
        final List<String> feedNamesToInclude = new ArrayList<>();
        final List<String> feedNamesToExclude = new ArrayList<>();

        // the root must be 'and'
        if (rootOp.getOp().equals(ExpressionOperator.Op.AND)) {
            for (ExpressionItem expressionItem : rootOp.getChildren()) {
                if (expressionItem instanceof ExpressionOperator) {
                    final ExpressionOperator operator = (ExpressionOperator) expressionItem;

                    // All inclusion sets must go inside 'OR' statements
                    if (operator.getOp().equals(ExpressionOperator.Op.OR)) {
                        // Within an inclusion set, all field names must be the same
                        final Collection<ExpressionTerm> terms = operator.getChildren().stream()
                                .filter(o -> o instanceof ExpressionTerm)
                                .map(o -> (ExpressionTerm) o)
                                .filter(t -> t.getCondition().equals(ExpressionTerm.Condition.EQUALS)
                                        || t.getCondition().equals(ExpressionTerm.Condition.IN))
                                .collect(Collectors.toList());

                        if (terms.size() != operator.getChildren().size()) {
                            final String errorMsg = "Found invalid terms inside an OR, an OR is used for inclusion sets, they must all be equals/in terms for same field";
                            throw new EntityServiceException(errorMsg);
                        }

                        final Set<String> fieldNames = terms.stream()
                                .map(ExpressionTerm::getField)
                                .distinct()
                                .collect(Collectors.toSet());
                        if (fieldNames.size() != 1) {
                            final String errorMsg = "Found mixed terms inside an OR, an OR is used for inclusion sets, they must all be same field within the set";
                            throw new EntityServiceException(errorMsg);
                        }

                        final List<String> values = terms.stream()
                                .map(ExpressionTerm::getValue)
                                .collect(Collectors.toList());

                        switch (fieldNames.iterator().next()) {
                            case FindStreamDataSource.FEED:
                                feedNamesToInclude.addAll(values);
                                break;
                            case FindStreamDataSource.STREAM_TYPE:
                                streamTypeNamesToInclude.addAll(values);
                                break;
                            case FindStreamDataSource.PARENT_STREAM_ID:
                                parentStreamsToInclude.addAll(values);
                                break;
                            case FindStreamDataSource.STREAM_ID:
                                streamIdsToInclude.addAll(values);
                                break;
                        }

                    } else if (operator.getOp().equals(ExpressionOperator.Op.NOT)) {
                        // Must contain an OR with feed exclusions
                        if (operator.getChildren().size() == 1) {
                            final ExpressionItem feedExclusionOrRaw = operator.getChildren().get(0);
                            if (feedExclusionOrRaw instanceof ExpressionOperator) {
                                final ExpressionOperator feedExclusionOr = (ExpressionOperator) feedExclusionOrRaw;
                                if (feedExclusionOr.equals(ExpressionOperator.Op.OR)) {
                                    feedExclusionOr.getChildren().forEach(feedExcludeRaw -> {
                                        if (feedExcludeRaw instanceof ExpressionTerm) {
                                            final ExpressionTerm feedExcludeTerm = (ExpressionTerm) feedExcludeRaw;
                                            // We need the ID for this feed name
                                            if (feedExcludeTerm.getField().equals(FindStreamDataSource.FEED)) {
                                                if (feedExcludeTerm.getCondition().equals(ExpressionTerm.Condition.EQUALS)) {
                                                    feedNamesToExclude.add(feedExcludeTerm.getValue());
                                                } else if (feedExcludeTerm.getCondition().equals(ExpressionTerm.Condition.IN)) {
                                                    Arrays.asList(feedExcludeTerm.getValue()
                                                            .split(ExpressionTerm.Condition.IN_CONDITION_DELIMITER))
                                                            .forEach(feedNamesToExclude::add);
                                                }
                                            } else {
                                                final String error = "Could not convert criteria, Found wrong condition inside AND -> NOT -> OR -> child, expected EQUALS for feed name";
                                                throw new EntityServiceException(error);
                                            }
                                        } else {
                                            final String error = "Could not convert criteria, Found Operator inside AND -> NOT -> OR -> child, expected term containing feed to exclude";
                                            throw new EntityServiceException(error);
                                        }
                                    });
                                } else {
                                    final String error = "Could not convert criteria, Found AND or NOT inside AND -> NOT -> Child(0), expected OR containing Feed= to exclude";
                                    throw new EntityServiceException(error);
                                }
                            } else {
                                final String error = "Could not convert criteria, found term inside the AND -> NOT -> Child(0), expected an OR containing Feed= to exclude";
                                throw new EntityServiceException(error);
                            }
                        } else {
                            final String error = "Could not convert criteria, found multiple children inside the AND -> NOT, expected an OR containing Feed= to exclude";
                            throw new EntityServiceException(error);
                        }
                    } else {
                        final String error = "Could not convert criteria, found a nested AND, only top level can be and, then nested operators must be OR's for inclusion sets";
                        throw new EntityServiceException(error);
                    }

                } else if (expressionItem instanceof ExpressionTerm) {
                    final ExpressionTerm term = (ExpressionTerm) expressionItem;
                }
            }
        }

        final Function<String, Optional<Long>> getIdForFeedName = (name) -> {
            List<Feed> feeds = feedService.find(new FindFeedCriteria(name));
            if (feeds != null && feeds.size() > 0) {
                return Optional.of(feeds.get(0).getId());
            }

            return Optional.empty();
        };

        final Function<String, Optional<Long>> getIdForStreamType = (name) -> {
            List<StreamType> streamTypes = streamTypeService.find(new FindStreamTypeCriteria(name));
            if (streamTypes != null && streamTypes.size() > 0) {
                return Optional.of(streamTypes.get(0).getId());
            }

            return Optional.empty();
        };

        final List<Long> streamTypeIdsToInclude = streamTypeNamesToInclude.stream()
                .map(getIdForStreamType)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        final List<Long> feedIdsToInclude = feedNamesToInclude.stream()
                .map(getIdForFeedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        final List<Long> feedIdsToExclude = feedNamesToExclude.stream()
                .map(getIdForFeedName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        if (streamTypeIdsToInclude.size() > 0) {
            newCriteria.obtainStreamTypeIdSet().addAll(streamTypeIdsToInclude);
        }
        if (feedIdsToInclude.size() > 0) {
            newCriteria.obtainFeeds().obtainInclude().addAll(feedIdsToInclude);
        }
        if (feedIdsToExclude.size() > 0) {
            newCriteria.obtainFeeds().obtainExclude().addAll(feedIdsToExclude);
        }

        // Do all the other criteria need to be set to 'ANY'?

            /*
            originalCriteria.obtainStreamProcessorIdSet().copyFrom(newCriteria.obtainStreamProcessorIdSet());
            originalCriteria.obtainFeeds().copyFrom(newCriteria.obtainFeeds());
            originalCriteria.obtainPipelineIdSet().copyFrom(newCriteria.obtainPipelineIdSet());
            originalCriteria.obtainStreamTypeIdSet().copyFrom(newCriteria.obtainStreamTypeIdSet());
            originalCriteria.obtainStreamIdSet().copyFrom(newCriteria.obtainStreamIdSet());
            originalCriteria.obtainStatusSet().copyFrom(newCriteria.obtainStatusSet());
            originalCriteria.obtainStreamIdRange().copyFrom(newCriteria.obtainStreamIdRange());
            originalCriteria.obtainParentStreamIdSet().copyFrom(newCriteria.obtainParentStreamIdSet());
            originalCriteria.createPeriod = Period.clone(newCriteria.createPeriod);
            originalCriteria.effectivePeriod = Period.clone(newCriteria.effectivePeriod);
            originalCriteria.statusPeriod = Period.clone(newCriteria.statusPeriod);

            if (newCriteria.attributeConditionList == null) {
                originalCriteria.attributeConditionList = null;
            } else {
                originalCriteria.attributeConditionList = new ArrayList<>(other.attributeConditionList);
            }
            */
        return newCriteria;
    }
}
