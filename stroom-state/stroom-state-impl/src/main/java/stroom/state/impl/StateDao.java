package stroom.state.impl;

import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.QueryField;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Val;
import stroom.query.language.functions.ValDate;
import stroom.query.language.functions.ValLong;
import stroom.query.language.functions.ValNull;
import stroom.query.language.functions.ValString;
import stroom.query.language.functions.ValuesConsumer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.xml.XMLUtil;

import com.datastax.oss.driver.api.core.CqlIdentifier;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatementBuilder;
import com.datastax.oss.driver.api.core.cql.BatchType;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.metadata.schema.ClusteringOrder;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.Literal;
import com.datastax.oss.driver.api.querybuilder.relation.ColumnRelationBuilder;
import com.datastax.oss.driver.api.querybuilder.relation.Relation;
import com.datastax.oss.driver.api.querybuilder.term.Term;
import com.datastax.oss.driver.internal.querybuilder.schema.compaction.DefaultTimeWindowCompactionStrategy;
import com.esotericsoftware.kryo.io.ByteBufferInputStream;
import com.sun.xml.fastinfoset.sax.SAXDocumentParser;
import org.xml.sax.InputSource;

import java.io.StringWriter;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.createTable;
import static com.datastax.oss.driver.api.querybuilder.SchemaBuilder.dropTable;


