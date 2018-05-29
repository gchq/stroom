package stroom.streamstore;

import stroom.dictionary.DictionaryStore;
import stroom.docref.DocRef;
import stroom.entity.shared.BaseEntity;
import stroom.entity.shared.BaseResultList;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.entity.shared.EntityServiceException;
import stroom.entity.shared.Period;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.common.v2.DateExpressionParser;
import stroom.streamstore.shared.Feed;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.FindStreamCriteria;
import stroom.streamstore.shared.QueryData;
import stroom.streamstore.shared.StreamAttributeCondition;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.streamstore.shared.StreamDataSource;
import stroom.streamstore.shared.StreamStatus;
import stroom.streamstore.shared.StreamType;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ExpressionToFindCriteria {
    private final FeedService feedService;
    private final PipelineStore pipelineStore;
    private final DictionaryStore dictionaryStore;
    private final StreamAttributeKeyService streamAttributeKeyService;
    private final StreamTypeService streamTypeService;

    private static final Map<String, StreamStatus> STREAM_STATUS_MAP = Arrays.stream(StreamStatus.values())
            .collect(Collectors.toMap(StreamStatus::getDisplayValue, Function.identity()));

    private static final Function<List<Op>, String> OP_STACK_DISPLAY = (s) ->
            s.stream().map(Op::getDisplayValue).collect(Collectors.joining(" -> "));

    private static final BiFunction<String, List<Op>, String> OP_STACK_ERROR = (err, ops) ->
            String.format("%s [%s]", err, OP_STACK_DISPLAY.apply(ops));

    @Inject
    public ExpressionToFindCriteria(final FeedService feedService,
                                    @Named("cachedPipelineStore") final PipelineStore pipelineStore,
                                    final DictionaryStore dictionaryStore,
                                    final StreamAttributeKeyService streamAttributeKeyService,
                                    @Named("cachedStreamTypeService") StreamTypeService streamTypeService) {
        this.feedService = feedService;
        this.pipelineStore = pipelineStore;
        this.dictionaryStore = dictionaryStore;
        this.streamAttributeKeyService = streamAttributeKeyService;
        this.streamTypeService = streamTypeService;
    }

    public OldFindStreamCriteria convert(final FindStreamCriteria findStreamCriteria) {
        return this.convert(findStreamCriteria, Context.now());
    }

    public OldFindStreamCriteria convert(final FindStreamCriteria findStreamCriteria, final Context context) {
        final OldFindStreamCriteria criteria = new OldFindStreamCriteria();

        convertExpression(findStreamCriteria.getExpression(), criteria, context);

        criteria.setFetchSet(findStreamCriteria.getFetchSet());
        criteria.setPageRequest(findStreamCriteria.getPageRequest());
        criteria.setStreamIdRange(findStreamCriteria.getStreamIdRange());

        if (findStreamCriteria.getSortList() != null) {
            findStreamCriteria.getSortList().forEach(criteria::addSort);
        }

        if (findStreamCriteria.getSelectedIdSet() != null) {
            criteria.obtainStreamIdSet().addAll(findStreamCriteria.getSelectedIdSet().getSet());
            criteria.obtainStreamIdSet().setMatchAll(findStreamCriteria.getSelectedIdSet().getMatchAll());
            criteria.obtainStreamIdSet().setMatchNull(findStreamCriteria.getSelectedIdSet().getMatchNull());
        }

        return criteria;
    }

    public OldFindStreamCriteria convert(final QueryData queryData) {
        return this.convert(queryData, Context.now());
    }

    public OldFindStreamCriteria convert(final QueryData queryData, final Context context) {
        final OldFindStreamCriteria newCriteria = new OldFindStreamCriteria();

        if ((queryData.getDataSource() == null) || !queryData.getDataSource().getType().equals(StreamDataSource.STREAM_STORE_TYPE)) {
            return newCriteria;
        }

        convertExpression(queryData.getExpression(), newCriteria, context);
        return newCriteria;
    }

    private void convertExpression(final ExpressionOperator expression,
                                   final OldFindStreamCriteria criteria,
                                   final Context context) {
        if (expression != null && expression.enabled() && expression.getChildren() != null) {
            final List<Op> opStack = new ArrayList<>();
            opStack.add(expression.getOp());
            addChildren(expression.getChildren(), opStack, criteria, context);
        }
    }

    private void addChildren(final List<ExpressionItem> children,
                             final List<Op> opStack,
                             final OldFindStreamCriteria criteria, final Context context) {
        final Op currentOp = opStack.get(opStack.size() - 1);
        if (opStack.size() > 3) {
            final String errorMsg = OP_STACK_ERROR.apply("We do not support the following set of nested operations", opStack);
            throw new EntityServiceException(errorMsg);
        }
        if (currentOp.equals(Op.NOT)) {
            if (opStack.size() == 3) {
                final String errorMsg = "No support for deep NOT operations " + OP_STACK_DISPLAY.apply(opStack);
                throw new EntityServiceException(errorMsg);
            } else if (opStack.size() > 1) {
                if (opStack.stream().filter(op -> op.equals(Op.NOT)).count() > 1) {
                    final String errorMsg = OP_STACK_ERROR.apply("No support for nested NOT operations", opStack);
                    throw new EntityServiceException(errorMsg);
                }
            }
        }

        final List<ExpressionTerm> terms = children.stream()
                .filter(ExpressionItem::enabled)
                .filter(item -> item instanceof ExpressionTerm)
                .map(item -> (ExpressionTerm) item)
                .collect(Collectors.toList());
        if (terms.size() > 0) {
            if (currentOp.equals(Op.OR)) {
                // Validate that all terms are of the same field type.
                final Set<String> fieldNames = terms.stream().map(ExpressionTerm::getField).collect(Collectors.toSet());
                if (fieldNames.size() > 1) {
                    final String errorMsg = OP_STACK_ERROR.apply("No support OR operations with mixed fields", opStack);
                    throw new EntityServiceException(errorMsg);
                }

                // Check that if parent is NOT then the only field we are using is FEED.
                if (opStack.contains(Op.NOT) && !StreamDataSource.FEED.equals(fieldNames.iterator().next())) {
                    final String errorMsg = OP_STACK_ERROR.apply("The use of NOT is only supported for Feed", opStack);
                    throw new EntityServiceException(errorMsg);
                }

                addTerms(terms, criteria, opStack.contains(Op.NOT), context);

            } else if (currentOp.equals(Op.AND)) {
                // Check that the same field does not occur more than once.
                final Set<String> fieldNames = terms.stream().map(ExpressionTerm::getField).collect(Collectors.toSet());
                if (fieldNames.size() < terms.size()) {
                    final String errorMsg = OP_STACK_ERROR.apply("AND operation with the same field multiple times", opStack);
                    throw new EntityServiceException(errorMsg);
                }

                // Check that the parent OP is not a NOT.
                if (opStack.contains(Op.NOT)) {
                    final String errorMsg = OP_STACK_ERROR.apply("No support for nested AND within NOT operations", opStack);
                    throw new EntityServiceException(errorMsg);
                }

                addTerms(terms, criteria, opStack.contains(Op.NOT), context);
            } else if (currentOp.equals(Op.NOT)) {
                // Validate that all terms are of the same field type.
                final Set<String> fieldNames = terms.stream().map(ExpressionTerm::getField).collect(Collectors.toSet());
                if (fieldNames.size() > 1) {
                    final String errorMsg = OP_STACK_ERROR.apply("No support NOT operations with mixed fields", opStack);
                    throw new EntityServiceException(errorMsg);
                }

                if (!StreamDataSource.FEED.equals(fieldNames.iterator().next())) {
                    final String errorMsg = OP_STACK_ERROR.apply("The use of NOT is only supported for Feed", opStack);
                    throw new EntityServiceException(errorMsg);
                }

                addTerms(terms, criteria, opStack.contains(Op.NOT), context);
            }
        }

        // Recurse into child operators.
        children.stream()
                .filter(ExpressionItem::enabled)
                .filter(item -> item instanceof ExpressionOperator)
                .map(item -> (ExpressionOperator) item)
                .forEach(expressionOperator -> {
                    final List<Op> childOpStack = new ArrayList<>(opStack);
                    childOpStack.add(expressionOperator.getOp());
                    addChildren(expressionOperator.getChildren(), childOpStack, criteria, context);
                });
    }

    private void addTerms(final List<ExpressionTerm> allTerms, final OldFindStreamCriteria criteria, final boolean negate, final Context context) {
        // Group terms by field.
        final Map<String, List<ExpressionTerm>> map = allTerms.stream()
                .collect(Collectors.groupingBy(ExpressionTerm::getField, Collectors.toList()));

        map.forEach((field, terms) -> {
            if (negate && !StreamDataSource.FEED.equals(field)) {
                final String errorMsg = "Negation attempted on " + field;
                throw new EntityServiceException(errorMsg);
            }

            switch (field) {
                case StreamDataSource.FEED:
                    if (negate) {
                        criteria.obtainFeeds().setExclude(
                                convertEntityIdSetValues(
                                        criteria.obtainFeeds().getExclude(),
                                        field,
                                        getAllValues(terms),
                                        value -> findFeeds(field, value)));
                    } else {
                        criteria.obtainFeeds().setInclude(
                                convertEntityIdSetValues(
                                        criteria.obtainFeeds().getInclude(),
                                        field,
                                        getAllValues(terms),
                                        value -> findFeeds(field, value)));
                    }
                    break;
                case StreamDataSource.PIPELINE:
                    criteria.setPipelineSet(
                            convertDocRefSetValues(
                                    criteria.getPipelineSet(),
                                    field,
                                    getAllValues(terms),
                                    value -> findPipelines(field, value)));
                    break;
                case StreamDataSource.STREAM_TYPE:
                    criteria.setStreamTypeIdSet(
                            convertEntityIdSetLongValues(
                                    criteria.getStreamTypeIdSet(),
                                    field,
                                    getAllValues(terms),
                                    streamTypeName -> {
                                        final StreamType streamType = streamTypeService.get(streamTypeName);
                                        return streamType.getId();
                                    }));
                    break;
                case StreamDataSource.STATUS:
                    criteria.setStatusSet(
                            convertCriteriaSetValues(
                                    criteria.getStatusSet(),
                                    field,
                                    getAllValues(terms),
                                    STREAM_STATUS_MAP::get));
                    break;
                case StreamDataSource.STREAM_ID:
                    criteria.setStreamIdSet(
                            convertEntityIdSetLongValues(
                                    criteria.getStreamIdSet(),
                                    field,
                                    getAllValues(terms),
                                    Long::valueOf));
                    break;
                case StreamDataSource.PARENT_STREAM_ID:
                    criteria.setParentStreamIdSet(
                            convertEntityIdSetLongValues(
                                    criteria.getParentStreamIdSet(),
                                    field,
                                    getAllValues(terms),
                                    Long::valueOf));
                    break;
                case StreamDataSource.CREATE_TIME:
                    setPeriod(criteria.obtainCreatePeriod(), terms, context);
                    break;
                case StreamDataSource.EFFECTIVE_TIME:
                    setPeriod(criteria.obtainEffectivePeriod(), terms, context);
                    break;
                case StreamDataSource.STATUS_TIME:
                    setPeriod(criteria.obtainStatusPeriod(), terms, context);
                    break;
                default:
                    // Assume all other field names are extended fields.
                    criteria.obtainAttributeConditionList()
                            .addAll(getStreamAttributeConditions(field, terms));
            }
        });
    }

    private Set<Feed> findFeeds(final String field, final String value) {
        // Try by name
        try {
            final Feed feed = feedService.get(value);
            if (feed != null) {
                return Collections.singleton(feed);
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }

        final String errorMsg = "Unexpected value '" + value + "' used for " + field;
        throw new EntityServiceException(errorMsg);
    }

    private CriteriaSet<DocRef> findPipelines(final String field, final String value) {
        final CriteriaSet<DocRef> set = new CriteriaSet<>();

        // Try by UUID
        try {
            final DocRef pipelineRef = new DocRef(PipelineDoc.DOCUMENT_TYPE, value);
            final PipelineDoc pipeline = pipelineStore.readDocument(pipelineRef);
            if (pipeline != null) {
                set.add(pipelineRef);
                return set;
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }

        // Try by name
        try {
            final List<DocRef> list = pipelineStore.findByName(value);
            if (list != null) {
                list.forEach(set::add);
                return set;
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }

        final String errorMsg = "Unexpected value '" + value + "' used for " + field;
        throw new EntityServiceException(errorMsg);
    }

    private void setPeriod(final Period period, final List<ExpressionTerm> terms, final Context context) {
        for (final ExpressionTerm term : terms) {
            switch (term.getCondition()) {
                case CONTAINS:
                    period.setFromMs(getMillis(term, term.getValue(), context));
                    period.setToMs(getMillis(term, term.getValue(), context));
                    break;
                case EQUALS:
                    period.setFromMs(getMillis(term, term.getValue(), context));
                    period.setToMs(getMillis(term, term.getValue(), context));
                    break;
                case GREATER_THAN:
                    period.setFromMs(getMillis(term, term.getValue(), context) + 1);
                    break;
                case GREATER_THAN_OR_EQUAL_TO:
                    period.setFromMs(getMillis(term, term.getValue(), context));
                    break;
                case LESS_THAN:
                    period.setToMs(getMillis(term, term.getValue(), context) - 1);
                    break;
                case LESS_THAN_OR_EQUAL_TO:
                    period.setToMs(getMillis(term, term.getValue(), context));
                    break;
                case BETWEEN:
                    final String[] values = term.getValue().split(ExpressionTerm.Condition.IN_CONDITION_DELIMITER);
                    if (values.length > 0) {
                        period.setFromMs(getMillis(term, values[0], context));
                    }
                    if (values.length > 1) {
                        period.setToMs(getMillis(term, values[1], context));
                    }
                    break;
//                case IN:
//                    if (term.getValue() != null && term.getValue().length() > 0) {
//                        values.addAll(Arrays.asList(term.getValue().split(ExpressionTerm.Condition.IN_CONDITION_DELIMITER)));
//                    }
//                    break;
//                case IN_DICTIONARY:
//                    final Set<String> words = dictionaryStore.getWords(term.getDictionary());
//                    values.addAll(words);
//                    break;
                default:
                    final String errorMsg = "Unexpected condition '" + term.getCondition() + "' used for " + term.getField();
                    throw new EntityServiceException(errorMsg);
            }
        }
    }

    private Long getMillis(final ExpressionTerm term, final String value, final Context context) {
        if (value == null) {
            return null;
        }

        final String trimmed = value.trim();
        if (trimmed.length() == 0) {
            return null;
        }

        try {
            return getDate(term.getField(), trimmed, context);
        } catch (final RuntimeException e) {
            final String errorMsg = "Unexpected value '" + value + "' used for " + term.getField();
            throw new EntityServiceException(errorMsg);
        }
    }

    private long getDate(final String fieldName, final String value, final Context context) {
        try {
            //empty optional will be caught below
            return DateExpressionParser.parse(value, context.timeZoneId, context.nowEpochMilli).get().toInstant().toEpochMilli();
        } catch (final RuntimeException e) {
            throw new EntityServiceException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

    private List<String> getAllValues(final List<ExpressionTerm> terms) {
        final List<String> values = new ArrayList<>();
        for (final ExpressionTerm term : terms) {
            switch (term.getCondition()) {
                case EQUALS:
                    if (term.getValue() != null && term.getValue().length() > 0) {
                        values.add(term.getValue());
                    }
                    break;
                case CONTAINS:
                    if (term.getValue() != null && term.getValue().length() > 0) {
                        values.add(term.getValue());
                    }
                    break;
                case IN:
                    if (term.getValue() != null && term.getValue().length() > 0) {
                        values.addAll(Arrays.asList(term.getValue().split(ExpressionTerm.Condition.IN_CONDITION_DELIMITER)));
                    }
                    break;
                case IN_DICTIONARY:
                    final Set<String> words = getDictionaryWords(term.getDictionary());
                    values.addAll(words);
                    break;
                default:
                    final String errorMsg = "Unexpected condition '" + term.getCondition() + "' used for " + term.getField();
                    throw new EntityServiceException(errorMsg);
            }
        }
        return values;
    }

    private Set<String> getDictionaryWords(final DocRef docRef) {
        Set<String> words = Collections.emptySet();
        final String data = dictionaryStore.getCombinedData(docRef);
        if (data != null) {
            words = new HashSet<>(Arrays.asList(data.split("\n")));
        }
        return words;
    }

    private <T> CriteriaSet<T> convertCriteriaSetValues(final CriteriaSet<T> existing, final String field, final List<String> values, final Function<String, T> mapping) {
        if (existing != null && existing.size() > 0) {
            final String errorMsg = "Field has already been added " + field;
            throw new EntityServiceException(errorMsg);
        }

        final CriteriaSet<T> set = new CriteriaSet<>();
        for (final String value : values) {
            if (value == null) {
                final String errorMsg = "Null value used for " + field;
                throw new EntityServiceException(errorMsg);
            }
            try {
                T val = mapping.apply(value);
                if (val == null) {
                    final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                    throw new EntityServiceException(errorMsg);
                }
                set.add(val);
            } catch (final RuntimeException e) {
                final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                throw new EntityServiceException(errorMsg);
            }
        }

        if (set.size() == 0) {
            return null;
        }

        return set;
    }

    private <T extends BaseEntity> EntityIdSet<T> convertEntityIdSetValues(final EntityIdSet<T> existing, final String field, final List<String> values, final Function<String, Set<T>> mapping) {
        if (existing != null && existing.size() > 0) {
            final String errorMsg = "Field has already been added " + field;
            throw new EntityServiceException(errorMsg);
        }

        final EntityIdSet<T> entityIdSet = new EntityIdSet<>();
        final Set<Long> set = entityIdSet.getSet();
        for (final String value : values) {
            if (value == null) {
                final String errorMsg = "Null value used for " + field;
                throw new EntityServiceException(errorMsg);
            }
            try {
                final Set<T> val = mapping.apply(value);
                if (val == null) {
                    final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                    throw new EntityServiceException(errorMsg);
                }
                entityIdSet.addAllEntities(val);
            } catch (final RuntimeException e) {
                final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                throw new EntityServiceException(errorMsg);
            }
        }

        if (set.size() == 0) {
            return null;
        }

        return entityIdSet;
    }

    private CriteriaSet<DocRef> convertDocRefSetValues(final CriteriaSet<DocRef> existing, final String field, final List<String> values, final Function<String, CriteriaSet<DocRef>> mapping) {
        if (existing != null && existing.size() > 0) {
            final String errorMsg = "Field has already been added " + field;
            throw new EntityServiceException(errorMsg);
        }

        final CriteriaSet<DocRef> set = new CriteriaSet<>();
        for (final String value : values) {
            if (value == null) {
                final String errorMsg = "Null value used for " + field;
                throw new EntityServiceException(errorMsg);
            }
            try {
                final CriteriaSet<DocRef> val = mapping.apply(value);
                if (val == null) {
                    final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                    throw new EntityServiceException(errorMsg);
                }
                val.forEach(set::add);
            } catch (final RuntimeException e) {
                final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                throw new EntityServiceException(errorMsg);
            }
        }

        if (set.size() == 0) {
            return null;
        }

        return set;
    }

    private <T extends BaseEntity> EntityIdSet<T> convertEntityIdSetLongValues(final EntityIdSet<T> existing, final String field, final List<String> values, final Function<String, Long> mapping) {
        if (existing != null && existing.size() > 0) {
            final String errorMsg = "Field has already been added " + field;
            throw new EntityServiceException(errorMsg);
        }

        final EntityIdSet<T> entityIdSet = new EntityIdSet<>();
        final Set<Long> set = entityIdSet.getSet();
        for (final String value : values) {
            if (value == null) {
                final String errorMsg = "Null value used for " + field;
                throw new EntityServiceException(errorMsg);
            }
            try {
                Long val = mapping.apply(value);
                if (val == null) {
                    final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                    throw new EntityServiceException(errorMsg);
                }
                entityIdSet.add(val);
            } catch (final RuntimeException e) {
                final String errorMsg = "Unexpected value '" + value + "' used for " + field;
                throw new EntityServiceException(errorMsg);
            }
        }

        if (set.size() == 0) {
            return null;
        }

        return entityIdSet;
    }

    private List<StreamAttributeCondition> getStreamAttributeConditions(final String field, final List<ExpressionTerm> terms) {
        final BaseResultList<StreamAttributeKey> keys = streamAttributeKeyService.find(new FindStreamAttributeKeyCriteria(field));

        if (keys == null || keys.size() == 0) {
            final String errorMsg = "No stream attribute key found for " + field;
            throw new EntityServiceException(errorMsg);
        }

        return terms.stream().map(term -> new StreamAttributeCondition(keys.getFirst(), term.getCondition(), term.getValue())).collect(Collectors.toList());
    }

    public static class Context {
        private final String timeZoneId;
        private final long nowEpochMilli;

        public Context(final String timeZoneId, final long nowEpochMilli) {
            this.timeZoneId = timeZoneId;
            this.nowEpochMilli = nowEpochMilli;
        }

        public static Context now() {
            return new Context(null, System.currentTimeMillis());
        }
    }
}
