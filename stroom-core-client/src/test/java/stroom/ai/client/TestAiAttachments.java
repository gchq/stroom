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

import stroom.ai.shared.AiAttachmentStatus;
import stroom.ai.shared.AiAttachmentType;
import stroom.ai.shared.AiChatAttachment;
import stroom.svg.shared.SvgImage;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiAttachments {

    @Test
    void aChipSaysWhichDashboardAndTableItIs() {
        assertThat(AiAttachments.label("Dashboard 'Events' -> Table 'Logons'")).isEqualTo("Events - Logons");
        assertThat(AiAttachments.label("Dashboard 'Events'")).isEqualTo("Events");
        assertThat(AiAttachments.label("Query 'Failed logons'")).isEqualTo("Failed logons");
        // Anything else is shown as it is.
        assertThat(AiAttachments.label("Query table")).isEqualTo("Query table");
        assertThat(AiAttachments.label(null)).isEqualTo("Table");
    }

    @Test
    void itsIconIsOfWhereItCameFrom() {
        assertThat(AiAttachments.icon("Dashboard 'Events' -> Table 'Logons'")).isEqualTo(SvgImage.DOCUMENT_DASHBOARD);
        assertThat(AiAttachments.icon("Query 'Failed logons'")).isEqualTo(SvgImage.DOCUMENT_QUERY);
        assertThat(AiAttachments.icon("Some table")).isEqualTo(SvgImage.TABLE);
    }

    @Test
    void itsTooltipHasTheRowCountOnceDownloaded() {
        assertThat(AiAttachments.tooltip(null)).isEqualTo("Downloading...");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.DOWNLOADING, null, false, null)))
                .isEqualTo("Downloading...");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.READY, 1234, false, null)))
                .isEqualTo("1234 rows");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.READY, 1, true, null)))
                .isEqualTo("1 row (cut to the row limit)");
        assertThat(AiAttachments.tooltip(attachment(AiAttachmentStatus.ERROR, null, false, "Timed out")))
                .isEqualTo("Couldn't be downloaded: Timed out");
        assertThat(AiAttachments.isDownloading(attachment(AiAttachmentStatus.PENDING, null, false, null))).isTrue();
        assertThat(AiAttachments.isDownloading(attachment(AiAttachmentStatus.READY, 2, false, null))).isFalse();
    }

    private static AiChatAttachment attachment(final AiAttachmentStatus status,
                                               final Integer rows,
                                               final boolean truncated,
                                               final String error) {
        return new AiChatAttachment(1, 2, 0L, 0L, status, AiAttachmentType.DASHBOARD, "Dashboard 'A'", rows,
                truncated, error);
    }
}
