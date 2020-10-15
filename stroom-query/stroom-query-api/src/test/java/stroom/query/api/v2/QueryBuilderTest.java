package stroom.query.api.v2;

import org.junit.jupiter.api.Test;
import stroom.docref.DocRef;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueryBuilderTest {
    @Test
    void doesBuild() {
        final String dataSourceName = "someDataSource";
        final String dataSourceType = "someDocRefType";
        final String dataSourceUuid = UUID.randomUUID().toString();

        final Query query = new Query.Builder()
                .dataSource(dataSourceType, dataSourceUuid, dataSourceName)
                .addParam("someKey0", "someValue0")
                .addParam("someKey1", "someValue1")
                .expression(new ExpressionOperator.Builder(ExpressionOperator.Op.AND)
                        .addTerm("fieldX", ExpressionTerm.Condition.EQUALS, "abc")
                        .addOperator(new ExpressionOperator.Builder(ExpressionOperator.Op.OR)
                                .addTerm("fieldA", ExpressionTerm.Condition.EQUALS, "Fred")
                                .addTerm("fieldA", ExpressionTerm.Condition.EQUALS, "Fred")
                                .build())
                        .addTerm("fieldY", ExpressionTerm.Condition.BETWEEN, "10,20")
                        .build())
                .build();

        // Examine the params
        final List<Param> params = query.getParams();
        assertThat(params).isNotNull();
        assertThat(params).hasSize(2);
        final Param param0 = params.get(0);
        assertThat(param0.getKey()).isEqualTo("someKey0");
        assertThat(param0.getValue()).isEqualTo("someValue0");
        final Param param1 = params.get(1);
        assertThat(param1.getKey()).isEqualTo("someKey1");
        assertThat(param1.getValue()).isEqualTo("someValue1");

        // Examine the datasource
        final DocRef dataSource = query.getDataSource();
        assertThat(dataSource).isNotNull();
        assertThat(dataSource.getName()).isEqualTo(dataSourceName);
        assertThat(dataSource.getType()).isEqualTo(dataSourceType);
        assertThat(dataSource.getUuid()).isEqualTo(dataSourceUuid);

        // Examine the expression
        ExpressionOperator root = query.getExpression();
        assertThat(root).isNotNull();
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
