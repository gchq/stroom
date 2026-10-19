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

package stroom.dashboard.client.main;

import stroom.dashboard.shared.Automate;
import stroom.dashboard.shared.QueryComponentSettings;
import stroom.dashboard.shared.TableComponentSettings;
import stroom.dashboard.shared.TextComponentSettings;
import stroom.docref.DocRef;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiComponentSettings {

    private static final DocRef PIPELINE = new DocRef("Pipeline", "pipeline-uuid", "Extraction");

    @Test
    void aTablesSettingsAreChangedAsItsSettingsDialogWould() {
        final TableComponentSettings table = TableComponentSettings.builder().queryId("query-1").build();

        assertThat(((TableComponentSettings) AiComponentSettings.table(table, "pageSize", "50", null, null))
                .getPageSize()).isEqualTo(50);
        assertThat(((TableComponentSettings) AiComponentSettings.table(table, "maxResults", "1000,10", null, null))
                .getMaxResults()).containsExactly(1000L, 10L);
        assertThat(((TableComponentSettings) AiComponentSettings.table(table, "showDetail", "true", null, null))
                .showDetail()).isTrue();
        final TableComponentSettings piped = (TableComponentSettings) AiComponentSettings.table(table,
                "extractionPipeline", "pipeline-uuid", null, PIPELINE);
        assertThat(piped.getExtractionPipeline()).isEqualTo(PIPELINE);
        assertThat(piped.extractValues()).isTrue();
        assertThat(piped.useDefaultExtractionPipeline()).isFalse();
        assertThat(((TableComponentSettings) AiComponentSettings.table(table, "query", "Other", "query-2", null))
                .getQueryId()).isEqualTo("query-2");
        final TableComponentSettings longer = (TableComponentSettings) AiComponentSettings.table(table,
                "maxStringFieldLength", "2000", null, null);
        assertThat(longer.getMaxStringFieldLength()).isEqualTo(2000);
        assertThat(longer.overrideMaxStringFieldLength()).isTrue();
    }

    @Test
    void aQuerysAutomationIsChangedKeepingTheRest() {
        final QueryComponentSettings query = QueryComponentSettings.builder()
                .automate(Automate.builder().refreshInterval("10s").build())
                .build();

        final QueryComponentSettings onOpen = (QueryComponentSettings) AiComponentSettings.query(query,
                "queryOnOpen", "true", null);
        assertThat(onOpen.getAutomate().isOpen()).isTrue();
        assertThat(onOpen.getAutomate().getRefreshInterval()).isEqualTo("10s");
        assertThat(((QueryComponentSettings) AiComponentSettings.query(query, "refreshInterval", "1m", null))
                .getAutomate().getRefreshInterval()).isEqualTo("1m");
        final DocRef view = new DocRef("View", "view-uuid", "Windows events");
        assertThat(((QueryComponentSettings) AiComponentSettings.query(query, "dataSource", "view-uuid", view))
                .getDataSource()).isEqualTo(view);
        // Without automation set yet.
        assertThat(((QueryComponentSettings) AiComponentSettings.query(QueryComponentSettings.builder().build(),
                "autoRefresh", "true", null)).getAutomate().isRefresh()).isTrue();
    }

    @Test
    void aTextComponentFollowsATableOrAnyAndShowsThroughAPipelineOrNone() {
        final TextComponentSettings text = TextComponentSettings.builder().tableId("table-1").pipeline(PIPELINE)
                .build();

        assertThat(((TextComponentSettings) AiComponentSettings.text(text, "table", "none", null, null))
                .getTableId()).isNull();
        assertThat(((TextComponentSettings) AiComponentSettings.text(text, "table", "Mentions", "table-2", null))
                .getTableId()).isEqualTo("table-2");
        assertThat(((TextComponentSettings) AiComponentSettings.text(text, "pipeline", "none", null, null))
                .getPipeline()).isNull();
        assertThat(((TextComponentSettings) AiComponentSettings.text(text, "showAsHtml", "true", null, null))
                .isShowAsHtml()).isTrue();
    }

    @Test
    void unknownSettingsOrMissingValuesAreNotChanged() {
        final TableComponentSettings table = TableComponentSettings.builder().build();

        assertThat(AiComponentSettings.table(table, "colour", "red", null, null)).isNull();
        assertThat(AiComponentSettings.table(table, "extractionPipeline", "x", null, null)).isNull();
        assertThat(AiComponentSettings.table(table, "query", "x", null, null)).isNull();
        assertThat(AiComponentSettings.query(QueryComponentSettings.builder().build(), "dataSource", "x", null))
                .isNull();
        assertThat(AiComponentSettings.text(TextComponentSettings.builder().build(), "pageSize", "1", null, null))
                .isNull();
    }
}
