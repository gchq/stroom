/*
 * Copyright 2026 Crown Copyright
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

package stroom.dashboard.impl.visualisation;

import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.api.Format;
import stroom.query.api.TableSettings;
import stroom.query.api.token.AbstractToken;
import stroom.query.api.token.KeywordGroup;
import stroom.query.api.token.TokenException;
import stroom.query.api.token.TokenType;
import stroom.query.language.DataSourceResolver;
import stroom.query.language.token.StructureBuilder;
import stroom.query.language.token.Tokeniser;
import stroom.visualisation.shared.VisualisationDoc;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestVisualisationTokenConsumerImpl {

    private static final DocRef BAR_CHART = new DocRef(VisualisationDoc.TYPE, "bar-chart-uuid", "BarChart");

    // A bar chart that takes text, numbers or dates along X and numbers up Y, both required
    private static final String SETTINGS = """
            {
              "inputs": [
                {"id": "x", "label": "X axis", "types": ["TEXT", "NUMBER", "DATE_TIME"], "required": true},
                {"id": "y", "label": "Y axis", "types": ["NUMBER"], "required": true},
                {"id": "series", "label": "Series", "types": ["TEXT"]}
              ],
              "tabs": [{"name": "Data", "controls": [
                {"id": "x", "type": "field", "label": "X"},
                {"id": "y", "type": "field", "label": "Y"},
                {"id": "series", "type": "field", "label": "Series"}
              ]}],
              "data": {"structure": {"values": {"fields": [{"id": "${x}"}, {"id": "${y}"}]}}}
            }
            """;

    private static final TableSettings PARENT = TableSettings.builder()
            .addColumns(Column.builder().name("Host").format(Format.TEXT).build())
            .addColumns(Column.builder().name("Count").format(Format.NUMBER).build())
            .addColumns(Column.builder().name("Time").format(Format.DATE_TIME).build())
            .addColumns(Column.builder().name("Status").format(Format.TEXT).build())
            .build();

    private VisualisationTokenConsumerImpl consumer;

    @BeforeEach
    void setUp() {
        final DataSourceResolver dataSourceResolver = Mockito.mock(DataSourceResolver.class);
        Mockito.when(dataSourceResolver.findVisualisationDoc("BarChart")).thenReturn(BAR_CHART);
        final VisualisationStore visualisationStore = Mockito.mock(VisualisationStore.class);
        Mockito.when(visualisationStore.readDocument(BAR_CHART)).thenReturn(VisualisationDoc.builder()
                .uuid(BAR_CHART.getUuid())
                .name(BAR_CHART.getName())
                .settings(SETTINGS)
                .build());
        consumer = new VisualisationTokenConsumerImpl(dataSourceResolver, visualisationStore);
    }

    @Test
    void testColumnsOfTheRightTypes() {
        final TableSettings tableSettings = consumer.processVis(show("show BarChart (x = Host, y = Count)"), PARENT);
        assertThat(tableSettings.getColumns()).extracting(Column::getExpression)
                .containsExactly("${Host}", "${Count}");
    }

    @Test
    void testColumnOfATypeTheInputDoesNotTake() {
        assertThatThrownBy(() -> consumer.processVis(show("show BarChart (x = Host, y = Status)"), PARENT))
                .isInstanceOf(TokenException.class)
                .hasMessage("Column 'Status' is text but 'Y axis' of 'BarChart' takes numbers");
        assertThatThrownBy(() -> consumer.processVis(show("show BarChart (x = Host, y = Time)"), PARENT))
                .isInstanceOf(TokenException.class)
                .hasMessage("Column 'Time' is date time but 'Y axis' of 'BarChart' takes numbers");
    }

    @Test
    void testRequiredInputMissing() {
        assertThatThrownBy(() -> consumer.processVis(show("show BarChart (x = Host)"), PARENT))
                .isInstanceOf(TokenException.class)
                .hasMessage("'BarChart' needs a column for 'Y axis' (y)");
        // Without the space before the brackets, the visualisation is read as a function
        assertThatThrownBy(() -> consumer.processVis(show("show BarChart(y = Count)"), PARENT))
                .isInstanceOf(TokenException.class)
                .hasMessage("'BarChart' needs a column for 'X axis' (x)");
    }

    @Test
    void testOptionalInputMayBeLeftOut() {
        // Series isn't required, and a text input takes any column
        assertThat(consumer.processVis(show("show BarChart (x = Count, y = Count, series = Time)"), PARENT)
                .getColumns()).hasSize(2);
        assertThat(consumer.processVis(show("show BarChart (x = Time, y = Count)"), PARENT)
                .getColumns()).hasSize(2);
    }

    // The 'show' keyword group of a query
    private static KeywordGroup show(final String query) {
        final List<AbstractToken> children = StructureBuilder.create(Tokeniser.parse(query)).getChildren();
        return children.stream()
                .filter(token -> token.getTokenType() == TokenType.SHOW)
                .map(KeywordGroup.class::cast)
                .findFirst()
                .orElseThrow();
    }
}
