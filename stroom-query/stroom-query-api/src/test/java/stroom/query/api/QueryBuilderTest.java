/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.api;

import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator.Op;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QueryBuilderTest {
    @Test
    void doesBuild() {
        final String dataSourceName = "someDataSource";
        final String dataSourceType = "someDocRefType";
        final String dataSourceUuid = UUID.randomUUID().toString();

        final Query query = Query
                .builder()
                .dataSource(new DocRef(dataSourceType, dataSourceUuid, dataSourceName))
                .addParam("someKey0", "someValue0")
                .addParam("someKey1", "someValue1")
                .expression(ExpressionOperator.builder()
                        .addTerm("fieldX", ExpressionTerm.Condition.EQUALS, "abc")
                        .addOperator(ExpressionOperator.builder().op(Op.OR)
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
        final ExpressionOperator root = query.getExpression();
        assertThat(root).isNotNull();
        assertThat(root.getChildren()).hasSize(3);

        final ExpressionItem rootChild1 = root.getChildren().get(0);
        final ExpressionItem rootChild2 = root.getChildren().get(1);
        final ExpressionItem rootChild3 = root.getChildren().get(2);

        assertThat(rootChild1 instanceof ExpressionTerm).isTrue();
        assertThat(((ExpressionTerm) rootChild1).getField()).isEqualTo("fieldX");

        assertThat(rootChild2 instanceof ExpressionOperator).isTrue();
        final ExpressionOperator child2Op = (ExpressionOperator) rootChild2;
        assertThat(child2Op.op()).isEqualTo(Op.OR);
        assertThat(child2Op.getChildren()).hasSize(2);

        assertThat(rootChild3 instanceof ExpressionTerm).isTrue();
        assertThat(((ExpressionTerm) rootChild3).getField()).isEqualTo("fieldY");
    }
}
