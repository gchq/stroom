package stroom.query.api.v2;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionBuilderTest {
    @Test
    void doesBuild() {
        ExpressionOperator root = new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                .addTerm("fieldX", ExpressionTerm.Condition.EQUALS, "abc")
                .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                        .addTerm("fieldA", ExpressionTerm.Condition.EQUALS, "Fred")
                        .addTerm("fieldA", ExpressionTerm.Condition.EQUALS, "Fred")
                        .build())
                .addTerm("fieldY", ExpressionTerm.Condition.BETWEEN, "10,20")
                .build();

        assertThat(root.getChildren()).hasSize(3);

        ExpressionItem rootChild1 = root.getChildren().get(0);
        ExpressionItem rootChild2 = root.getChildren().get(1);
        ExpressionItem rootChild3 = root.getChildren().get(2);

        assertThat(rootChild1 instanceof ExpressionTerm).isTrue();
        assertThat(((ExpressionTerm) rootChild1).getField()).isEqualTo("fieldX");

        assertThat(rootChild2 instanceof ExpressionOperator).isTrue();
        ExpressionOperator child2Op = (ExpressionOperator) rootChild2;
        assertThat(child2Op.op()).isEqualTo(ExpressionOperator.Op.OR);
        assertThat(child2Op.getChildren()).hasSize(2);

        assertThat(rootChild3 instanceof ExpressionTerm).isTrue();
        assertThat(((ExpressionTerm) rootChild3).getField()).isEqualTo("fieldY");
    }
}
