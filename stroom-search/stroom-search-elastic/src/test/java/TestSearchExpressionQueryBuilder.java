import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.FieldType;
import stroom.query.common.v2.DateExpressionParser;
import stroom.query.common.v2.MockIndexFieldCache;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexField;

import co.elastic.clients.elasticsearch._types.mapping.Property.Kind;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Optional;

public class TestSearchExpressionQueryBuilder {


    @Test
    public void testBuildQuery() {
        final MockIndexFieldCache indexFieldCache = new MockIndexFieldCache();
        final ExpressionOperator.Builder expressionBuilder = ExpressionOperator.builder().op(Op.AND);
        final SearchExpressionQueryBuilder builder = new SearchExpressionQueryBuilder(
                new DocRef("test", "test"),
                indexFieldCache,
                null,
                DateTimeSettings.builder().build()
        );

        final ElasticIndexField answerField = ElasticIndexField
                .builder()
                .fldName("answer")
                .nativeType("long")
                .fldType(FieldType.LONG)
                .build();

        indexFieldCache.put(answerField.getFldName(), answerField);
        final Long answerFieldValue = 42L;

        // Single numeric EQUALS condition contained within the default AND clause

        expressionBuilder.addTerm(answerField.getFldName(), Condition.EQUALS, answerFieldValue.toString());
        Query queryBuilder = builder.buildQuery(expressionBuilder.build());

        Assertions.assertTrue(queryBuilder.isBool(), "Is a `bool` query");
        BoolQuery boolQuery = queryBuilder.bool();
        Assertions.assertEquals(1, boolQuery.must().size(), "Bool query contains exactly one item");

        final TermQuery termQuery = boolQuery.must().getFirst().term();
        Assertions.assertEquals(answerField.getFldName(), termQuery.field(), "Field name is correct");
        Assertions.assertEquals(answerFieldValue, termQuery.value().longValue(), "Query value is correct");

        // Add a second text EQUALS condition
        final ElasticIndexField nameField = ElasticIndexField
                .builder()
                .fldName("name")
                .nativeType(Kind.Text.jsonValue())
                .fldType(FieldType.TEXT)
                .build();
        indexFieldCache.put(nameField.getFldName(), nameField);

        // Add a nested NOT GREATER THAN date condition
        final ElasticIndexField dateField = ElasticIndexField
                .builder()
                .fldName("date")
                .nativeType(Kind.Date.jsonValue())
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

        final ExpressionOperator notOperator = ExpressionOperator.builder()
                .op(Op.NOT)
                .addTerm(dateField.getFldName(), Condition.GREATER_THAN, nowStr)
                .build();

        expressionBuilder.addOperator(notOperator);
        queryBuilder = builder.buildQuery(expressionBuilder.build());

        boolQuery = queryBuilder.bool();
        Assertions.assertEquals(2, boolQuery.must().size(), "Bool query contains exactly two items");
        final BoolQuery innerBoolQuery = boolQuery.must().get(1).bool();
        Assertions.assertEquals(1, innerBoolQuery.mustNot().size(),
                "Inner bool query contains one item");

        final RangeQuery firstRangeQuery = innerBoolQuery.mustNot().getFirst().range();
        Assertions.assertEquals(dateField.getFldName(), firstRangeQuery.field(),
                "Field name of first range query is correct");
    }
}
