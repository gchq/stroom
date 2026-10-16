/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.analytics.impl;

import stroom.analytics.shared.ReportDoc;
import stroom.dashboard.shared.DownloadSearchResultFileType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.simplejavamail.api.email.AttachmentResource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers what a report email carries. A summary that could not go inside the report has to reach the
 * recipient some other way, and a template that refers to the summary has to render whether or not there
 * was one to make.
 */
class TestReportEmailContent {

    private static final String SUMMARY = "Two users account for most of the activity.";

    @TempDir
    private Path tempDir;

    private final RuleEmailTemplatingService templatingService = new RuleEmailTemplatingService();

    @Test
    void csvWithASummary_isSentAsTwoAttachments() {
        final List<AttachmentResource> attachments = EmailSender.createAttachments(
                reportFile(DownloadSearchResultFileType.CSV, SUMMARY, file("report.csv"),
                        file("report_summary.md")));

        assertThat(attachments).extracting(AttachmentResource::getName)
                .containsExactly("report.csv", "report_summary.md");
    }

    @Test
    void reportThatCarriesItsOwnSummary_isSentAsOneAttachment() {
        final List<AttachmentResource> attachments = EmailSender.createAttachments(
                reportFile(DownloadSearchResultFileType.EXCEL, SUMMARY, file("report.xlsx"), null));

        assertThat(attachments).extracting(AttachmentResource::getName)
                .containsExactly("report.xlsx");
    }

    @Test
    void theSummary_isAvailableToTheEmailTemplate() {
        final Map<String, Object> context = EmailSender.createTemplateContext(
                report(),
                reportFile(DownloadSearchResultFileType.CSV, SUMMARY, file("report.csv"), null),
                Instant.now(),
                Instant.now());

        assertThat(templatingService.renderTemplate("Summary: {{ aiSummary }}", context))
                .isEqualTo("Summary: " + SUMMARY);
    }

    /**
     * Jinjava is configured to fail on unknown tokens, so a template using the summary must still render
     * for a report that has no summary - otherwise turning the summary off, or a model outage, would stop
     * the email going out at all.
     */
    @Test
    void templateUsingTheSummary_stillRendersWhenThereIsNoSummary() {
        final Map<String, Object> context = EmailSender.createTemplateContext(
                report(),
                reportFile(DownloadSearchResultFileType.CSV, null, file("report.csv"), null),
                Instant.now(),
                Instant.now());

        assertThat(templatingService.renderTemplate("Summary: {{ aiSummary }}", context))
                .isEqualTo("Summary: ");
    }

    // -----------------------------------------------------------------------------------------------

    private ReportDoc report() {
        return ReportDoc.builder()
                .uuid(UUID.randomUUID().toString())
                .name("Test Report")
                .build();
    }

    private ReportFile reportFile(final DownloadSearchResultFileType fileType,
                                  final String aiSummary,
                                  final Path file,
                                  final Path summaryFile) {
        return new ReportFile(file, fileType, 5, aiSummary, summaryFile);
    }

    private Path file(final String name) {
        try {
            return Files.writeString(tempDir.resolve(name), "content");
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
