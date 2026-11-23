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

package stroom.ai.impl.tools;

import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/// A field named in a StroomQL select in double quotes is that text in every row, not the field: validate_stroomql
/// and run_stroomql say to write it as ${...} (seen when a query of the Stream Store selected "Create Time").
class TestQuotedSelectFields {

    private static final List<String> FIELDS = List.of("Feed", "Create Time", "Status");
    private static final DocRef META = new DocRef("Searchable", "meta", "Stream Store");

    @Test
    void aFieldInDoubleQuotesInSelectIsNamedWithItsReferenceForm() {
        assertThat(QuotedSelectFields.note("from \"Stream Store\" select Feed, \"Create Time\"", FIELDS))
                .isEqualTo(" In select, \"Create Time\" is that text in every row, not the field: write "
                           + "${Create Time} (a field whose name has a space is written ${...}; double quotes "
                           + "give text, or a column name after as).");
    }

    @Test
    void theFieldIsMatchedIgnoringCaseAndNamedAsTheDataSourceSpellsIt() {
        assertThat(QuotedSelectFields.note("from \"Stream Store\" select \"create time\" as Created, Feed", FIELDS))
                .contains("\"Create Time\" is that text").contains("write ${Create Time}");
    }

    @Test
    void severalAreNamedTogether() {
        assertThat(QuotedSelectFields.note("from x select \"Create Time\", \"Status\" limit 10", FIELDS))
                .contains("\"Create Time\", \"Status\" are those texts in every row, not the fields")
                .contains("write ${Create Time}, ${Status}");
    }

    @Test
    void nothingIsSaidOfAColumnNameAfterAsAFieldReferenceOrWhere() {
        assertThat(QuotedSelectFields.note("from x where \"Create Time\" > day() "
                                           + "select ${Create Time} as \"Create Time\", Feed", FIELDS)).isEmpty();
    }

    @Test
    void nothingIsSaidOfQuotedTextThatIsNoFieldOrAFunctionsArgument() {
        assertThat(QuotedSelectFields.note("from x select \"Not a field\" as Label, "
                                           + "concat(\"Create Time\", Feed) as Joined", FIELDS)).isEmpty();
    }

    @Test
    void aQueryThatCantBeReadGivesNothing() {
        assertThat(QuotedSelectFields.quotedColumns("from x select \"unterminated")).isEmpty();
        assertThat(QuotedSelectFields.note("", FIELDS)).isEmpty();
    }

    @Test
    void theDataSourcesFieldsAreReadOnlyWhenSelectHasQuotedText() {
        final QueryService queryService = Mockito.mock(QueryService.class);
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createText("Feed"), QueryField.createDate("Create Time"))));

        assertThat(QuotedSelectFields.note(queryService, META, "from \"Stream Store\" select Feed, \"Create Time\""))
                .contains("write ${Create Time}");
        assertThat(QuotedSelectFields.note(queryService, META, "from \"Stream Store\" select Feed, ${Create Time}"))
                .isEmpty();
        assertThat(QuotedSelectFields.note(queryService, null, "from x select \"Create Time\"")).isEmpty();
        Mockito.verify(queryService, Mockito.times(1)).findFields(Mockito.any());
    }

    @Test
    void fieldsThatCantBeReadGiveNothing() {
        final QueryService queryService = Mockito.mock(QueryService.class);
        Mockito.when(queryService.findFields(Mockito.any())).thenThrow(new RuntimeException("down"));

        assertThat(QuotedSelectFields.note(queryService, META, "from x select \"Create Time\"")).isEmpty();
    }
}
