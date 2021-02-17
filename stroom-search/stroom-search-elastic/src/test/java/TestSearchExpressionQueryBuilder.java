import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionOperator.Builder;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.search.elastic.shared.ElasticIndexFieldType;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.xml.converter.ds3.Expression;

import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSearchExpressionQueryBuilder {
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
                null,
                System.currentTimeMillis()
        );
    }

    @Test
    public void testBuildQueryNumericField() {
        ElasticIndexField answerField = new ElasticIndexField();
        answerField.setFieldName("answer");
        answerField.setFieldUse(ElasticIndexFieldType.NUMBER);
        indexFieldsMap.put("answer", answerField);
        final Long answerFieldValue = 42L;
        expressionBuilder.addTerm(answerField.getFieldName(), Condition.EQUALS, answerFieldValue.toString());

        QueryBuilder queryBuilder = builder.buildQuery(expressionBuilder.build());

        Assert.assertTrue("Is a `bool` query", queryBuilder instanceof BoolQueryBuilder);
        BoolQueryBuilder boolQuery = (BoolQueryBuilder)queryBuilder;
        Assert.assertEquals("Bool query contains exactly one item", 1, boolQuery.must().size());

        TermQueryBuilder termQuery = (TermQueryBuilder)boolQuery.must().get(0);
        Assert.assertEquals("Field name is correct", answerField.getFieldName(), termQuery.fieldName());
        Assert.assertEquals("Query value is correct", answerFieldValue, termQuery.value());
    }
}
