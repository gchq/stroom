package stroom.pipeline.refdata;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.DataSource;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DocRefField;
import stroom.datasource.api.v2.FieldTypes;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.pipeline.refdata.store.RefDataStore;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.pipeline.refdata.store.RefStoreEntry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.query.api.v2.ExpressionItem;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.security.api.SecurityContext;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PermissionException;

import javax.inject.Inject;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ReferenceDataServiceImpl implements ReferenceDataService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ReferenceDataResourceImpl.class);

    private static final DocRef REF_STORE_PSEUDO_DOC_REF = new DocRef(
            "Searchable",
            "Reference Data Store",
            "Reference Data Store");

    private static final List<Condition> SUPPORTED_STRING_CONDITIONS = List.of(Condition.EQUALS);
    private static final List<Condition> SUPPORTED_DOC_REF_CONDITIONS = List.of(Condition.IS_DOC_REF, Condition.EQUALS);

    private static final AbstractField KEY_FIELD = new TextField(
            "Key", true, SUPPORTED_STRING_CONDITIONS);
    private static final AbstractField VALUE_FIELD = new TextField(
            "Value", true, SUPPORTED_STRING_CONDITIONS);
    private static final AbstractField MAP_NAME_FIELD = new TextField(
            "Map Name", true, SUPPORTED_STRING_CONDITIONS);
    private static final AbstractField CREATE_TIME_FIELD = new DateField(
            "Create Time", true);
    private static final AbstractField EFFECTIVE_TIME_FIELD = new DateField(
            "Effective Time", true);
    private static final AbstractField LAST_ACCESSED_TIME_FIELD = new DateField(
            "Last Accessed Time", true);
    private static final AbstractField PIPELINE_FIELD = new DocRefField(PipelineDoc.DOCUMENT_TYPE,
            "Reference Loader Pipeline", true, SUPPORTED_DOC_REF_CONDITIONS);
    private static final AbstractField PROCESSING_STATE_FIELD = new TextField(
            "Processing State", false);
    private static final AbstractField STREAM_ID_FIELD = new IdField(
            "Stream ID", false);
    private static final AbstractField STREAM_NO_FIELD = new LongField(
            "Sub-Stream Number", false);
    private static final AbstractField PIPELINE_VERSION_FIELD = new TextField(
            "Pipeline Version", false);

    private static final List<AbstractField> FIELDS = List.of(
            KEY_FIELD,
            VALUE_FIELD,
            MAP_NAME_FIELD,
            CREATE_TIME_FIELD,
            EFFECTIVE_TIME_FIELD,
            LAST_ACCESSED_TIME_FIELD,
            PIPELINE_FIELD,
            PROCESSING_STATE_FIELD,
            STREAM_ID_FIELD,
            STREAM_NO_FIELD,
            PIPELINE_VERSION_FIELD);

    private static final DataSource DATA_SOURCE = new DataSource(FIELDS);

    private static final Map<String, AbstractField> FIELD_NAME_TO_FIELD_MAP = FIELDS.stream()
            .collect(Collectors.toMap(AbstractField::getName, Function.identity()));

