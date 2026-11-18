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
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.PipelineDoc;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAcceptedErrors {

    @TempDir
    Path tempDir;

    @Test
    void acceptedErrorsAreKeptInTheDocsTextAndReadBack() {
        final List<AcceptedErrors.Entry> accepted = List.of(AcceptedErrors.entry("decorationFilter",
                "No HR record for user svc-backup", "Service accounts have no HR record", "2026-10-04",
                "No HR record for user svc-*"));

        final String text = "## Errors\n\n" + AcceptedErrors.block(accepted) + "\n";

        assertThat(AcceptedErrors.readAccepted(text)).isEqualTo(accepted);
        assertThat(AcceptedErrors.readAccepted("no block here")).isEmpty();
        assertThat(AcceptedErrors.block(List.of())).isEmpty();
    }

    @Test
    void aNewerEntryForTheSameKindReplacesTheOlder() {
        final List<AcceptedErrors.Entry> accepted = List.of(AcceptedErrors.entry("decorationFilter",
                "No HR record for user svc-backup", "older", "2026-10-04", "No HR record for user svc-*"));
        final AcceptedErrors.Entry newer = AcceptedErrors.entry("decorationFilter", "No HR record for user svc-x",
                "newer", "2026-10-05", "No HR record for user svc-*");

        assertThat(AcceptedErrors.merge(accepted, List.of(newer)))
                .extracting(AcceptedErrors.Entry::reason)
                .containsExactly("newer");
    }

    @Test
    void anEntryOfAnotherElementDoesNotMatch() {
        final List<AcceptedErrors.Entry> accepted = List.of(AcceptedErrors.entry("decorationFilter", "x 1", "r",
                "2026-10-04", null));

        assertThat(AcceptedErrors.match(accepted, "decorationFilter", "x 2")).isNotNull();
        assertThat(AcceptedErrors.match(accepted, "translationFilter", "x 2")).isNull();
    }

    @Test
    void theErrorsAcceptedForAPipelineAreReadFromItsDocumentationDocs() {
        final ToolTestFixture fixture = new ToolTestFixture(tempDir);
        fixture.add(new DocRef(PipelineDoc.TYPE, "p", "Acme-Events"), null, true);
        final String text = AcceptedErrors.block(List.of(AcceptedErrors.entry(null, "Lookup failed for 7",
                "Known", "2026-10-04", null)));
        fixture.add(new DocRef(DocumentationDoc.TYPE, "d", "Acme-Events"),
                new DocumentationDoc("d", "Acme-Events", null, null, null, null, null, null, text), true);
        // A doc the user can't view gives nothing.
        fixture.add(new DocRef(DocumentationDoc.TYPE, "hidden", "Acme-Events"),
                new DocumentationDoc("hidden", "Acme-Events", null, null, null, null, null, null,
                        AcceptedErrors.block(List.of(AcceptedErrors.entry(null, "Other", "Hidden", "2026-10-04",
                                null)))), false);

        // Finding by name as Stroom does: only documents the user can view.
        fixture.feedAccess();
        final AcceptedErrors acceptedErrors = new AcceptedErrors(() -> fixture.docFinder, fixture.documentAccess());

        assertThat(acceptedErrors.acceptedFor("p"))
                .extracting(AcceptedErrors.Entry::reason)
                .containsExactly("Known");
        assertThat(acceptedErrors.acceptedFor("no-such-pipeline")).isEmpty();
    }
}