public class StateDao {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StateDao.class);

    private static final int MAX_BATCH_STATEMENTS = 65535;

    private static final CqlIdentifier TABLE = CqlIdentifier.fromCql("state");
    private static final CqlIdentifier COLUMN_MAP = CqlIdentifier.fromCql("map");
    private static final CqlIdentifier COLUMN_KEY = CqlIdentifier.fromCql("key");
    private static final CqlIdentifier COLUMN_EFFECTIVE_TIME = CqlIdentifier.fromCql("effective_time");
    private static final CqlIdentifier COLUMN_TYPE_ID = CqlIdentifier.fromCql("type_Id");
    private static final CqlIdentifier COLUMN_VALUE = CqlIdentifier.fromCql("value");
    private static final SimpleStatement CREATE_TABLE = createTable(TABLE)
            .ifNotExists()
            .withPartitionKey(COLUMN_MAP, DataTypes.TEXT)
            .withPartitionKey(COLUMN_KEY, DataTypes.TEXT)
            .withClusteringColumn(COLUMN_EFFECTIVE_TIME, DataTypes.TIMESTAMP)
            .withColumn(COLUMN_TYPE_ID, DataTypes.TINYINT)
            .withColumn(COLUMN_VALUE, DataTypes.BLOB)
            .withClusteringOrder(COLUMN_EFFECTIVE_TIME, ClusteringOrder.DESC)
            .withCompaction(new DefaultTimeWindowCompactionStrategy())
            .build();
    private static final SimpleStatement DROP_TABLE = dropTable(TABLE)
            .ifExists()
            .build();

    private static final SimpleStatement INSERT = insertInto(TABLE)
            .value(COLUMN_MAP, bindMarker())
            .value(COLUMN_KEY, bindMarker())
            .value(COLUMN_EFFECTIVE_TIME, bindMarker())
            .value(COLUMN_TYPE_ID, bindMarker())
            .value(COLUMN_VALUE, bindMarker())
            .build();

    private static final SimpleStatement SELECT = selectFrom(TABLE)
            .column(COLUMN_EFFECTIVE_TIME)
            .column(COLUMN_TYPE_ID)
            .column(COLUMN_VALUE)
            .whereColumn(COLUMN_MAP).isEqualTo(bindMarker())
            .whereColumn(COLUMN_KEY).isEqualTo(bindMarker())
            .whereColumn(COLUMN_EFFECTIVE_TIME).isLessThanOrEqualTo(bindMarker())
            .limit(1)
            .allowFiltering()
            .build();

    private static final Map<String, CqlIdentifier> FIELD_MAP = Map.of(
            StateFields.MAP, COLUMN_MAP,
            StateFields.KEY, COLUMN_KEY,
            StateFields.EFFECTIVE_TIME, COLUMN_EFFECTIVE_TIME,
            StateFields.VALUE_TYPE, COLUMN_TYPE_ID,
            StateFields.VALUE, COLUMN_VALUE);

    public static void createTables(final CqlSession session) {
        LOGGER.info("Creating tables...");
        LOGGER.logDurationIfInfoEnabled(() -> {
            session.execute(CREATE_TABLE);
        }, "createTables()");
    }

    public static void dropTables(final CqlSession session) {
        session.execute(DROP_TABLE);
    }

    public static void insert(final CqlSession session,
                              final List<State> states) {
        Objects.requireNonNull(states, "Null states list");

//        final SimpleStatement simpleStatement = insertInto("state")
//                .value("map", bindMarker())
//                .value("key", bindMarker())
//                .value("effective_time", bindMarker())
//                .value("type_id", bindMarker())
//                .value("value", bindMarker())
//                .build();

//        final String cql = """
//                INSERT INTO state (map, key, effective_time, type_id, value)
//                VALUES (?, ?, ?, ?, ?)
//                """;
//        final PreparedStatement statement = session.prepare(cql);

        final PreparedStatement preparedStatement = session.prepare(INSERT);
        BatchStatementBuilder builder = new BatchStatementBuilder(BatchType.UNLOGGED);

        int statementCount = 0;
        for (final State state : states) {
            builder = builder.addStatement(preparedStatement.bind(
                    state.map(),
                    state.key(),
                    state.effectiveTime(),
                    state.typeId(),
                    state.value()));
            statementCount++;

            if (statementCount >= MAX_BATCH_STATEMENTS) {
                session.execute(builder.build());
                builder.clearStatements();
                statementCount = 0;
            }
        }

        session.execute(builder.build());
        builder.clearStatements();
    }

    public static Optional<State> getState(final CqlSession session, final StateRequest request) {

//        final String cql1 = """
//                SELECT effective_time, value
//                FROM state
//                WHERE map = ?
//                AND key = ?
//                ALLOW FILTERING
//                """;
//
//        final PreparedStatement prepared1 = session.prepare(cql1);
//        final BoundStatement bound1 = prepared1.bind("TEST_MAP", "TEST_KEY");
//        session.execute(bound1).forEach(row -> {
//            final Instant effectiveTime = row.getInstant("effective_time");
//            final String value = row.getString("value");
//
//            LOGGER.info("Effective Time: {}, Value: {}", effectiveTime, value);
//        });
//
//
//        final String cql = """
//                SELECT effective_time, type_id, value
//                FROM state
//                WHERE map = ?
//                AND key = ?
//                AND effective_time <= ?
//                LIMIT 1
//                ALLOW FILTERING
//                """;


//        final SimpleStatement simpleStatement =
//                selectFrom("state")
//                .column("effective_time")
//                .column("type_id")
//                .column("value")
//                .whereColumn("map").isEqualTo(bindMarker())
//                .whereColumn("key").isEqualTo(bindMarker())
//                .whereColumn("effective_time").isLessThanOrEqualTo(bindMarker())
//                .limit(1)
//                .allowFiltering()
//                .build();


//                .value("key", bindMarker())
//                .value("effective_time", bindMarker())
//                .value("type_id", bindMarker())
//                .value("value", bindMarker())
//                .build();

        //SORT BY effective_time DESC

//        final PreparedStatement preparedStatement = session.prepare(cql);

        final PreparedStatement preparedStatement = session.prepare(SELECT);
        final BoundStatement bound = preparedStatement.bind(request.map(), request.key(), request.effectiveTime());
        return Optional
                .ofNullable(session.execute(bound).one())
                .map(row -> new State(
                        request.map(),
                        request.key(),
                        row.getInstant(0),
                        row.getByte(1),
                        row.getByteBuffer(2)));
    }

    private static void getRelations(final ExpressionOperator expressionOperator,
                                     final List<Relation> relations) {
        if (expressionOperator.enabled() &&
                expressionOperator.getChildren() != null &&
                !expressionOperator.getChildren().isEmpty()) {
            switch (expressionOperator.op()) {
                case AND -> expressionOperator.getChildren().forEach(child -> {
                    if (child instanceof final ExpressionTerm expressionTerm) {
                        if (expressionTerm.enabled()) {
                            try {
                                final Relation relation = convertTerm(expressionTerm);
                                relations.add(relation);
                            } catch (final RuntimeException e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        }
                    } else if (child instanceof final ExpressionOperator operator) {
                        getRelations(operator, relations);
                    }
                });
                case OR -> throw new RuntimeException("OR conditions are not supported");
                case NOT -> throw new RuntimeException("NOT conditions are not supported");
            }
        }
    }

    private static Relation convertTerm(final ExpressionTerm term) {
        final CqlIdentifier column = FIELD_MAP.get(term.getField());
        if (column == null) {
            throw new RuntimeException("Unexpected field " + term.getField());
        }

        final QueryField queryField = StateFields.FIELD_MAP.get(term.getField());
        final ColumnRelationBuilder<Relation> builder = Relation.column(column);
        return switch (term.getCondition()) {
            case EQUALS -> builder.isEqualTo(convertLiteral(queryField, term.getValue()));
            case CONTAINS -> builder.isEqualTo(convertLiteral(queryField, term.getValue()));
            case NOT_EQUALS -> builder.isNotEqualTo(convertLiteral(queryField, term.getValue()));
            case LESS_THAN -> builder.isLessThan(convertLiteral(queryField, term.getValue()));
            case LESS_THAN_OR_EQUAL_TO -> builder.isLessThanOrEqualTo(convertLiteral(queryField, term.getValue()));
            case GREATER_THAN -> builder.isGreaterThan(convertLiteral(queryField, term.getValue()));
            case GREATER_THAN_OR_EQUAL_TO ->
                    builder.isGreaterThanOrEqualTo(convertLiteral(queryField, term.getValue()));
            case IN -> {
                Term[] terms = new Term[0];
                if (term.getValue() != null) {
                    final String[] values = term.getValue().split(",");
                    terms = new Term[values.length];
                    for (int i = 0; i < values.length; i++) {
                        terms[i] = convertLiteral(queryField, values[i]);
                    }
                }
                yield builder.in(terms);
            }
            default -> throw new RuntimeException("Condition " + term.getCondition() + " is not supported.");
        };
    }

    private static Literal convertLiteral(final QueryField queryField, final String value) {
        switch (queryField.getFldType()) {
            case ID -> {
                return literal(Long.parseLong(value));
            }
            case BOOLEAN -> {
                return literal(Boolean.parseBoolean(value));
            }
            case INTEGER -> {
                return literal(Integer.parseInt(value));
            }
            case LONG -> {
                return literal(Long.parseLong(value));
            }
            case FLOAT -> {
                return literal(Float.parseFloat(value));
            }
            case DOUBLE -> {
                return literal(Double.parseDouble(value));
            }
            case DATE -> {
                return literal(Instant.parse(value));
            }
            case TEXT -> {
                return literal(value);
            }
            case KEYWORD -> {
                return literal(value);
            }
            case IPV4_ADDRESS -> {
                return literal(Long.parseLong(value));
            }
            case DOC_REF -> {
//                return literal(Long.parseLong(value));
            }
        }
        throw new RuntimeException("Unable to convert literal: " + queryField.getFldType());
    }

    public static void search(final CqlSession session,
                              final ExpressionCriteria criteria,
                              final FieldIndex fieldIndex,
                              final ValuesConsumer consumer) {
        final List<Relation> relations = new ArrayList<>();
        getRelations(criteria.getExpression(), relations);
        final String[] fieldNames = fieldIndex.getFields();
        final FieldType[] fieldTypes = new FieldType[fieldNames.length];
        final List<CqlIdentifier> columns = new ArrayList<>();
        int columnPos = 0;
        final int[] columnPositions = new int[fieldNames.length];
        int valueTypePosition = -1;
        int valuePosition = -1;

        for (int i = 0; i < fieldNames.length; i++) {
            final String fieldName = fieldNames[i];
            final QueryField queryField = StateFields.FIELD_MAP.get(fieldName);
            if (queryField != null) {
                fieldTypes[i] = queryField.getFldType();

                final CqlIdentifier column = FIELD_MAP.get(fieldName);
                columns.add(column);
                columnPositions[i] = columnPos;

                if (valueTypePosition == -1 && StateFields.VALUE_TYPE.equals(fieldName)) {
                    valueTypePosition = columnPos;
                }
                if (valuePosition == -1 && StateFields.VALUE.equals(fieldName)) {
                    valuePosition = columnPos;
                }

                columnPos++;
            }
        }

        // Add the value type and record the value type position if it is needed.
        if (valuePosition != -1 && valueTypePosition == -1) {
            columns.add(FIELD_MAP.get(StateFields.VALUE_TYPE));
            valueTypePosition = columnPos;
        }
        final int vtp = valueTypePosition;

        final SimpleStatement SELECT = selectFrom(TABLE)
                .columns(columns.toArray(new CqlIdentifier[0]))
                .where(relations)
                .allowFiltering()
                .build();
        session.execute(SELECT).forEach(row -> {
            final Val[] values = new Val[fieldNames.length];
            for (int i = 0; i < values.length; i++) {
                final String fieldName = fieldNames[i];
                final int columnPosition = columnPositions[i];

                switch (fieldName) {
                    case StateFields.MAP -> values[i] = ValString.create(row.getString(columnPosition));
                    case StateFields.KEY -> values[i] = ValString.create(row.getString(columnPosition));
                    case StateFields.KEY_START -> values[i] = ValLong.create(row.getLong(columnPosition));
                    case StateFields.KEY_END -> values[i] = ValLong.create(row.getLong(columnPosition));
                    case StateFields.EFFECTIVE_TIME -> values[i] = ValDate.create(row.getInstant(columnPosition));
                    case StateFields.VALUE_TYPE -> {
                        final byte valueType = row.getByte(columnPosition);
                        switch (valueType) {
                            case stroom.pipeline.refdata.store.StringValue.TYPE_ID ->
                                    values[i] = ValString.create("String");
                            case stroom.pipeline.refdata.store.FastInfosetValue.TYPE_ID ->
                                    values[i] = ValString.create("Fast Infoset");
                            default -> values[i] = ValNull.INSTANCE;
                        }
                    }
                    case StateFields.VALUE -> {
                        final byte valueType = row.getByte(vtp);
                        switch (valueType) {
                            case stroom.pipeline.refdata.store.StringValue.TYPE_ID ->
                                    values[i] = ValString.create(convertString(row.getByteBuffer(columnPosition)));
                            case stroom.pipeline.refdata.store.FastInfosetValue.TYPE_ID ->
                                    values[i] = ValString.create(convertFastInfoset(row.getByteBuffer(columnPosition)));
                            default -> values[i] = ValNull.INSTANCE;
                        }
                    }
                    default -> values[i] = ValNull.INSTANCE;
                }
            }
            consumer.accept(Val.of(values));
        });
    }

    private static String convertString(final ByteBuffer byteBuffer) {
        return new String(byteBuffer.array(), StandardCharsets.UTF_8);
    }

    private static String convertFastInfoset(final ByteBuffer byteBuffer) {
        try {
            final Writer writer = new StringWriter(1000);
            final SAXDocumentParser parser = new SAXDocumentParser();
            XMLUtil.prettyPrintXML(parser, new InputSource(new ByteBufferInputStream(byteBuffer)), writer);
            return writer.toString();

        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}