//    private static final Map<String, Function<RefStoreEntry, Val>> FIELD_TO_VAL_EXTRACTOR_MAP = Map.ofEntries(
//            Map.entry(KEY_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getKey())),
//            Map.entry(VALUE_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getValue())),
//            Map.entry(MAP_NAME_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getMapDefinition().getMapName())),
//            Map.entry(CREATE_TIME_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getRefDataProcessingInfo().getCreateTimeEpochMs())),
//            Map.entry(EFFECTIVE_TIME_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getRefDataProcessingInfo().getEffectiveTimeEpochMs())),
//            Map.entry(LAST_ACCESSED_TIME_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getRefDataProcessingInfo().getLastAccessedTimeEpochMs())),
//            Map.entry(PIPELINE_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineDocRef().toInfoString())),
//            Map.entry(PROCESSING_STATE_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getRefDataProcessingInfo().getProcessingState().getDisplayName())),
//            Map.entry(STREAM_ID_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamId())),
//            Map.entry(STREAM_NO_FIELD.getName(), refStoreEntry ->
//                    ValLong.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamNo())),
//            Map.entry(PIPELINE_VERSION_FIELD.getName(), refStoreEntry ->
//                    ValString.create(refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineVersion())));

    private static final Map<String, Function<RefStoreEntry, Object>> FIELD_TO_EXTRACTOR_MAP = Map.ofEntries(
            Map.entry(KEY_FIELD.getName(),
                    RefStoreEntry::getKey),
            Map.entry(VALUE_FIELD.getName(),
                    RefStoreEntry::getValue),
            Map.entry(MAP_NAME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getMapName()),
            Map.entry(CREATE_TIME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getCreateTimeEpochMs()),
            Map.entry(EFFECTIVE_TIME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getEffectiveTimeEpochMs()),
            Map.entry(LAST_ACCESSED_TIME_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getLastAccessedTimeEpochMs()),
            Map.entry(PIPELINE_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineDocRef()),
            Map.entry(PROCESSING_STATE_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getRefDataProcessingInfo().getProcessingState().getDisplayName()),
            Map.entry(STREAM_ID_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamId()),
            Map.entry(STREAM_NO_FIELD.getName(), refStoreEntry ->
                    refStoreEntry.getMapDefinition().getRefStreamDefinition().getStreamNo()),
            Map.entry(PIPELINE_VERSION_FIELD.getName(), refStoreEntry ->
                   refStoreEntry.getMapDefinition().getRefStreamDefinition().getPipelineVersion()));

    private final RefDataStore refDataStore;
    private final SecurityContext securityContext;

    @Inject
    public ReferenceDataServiceImpl(final RefDataStoreFactory refDataStoreFactory,
                                    final SecurityContext securityContext) {
        this.refDataStore = refDataStoreFactory.getOffHeapStore();
        this.securityContext = securityContext;
    }

    @Override
    public List<RefStoreEntry> entries(final int limit) {
        return withPermissionCheck(() -> {
            final List<RefStoreEntry> entries;
            try {
                entries = refDataStore.list(limit);
            } catch (Exception e) {
                LOGGER.error("Error listing reference data",e);
                throw e;
            }
            return entries;
        });
    }

    private <T> T withPermissionCheck(final Supplier<T> supplier) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            return supplier.get();
        } else {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to view reference data");
        }
    }

    private void withPermissionCheck(final Runnable runnable) {
        // TODO @AT Need some kind of fine grained doc permission check on the pipe associated with each entry
        //   but this will do for a first stab
        if (securityContext.isAdmin()) {
            runnable.run();
        } else {
            throw new PermissionException(securityContext.getUserId(), "You do not have permission to view reference data");
        }
    }

    @Override
    public DocRef getDocRef() {
        return REF_STORE_PSEUDO_DOC_REF;
    }

    @Override
    public DataSource getDataSource() {
        return DATA_SOURCE;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final AbstractField[] fields,
                       final Consumer<Val[]> consumer) {

        withPermissionCheck(() -> {
            LOGGER.logDurationIfInfoEnabled(
                    () -> doSearch(criteria, fields, consumer),
                    "Querying ref store");
        });
    }

    private void doSearch(final ExpressionCriteria criteria,
                          final AbstractField[] fields,
                          final Consumer<Val[]> consumer) {
        // TODO @AT This is a temporary very crude impl to see if it works.
        //  The search code ought to be pushed down to the offHeapStore so it can query the many DBs
        //  selectively and in a MUCH more efficient way, e.g. using start/stop keys on the kv store scan.

        // TODO @AT Do we need to do something with param replacement? e.g.:
        //     final Map<String, String> paramMap = ExpressionParamUtil.createParamMap(query.getParams());
        //     expression = ExpressionUtil.replaceExpressionParameters(expression, paramMap);
        //   But we don't have a paramMap.

        // TODO @AT Need to check if we have any common code for this sort of ExpOp, ExpTerm, Val munging

        // TODO @AT Need to run the query as a task so it can be monitored from the UI.

        final List<RefStoreEntry> entries = entries(10_000);

        final Predicate<RefStoreEntry> predicate = buildEntryPredicate(criteria);

        try {
            entries.stream()
                    .filter(predicate)
                    .forEach(refStoreEntry -> {
                        final Val[] valArr = new Val[fields.length];

                        for (int i = 0; i < fields.length; i++) {
                            final Object value = FIELD_TO_EXTRACTOR_MAP.get(fields[i].getName())
                                    .apply(refStoreEntry);
                            valArr[i] = convertToVal(value, fields[i]);;
                        }
                        consumer.accept(valArr);
                    });
        } catch (Exception e) {
            LOGGER.error("Error querying entry list", e);
            throw e;
        }
    }


    private Predicate<RefStoreEntry> buildEntryPredicate(final ExpressionCriteria expressionCriteria) {
        try {
            if (expressionCriteria != null) {
                return convertExpressionOperator(expressionCriteria.getExpression());
            } else {
                return refStoreEntry -> true;
            }
        } catch (Exception e) {
            LOGGER.error("Error building predicate for {}", expressionCriteria, e);
            throw e;
        }
    }

    private Predicate<RefStoreEntry> convertExpressionItem(final ExpressionItem expressionItem) {
        if (expressionItem != null && expressionItem.enabled()) {
            if (expressionItem instanceof ExpressionOperator) {
                return convertExpressionOperator((ExpressionOperator) expressionItem);
            } else if (expressionItem instanceof ExpressionTerm) {
                return convertExpressionTerm((ExpressionTerm) expressionItem);
            } else {
                throw new RuntimeException("Unknown class " + expressionItem.getClass().getName());
            }
        } else {
            return refStoreEntry -> true;
        }
    }

    private Predicate<RefStoreEntry> convertExpressionOperator(final ExpressionOperator expressionOperator) {
        // Stroom allows NOT {} expressions to have more than one child and so
        // NOT { x=1, y=1 }
        // is treated like an implicit AND, i.e.
        // AND { NOT {x=1}, NOT {y=1}

        if (expressionOperator.getChildren() != null) {
            Predicate<RefStoreEntry> predicate = null;
            for (final ExpressionItem child : expressionOperator.getChildren()) {
                final Predicate<RefStoreEntry> childPredicate = convertExpressionItem(child);
                if (predicate == null) {
                    if (expressionOperator.op().equals(Op.NOT)) {
                        predicate = childPredicate.negate();
                    } else {
                        predicate = childPredicate;
                    }
                } else {
                    if (expressionOperator.op().equals(Op.AND)) {
                        predicate = predicate.and(childPredicate);
                    } else if (expressionOperator.op().equals(Op.OR)) {
                        predicate = predicate.or(childPredicate);
                    } else if (expressionOperator.op().equals(Op.NOT)) {
                        predicate = predicate.and(childPredicate.negate());
                    } else {
                        throw new RuntimeException("Unknown op " + expressionOperator.op());
                    }
                }
            }
            return predicate != null
                    ? predicate
                    : refStoreEntry -> true;
        } else {
            return refStoreEntry -> true;
        }
    }

    private Predicate<RefStoreEntry> convertExpressionTerm(final ExpressionTerm expressionTerm) {

        // name => field
        // field => fieldType
        AbstractField abstractField = FIELD_NAME_TO_FIELD_MAP.get(expressionTerm.getField());

        if (abstractField.getType().equals(FieldTypes.TEXT)) {
            return buildTextFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (String) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.LONG)) {
            return buildLongFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.DATE)) {
            return buildDateFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (Long) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else if (abstractField.getType().equals(FieldTypes.DOC_REF)) {
            return buildDocRefFieldPredicate(
                    expressionTerm,
                    refStoreEntry -> (DocRef) FIELD_TO_EXTRACTOR_MAP.get(expressionTerm.getField()).apply(refStoreEntry));
        } else {
            throw new RuntimeException("Unsupported term " + expressionTerm);
        }

//        if (expressionTerm.getField().equals(KEY_FIELD.getName())) {
//            // TODO @AT Implement
//            return buildTextFieldPredicate(expressionTerm, )
//        } else {
//            // TODO @AT Implement
//            return refStoreEntry -> true;
//        }
    }

    private Predicate<RefStoreEntry> buildTextFieldPredicate(final ExpressionTerm expressionTerm,
                                                                             final Function<RefStoreEntry, String> valueExtractor) {
        // TODO @AT Handle wildcarding in term
        if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> Objects.equals(expressionTerm.getValue(), valueExtractor.apply(rec));
        } else if (expressionTerm.getCondition().equals(Condition.CONTAINS)) {
            return rec -> valueExtractor.apply(rec).contains(expressionTerm.getValue());
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildLongFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        final Long termValue = Long.valueOf(expressionTerm.getValue());
        if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> Objects.equals(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN)) {
            return rec -> valueExtractor.apply(rec) > termValue;
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) >= termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN)) {
            return rec -> valueExtractor.apply(rec) < termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) <= termValue;
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildDateFieldPredicate(final ExpressionTerm expressionTerm,
                                                             final Function<RefStoreEntry, Long> valueExtractor) {
        // TODO @AT Handle stuff like 'today() -1d'
        // TODO @AT Need to get now() once for the query
        final Long termValue = getDate(expressionTerm.getField(), expressionTerm.getValue(), Instant.now().toEpochMilli());
        if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> Objects.equals(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN)) {
            return rec -> valueExtractor.apply(rec) > termValue;
        } else if (expressionTerm.getCondition().equals(Condition.GREATER_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) >= termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN)) {
            return rec -> valueExtractor.apply(rec) < termValue;
        } else if (expressionTerm.getCondition().equals(Condition.LESS_THAN_OR_EQUAL_TO)) {
            return rec -> valueExtractor.apply(rec) <= termValue;
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private Predicate<RefStoreEntry> buildDocRefFieldPredicate(final ExpressionTerm expressionTerm,
                                                               final Function<RefStoreEntry, DocRef> valueExtractor) {
        final DocRef termValue = new DocRef.Builder()
                .uuid(expressionTerm.getValue())
                .build();
        if (expressionTerm.getCondition().equals(Condition.IS_DOC_REF)) {
            return rec -> docRefsEqualOnUuid(valueExtractor.apply(rec), termValue);
        } else if (expressionTerm.getCondition().equals(Condition.EQUALS)) {
            return rec -> docRefsEqualOnName(valueExtractor.apply(rec), termValue);
        } else {
            throw new RuntimeException("Unexpected condition " + expressionTerm.getCondition());
        }
    }

    private boolean docRefsEqualOnUuid(final DocRef docRef1, final DocRef docRef2) {
        if (docRef1 == null && docRef2 == null) {
            return false;
        } if (docRef1 == null) {
            return false;
        } if (docRef2 == null) {
            return false;
        } else {
            return Objects.equals(docRef1.getUuid(), docRef2.getUuid());
        }
    }

    private boolean docRefsEqualOnName(final DocRef docRef1, final DocRef docRef2) {
        if (docRef1 == null && docRef2 == null) {
            return false;
        } if (docRef1 == null) {
            return false;
        } if (docRef2 == null) {
            return false;
        } else {
            return Objects.equals(docRef1.getName(), docRef2.getName());
        }
    }

    private Val convertToVal(final Object object, final AbstractField field) {
        switch (field.getType()) {
            case FieldTypes.TEXT:
                return ValString.create((String) object);
            case FieldTypes.LONG:
            case FieldTypes.ID:
            case FieldTypes.DATE:
                return ValLong.create((long) object);
            case FieldTypes.DOC_REF:
                return ValString.create(((DocRef) object).toInfoString());
            default:
                throw new RuntimeException("Unexpected field type " + field.getType());
        }
    }

    private long getDate(final String fieldName, final String value, final long nowEpochMs) {
        try {
            // TODO @AT Get the timezone from the user's local?
            return DateExpressionParser.parse(value, ZoneOffset.UTC.getId(), nowEpochMs)
                    .map(dt -> dt.toInstant().toEpochMilli())
                    .orElseThrow(() -> new RuntimeException("Expected a standard date value for field \"" + fieldName
                            + "\" but was given string \"" + value + "\""));
        } catch (final RuntimeException e) {
            throw new RuntimeException("Expected a standard date value for field \"" + fieldName
                    + "\" but was given string \"" + value + "\"");
        }
    }

}
