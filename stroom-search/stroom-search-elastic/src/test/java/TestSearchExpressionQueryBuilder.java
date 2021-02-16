import stroom.search.elastic.search.SearchExpressionQueryBuilder;
import stroom.util.test.StroomJUnit4ClassRunner;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestSearchExpressionQueryBuilder {
    SearchExpressionQueryBuilder builder;

    @Before
    public void init() {
        builder = new SearchExpressionQueryBuilder(
                null,
                null,
                null,
                System.currentTimeMillis()
        );
    }

    @Test
    public void testSimple() {
        Assert.assertEquals("Is true", 42, 42);
    }

    @Test
    public void testTokenizeExpression() {
        String expr = "123, 456,789";
        String[] expected = { "123", "456", "789" };
        String[] actual = builder.tokenizeExpression(expr);
        Assert.assertArrayEquals("Expression tokens were extracted", expected, actual);
    }
}
