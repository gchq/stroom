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

package stroom.query.impl;

import stroom.query.shared.QueryHelpDetail;
import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;
import stroom.ui.config.shared.UiConfig;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestStructures {

    @Test
    void keywordHelpLinksToItsDocumentationInANewTab() {
        final Structures structures = new Structures(UiConfig::new);
        final QueryHelpRow row = QueryHelpRow.builder()
                .type(QueryHelpType.STRUCTURE)
                .id("structure.from")
                .title("from")
                .build();

        final Optional<QueryHelpDetail> detail = structures.fetchDetail(row);

        // The link names the keyword, and says it opens a new tab
        assertThat(detail).isPresent();
        assertThat(detail.get().getDocumentation())
                .contains("For more information see the <a href=\"")
                .contains("#from\" target=\"_blank\" rel=\"noopener noreferrer\">from keyword documentation"
                          + "<span class=\"sr-only\"> (opens in new tab)</span></a>.")
                .doesNotContain("Help Documentation");
    }
}
