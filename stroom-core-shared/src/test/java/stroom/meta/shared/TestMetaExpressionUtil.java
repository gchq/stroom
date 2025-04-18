package stroom.meta.shared;

import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;

import org.assertj.core.api.Assertions;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestMetaExpressionUtil {

    @Test
    void createDataIdSetExpression() {

        final ExpressionOperator expr = MetaExpressionUtil.createDataIdSetExpression(Set.of(1L, 2L, 3L));

        assertThat(expr.getChildren())
                .hasSize(1);

        final ExpressionItem expressionItem = expr.getChildren().get(0);
        assertThat(expressionItem)
                .isInstanceOf(ExpressionTerm.class);
        final ExpressionTerm term = (ExpressionTerm) expressionItem;
        assertThat(term)
                .extracting(ExpressionTerm::getField)
                .isEqualTo(MetaFields.ID.getFldName());
        assertThat(term)
                .extracting(ExpressionTerm::getCondition)
                .isEqualTo(Condition.IN);
        // Can't be sure of order as it is a set
        assertThat(term)
                .extracting(ExpressionTerm::getValue, Assertions.as(InstanceOfAssertFactories.STRING))
                .contains("1")
                .contains("2")
                .contains("3")
                .contains(",");
    }

    @Test
    void createDataIdSetExpression_oneItem() {

        final ExpressionOperator expr = MetaExpressionUtil.createDataIdSetExpression(Set.of(2L));

        assertThat(expr.getChildren())
                .hasSize(1);

        final ExpressionItem expressionItem = expr.getChildren().get(0);
        assertThat(expressionItem)
                .isInstanceOf(ExpressionTerm.class);
        final ExpressionTerm term = (ExpressionTerm) expressionItem;
        assertThat(term)
                .extracting(ExpressionTerm::getField)
                .isEqualTo(MetaFields.ID.getFldName());
        assertThat(term)
                .extracting(ExpressionTerm::getCondition)
                .isEqualTo(Condition.EQUALS);
        // Can't be sure of order as it is a set
        assertThat(term)
                .extracting(ExpressionTerm::getValue)
                .isEqualTo("2");
    }
}
