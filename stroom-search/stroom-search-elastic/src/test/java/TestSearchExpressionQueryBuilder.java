import stroom.query.api.v2.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TestSearchExpressionQueryBuilder {

    private final long nowEpochMillis = System.currentTimeMillis();
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
                DateTimeSettings.builder().build(),
                nowEpochMillis
        );
    }

    @Test
    public void testBuildQuery() {
        ElasticIndexField answerField = new ElasticIndexField();
        answerField.setFieldName("answer");
        answerField.setFieldUse(ElasticIndexFieldType.LONG);
        indexFieldsMap.put(answerField.getFieldName(), answerField);
        final Long answerFieldValue = 42L;

        // Single numeric EQUALS condition contained within the default AND clause

        expressionBuilder.addTerm(answerField.getFieldName(), Condition.EQUALS, answerFieldValue.toString());
        QueryBuilder queryBuilder = builder.buildQuery(expressionBuilder.build());

        Assertions.assertTrue(queryBuilder instanceof BoolQueryBuilder, "Is a `bool` query");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) queryBuilder;
        Assertions.assertEquals(1, boolQuery.must().size(), "Bool query contains exactly one item");

        TermQueryBuilder termQuery = (TermQueryBuilder) boolQuery.must().get(0);
        Assertions.assertEquals(answerField.getFieldName(), termQuery.fieldName(), "Field name is correct");
        Assertions.assertEquals(answerFieldValue, termQuery.value(), "Query value is correct");

        // Add a second text EQUALS condition

        ElasticIndexField nameField = new ElasticIndexField();
        nameField.setFieldName("name");
        nameField.setFieldUse(ElasticIndexFieldType.TEXT);
        indexFieldsMap.put(nameField.getFieldName(), nameField);
        final String nameFieldValue = "one,two, three";

        expressionBuilder.addTerm(nameField.getFieldName(), Condition.CONTAINS, nameFieldValue);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = (BoolQueryBuilder) queryBuilder;
        Assertions.assertEquals(2, boolQuery.must().size(), "Bool query contains exactly two items");
        // Inner bool query, with a `MUST` condition for each term in the list
        BoolQueryBuilder innerBoolQuery = (BoolQueryBuilder) boolQuery.must().get(1);
        Assertions.assertEquals(3, innerBoolQuery.must().size(), "Inner bool query contains exactly three items");

        TermQueryBuilder firstTermQuery = (TermQueryBuilder) innerBoolQuery.must().get(0);
        Assertions.assertEquals(nameField.getFieldName(), firstTermQuery.fieldName(),
                "Field name of first term query is correct");
        Assertions.assertEquals("one", firstTermQuery.value(), "Field value of first term query is correct");

        // Add a nested NOT GREATER THAN date condition

        ElasticIndexField dateField = new ElasticIndexField();
        dateField.setFieldName("date");
        dateField.setFieldUse(ElasticIndexFieldType.DATE);
        indexFieldsMap.put(dateField.getFieldName(), dateField);
        final String nowStr = "2021-02-17T01:23:34.000";
        final long expectedParsedDateFieldValue = 1613525014000L;

        // Parse the date/time. Must specify UTC for `timeZoneId`, otherwise the local system timezone will be used
        final Optional<ZonedDateTime> expectedDate = DateExpressionParser.parse(
                nowStr,
                nowEpochMillis);
        Assertions.assertTrue(expectedDate.isPresent(), "Date was parsed");
        final long dateFieldValue = expectedDate.get().toInstant().toEpochMilli();
        Assertions.assertEquals(expectedParsedDateFieldValue, dateFieldValue, "Parsed date value is correct");

        ExpressionOperator notOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(dateField.getFieldName(), Condition.GREATER_THAN, nowStr)
                .build();

        expressionBuilder.addOperator(notOperator);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = (BoolQueryBuilder) queryBuilder;
        Assertions.assertEquals(3, boolQuery.must().size(), "Bool query contains exactly three items");
        innerBoolQuery = (BoolQueryBuilder) boolQuery.must().get(2);
        Assertions.assertEquals(1, innerBoolQuery.mustNot().size(), "Inner bool query contains one item");

        RangeQueryBuilder firstRangeQuery = (RangeQueryBuilder) innerBoolQuery.mustNot().get(0);
        Assertions.assertEquals(dateField.getFieldName(), firstRangeQuery.fieldName(),
                "Field name of first range query is correct");
        Assertions.assertEquals(expectedParsedDateFieldValue, firstRangeQuery.from(),
                "Field value of first range query is correct");
    }
}
