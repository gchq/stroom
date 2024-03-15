import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;

import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestSearchExpressionQueryBuilder {

    private ExpressionOperator.Builder expressionBuilder;
    private Map<String, ElasticIndexField> indexFieldsMap;
    private SearchExpressionQueryBuilder builder;

    @BeforeEach
    public void init() {
        expressionBuilder = ExpressionOperator.builder().op(Op.AND);
        indexFieldsMap = new HashMap<>();

        builder = new SearchExpressionQueryBuilder(
                null,
                indexFieldsMap,
                DateTimeSettings.builder().build()
        );
    }

    @Test
    public void testBuildQuery() {
        ElasticIndexField answerField = new ElasticIndexField();
        answerField.setFieldName("answer");
        answerField.setFieldType(Kind.Long.jsonValue());
        answerField.setFieldUse(ElasticIndexFieldType.LONG);
        indexFieldsMap.put(answerField.getFieldName(), answerField);
        final long answerFieldValue = 42L;

        // Single numeric EQUALS condition contained within the default AND clause

        expressionBuilder.addTerm(answerField.getFieldName(), Condition.EQUALS, Long.toString(answerFieldValue));
        Query queryBuilder = builder.buildQuery(expressionBuilder.build());

        Assertions.assertTrue(queryBuilder.isBool(), "Is a `bool` query");
        BoolQuery boolQuery = queryBuilder.bool();
        Assertions.assertEquals(1, boolQuery.must().size(), "Bool query contains exactly one item");

        TermQuery termQuery = boolQuery.must().getFirst().term();
        Assertions.assertEquals(answerField.getFieldName(), termQuery.field(), "Field name is correct");
        Assertions.assertEquals(answerFieldValue, termQuery.value().longValue(), "Query value is correct");

        // Add a second text EQUALS condition

        ElasticIndexField nameField = new ElasticIndexField();
        nameField.setFieldName("name");
        nameField.setFieldType(Kind.Text.jsonValue());
        nameField.setFieldUse(ElasticIndexFieldType.TEXT);
        indexFieldsMap.put(nameField.getFieldName(), nameField);

        // Add a nested NOT GREATER THAN date condition

        ElasticIndexField dateField = new ElasticIndexField();
        dateField.setFieldName("date");
        dateField.setFieldType(Kind.Date.jsonValue());
        dateField.setFieldUse(ElasticIndexFieldType.DATE);
        indexFieldsMap.put(dateField.getFieldName(), dateField);
        final String nowStr = "2021-02-17T01:23:34.000";
        final long expectedParsedDateFieldValue = 1613525014000L;

        // Parse the date/time. Must specify UTC for `timeZoneId`, otherwise the local system timezone will be used
        final Optional<ZonedDateTime> expectedDate = DateExpressionParser.parse(nowStr);
        Assertions.assertTrue(expectedDate.isPresent(), "Date was parsed");
        final long dateFieldValue = expectedDate.get().toInstant().toEpochMilli();
        Assertions.assertEquals(expectedParsedDateFieldValue, dateFieldValue, "Parsed date value is correct");

        ExpressionOperator notOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(dateField.getFieldName(), Condition.GREATER_THAN, nowStr)
                .build();

        expressionBuilder.addOperator(notOperator);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = queryBuilder.bool();
        Assertions.assertEquals(2, boolQuery.must().size(), "Bool query contains exactly two items");
        BoolQuery innerBoolQuery = boolQuery.must().get(1).bool();
        Assertions.assertEquals(1, innerBoolQuery.mustNot().size(),
                "Inner bool query contains one item");

        RangeQuery firstRangeQuery = innerBoolQuery.mustNot().getFirst().range();
        Assertions.assertEquals(dateField.getFieldName(), firstRangeQuery.field(),
                "Field name of first range query is correct");
    }
}
