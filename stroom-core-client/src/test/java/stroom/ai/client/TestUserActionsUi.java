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

package stroom.ai.client;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserActionsUi {

    @Test
    void aButtonCanBeUsedUntilItIsUsedOrExpires() {
        assertThat(UserActionsUi.isPending("PENDING", 2_000, 1_000)).isTrue();
        assertThat(UserActionsUi.isPending("PENDING", 1_000, 1_000)).isFalse();
        assertThat(UserActionsUi.isPending("DONE", 2_000, 1_000)).isFalse();
        assertThat(UserActionsUi.isPending(null, 2_000, 1_000)).isFalse();
    }

    @Test
    void whatIsShownInPlaceOfAButton() {
        assertThat(UserActionsUi.outcomeText("DONE", "Enabled processor filter 31."))
                .isEqualTo("Enabled processor filter 31.");
        assertThat(UserActionsUi.outcomeText("PENDING", null)).isEqualTo("This button has expired.");
        assertThat(UserActionsUi.outcomeText("FAILED", " ")).isEqualTo("FAILED");
    }

    @Test
    void buttonTexts() {
        assertThat(UserActionsUi.buttonText(UserActionsUi.ENABLE_PROCESSOR_FILTER)).isEqualTo("Enable");
        assertThat(UserActionsUi.buttonText(UserActionsUi.DISABLE_PROCESSOR_FILTER)).isEqualTo("Disable");
        assertThat(UserActionsUi.buttonText(UserActionsUi.OPEN_DOCUMENT)).isEqualTo("Open");
        assertThat(UserActionsUi.buttonText(UserActionsUi.CREATE_QUERY)).isEqualTo("Create query…");
        assertThat(UserActionsUi.buttonText(UserActionsUi.APPLY_CODE_CHANGE)).isEqualTo("Review and apply…");
        assertThat(UserActionsUi.buttonText("something_else")).isEqualTo("Do it");
    }

    @Test
    void aProposedQueryAndAnElasticsearchRequestAreShownAsCode() {
        assertThat(UserActionsUi.showsCode(UserActionsUi.CREATE_QUERY)).isTrue();
        assertThat(UserActionsUi.showsCode(UserActionsUi.ELASTICSEARCH_REQUEST)).isTrue();
        assertThat(UserActionsUi.showsCode(UserActionsUi.OPEN_DOCUMENT)).isFalse();
    }

    @Test
    void onlyAnAppliedCodeChangeOffersUndoApply() {
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_CODE_CHANGE, "DONE")).isTrue();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.APPLY_CODE_CHANGE, "PENDING")).isFalse();
        assertThat(UserActionsUi.isAppliedCodeChange(UserActionsUi.CREATE_QUERY, "DONE")).isFalse();
        // A proposed change is shown as a diff, not as code to copy.
        assertThat(UserActionsUi.showsCode(UserActionsUi.APPLY_CODE_CHANGE)).isFalse();
        assertThat(UserActionsUi.UNDO_WITHDRAWN).contains("Ctrl+Z");
    }
}
