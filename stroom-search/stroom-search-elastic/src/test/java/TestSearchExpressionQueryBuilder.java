import stroom.datasource.api.v2.FieldType;
import stroom.docref.DocRef;
import stroom.expression.api.DateTimeSettings;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Op;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.MockIndexFieldCache;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexField;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

public class TestSearchExpressionQueryBuilder {


    @Test
    public void testBuildQuery() {
        final ElasticIndexField answerField = ElasticIndexField
                .builder()
                .fldName("answer")
                .nativeType("long")
                .fldType(FieldType.LONG)
                .build();

        final MockIndexFieldCache indexFieldCache = new MockIndexFieldCache();
        final ExpressionOperator.Builder expressionBuilder = ExpressionOperator.builder().op(Op.AND);
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                new DocRef("test", "test"),
                indexFieldCache,
                null,
                DateTimeSettings.builder().build()
        );

        indexFieldCache.put(answerField.getFldName(), answerField);
        final Long answerFieldValue = 42L;

        // Single numeric EQUALS condition contained within the default AND clause

        expressionBuilder.addTerm(answerField.getFldName(), Condition.EQUALS, answerFieldValue.toString());
        QueryBuilder queryBuilder = builder.buildQuery(expressionBuilder.build());

        Assertions.assertInstanceOf(BoolQueryBuilder.class, queryBuilder, "Is a `bool` query");
        BoolQueryBuilder boolQuery = (BoolQueryBuilder) queryBuilder;
        Assertions.assertEquals(1, boolQuery.must().size(), "Bool query contains exactly one item");

        TermQueryBuilder termQuery = (TermQueryBuilder) boolQuery.must().getFirst();
        Assertions.assertEquals(answerField.getFldName(), termQuery.fieldName(), "Field name is correct");
        Assertions.assertEquals(answerFieldValue, termQuery.value(), "Query value is correct");

        // Add a second text EQUALS condition
        final ElasticIndexField nameField = ElasticIndexField
                .builder()
                .fldName("name")
                .nativeType("text")
                .fldType(FieldType.TEXT)
                .build();
        indexFieldCache.put(nameField.getFldName(), nameField);

        // Add a nested NOT GREATER THAN date condition
        final ElasticIndexField dateField = ElasticIndexField
                .builder()
                .fldName("date")
                .nativeType("date")
                .fldType(FieldType.DATE)
                .build();
        indexFieldCache.put(dateField.getFldName(), dateField);
        final String nowStr = "2021-02-17T01:23:34.000";
        final long expectedParsedDateFieldValue = 1613525014000L;

        // Parse the date/time. Must specify UTC for `timeZoneId`, otherwise the local system timezone will be used
        final Optional<ZonedDateTime> expectedDate = DateExpressionParser.parse(nowStr);
        Assertions.assertTrue(expectedDate.isPresent(), "Date was parsed");
        final long dateFieldValue = expectedDate.get().toInstant().toEpochMilli();
        Assertions.assertEquals(expectedParsedDateFieldValue, dateFieldValue, "Parsed date value is correct");

        ExpressionOperator notOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(dateField.getFldName(), Condition.GREATER_THAN, nowStr)
                .build();

        expressionBuilder.addOperator(notOperator);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = (BoolQueryBuilder) queryBuilder;
        Assertions.assertEquals(2, boolQuery.must().size(), "Bool query contains exactly two items");
        BoolQueryBuilder innerBoolQuery = (BoolQueryBuilder) boolQuery.must().get(1);
        Assertions.assertEquals(1, innerBoolQuery.mustNot().size(),
                "Inner bool query contains one item");

        RangeQueryBuilder firstRangeQuery = (RangeQueryBuilder) innerBoolQuery.mustNot().getFirst();
        Assertions.assertEquals(dateField.getFldName(), firstRangeQuery.fieldName(),
                "Field name of first range query is correct");
        Assertions.assertEquals(expectedParsedDateFieldValue, firstRangeQuery.from(),
                "Field value of first range query is correct");
    }
}
