import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.util.test.StroomJUnit4ClassRunner;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSearchExpressionQueryBuilder {
    private final long nowEpochMillis = System.currentTimeMillis();
    private ExpressionOperator.Builder expressionBuilder;
    private Map<String, ElasticIndexField> indexFieldsMap;
    private SearchExpressionQueryBuilder builder;

    @Before
    public void init() {
        expressionBuilder = new Builder();
        indexFieldsMap = new HashMap<>();

        builder = new SearchExpressionQueryBuilder(
                null,
                indexFieldsMap,
                "UTC",
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

        Assert.assertTrue("Is a `bool` query", queryBuilder instanceof BoolQueryBuilder);
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) queryBuilder;
        Assert.assertEquals("Bool query contains exactly one item", 1, boolQuery.must().size());

        TermQueryBuilder termQuery = (TermQueryBuilder) boolQuery.must().get(0);
        Assert.assertEquals("Field name is correct", answerField.getFieldName(), termQuery.fieldName());
        Assert.assertEquals("Query value is correct", answerFieldValue, termQuery.value());

        // Add a second text EQUALS condition

        ElasticIndexField nameField = new ElasticIndexField();
        nameField.setFieldName("name");
        nameField.setFieldUse(ElasticIndexFieldType.TEXT);
        indexFieldsMap.put(nameField.getFieldName(), nameField);
        final String nameFieldValue = "one,two, three";

        expressionBuilder.addTerm(nameField.getFieldName(), Condition.CONTAINS, nameFieldValue);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = (BoolQueryBuilder)queryBuilder;
        Assert.assertEquals("Bool query contains exactly two items", 2, boolQuery.must().size());
        // Inner bool query, with a `MUST` condition for each term in the list
        BoolQueryBuilder innerBoolQuery = (BoolQueryBuilder) boolQuery.must().get(1);
        Assert.assertEquals("Inner bool query contains exactly three items", 3, innerBoolQuery.must().size());

        TermQueryBuilder firstTermQuery = (TermQueryBuilder) innerBoolQuery.must().get(0);
        Assert.assertEquals("Field name of first term query is correct", nameField.getFieldName(), firstTermQuery.fieldName());
        Assert.assertEquals("Field value of first term query is correct", "one", firstTermQuery.value());

        // Add a nested NOT GREATER THAN date condition

        ElasticIndexField dateField = new ElasticIndexField();
        dateField.setFieldName("date");
        dateField.setFieldUse(ElasticIndexFieldType.DATE);
        indexFieldsMap.put(dateField.getFieldName(), dateField);
        final String nowStr = "2021-02-17T01:23:34.000";
        final long expectedParsedDateFieldValue = 1613525014000L;

        // Parse the date/time. Must specify UTC for `timeZoneId`, otherwise the local system timezone will be used
        final Optional<ZonedDateTime> expectedDate = DateExpressionParser.parse(nowStr, "UTC", nowEpochMillis);
        Assert.assertTrue("Date was parsed", expectedDate.isPresent());
        final long dateFieldValue = expectedDate.get().toInstant().toEpochMilli();
        Assert.assertEquals("Parsed date value is correct", expectedParsedDateFieldValue, dateFieldValue);

        ExpressionOperator notOperator = new Builder()
                .op(Op.NOT)
                .addTerm(dateField.getFieldName(), Condition.GREATER_THAN, nowStr)
                .build();

        expressionBuilder.addOperator(notOperator);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = (BoolQueryBuilder) queryBuilder;
        Assert.assertEquals("Bool query contains exactly three items", 3, boolQuery.must().size());
        innerBoolQuery = (BoolQueryBuilder) boolQuery.must().get(2);
        Assert.assertEquals("Inner bool query contains one item", 1, innerBoolQuery.mustNot().size());

        RangeQueryBuilder firstRangeQuery = (RangeQueryBuilder) innerBoolQuery.mustNot().get(0);
        Assert.assertEquals("Field name of first range query is correct", dateField.getFieldName(), firstRangeQuery.fieldName());
        Assert.assertEquals("Field value of first range query is correct", expectedParsedDateFieldValue, firstRangeQuery.from());
    }
}
