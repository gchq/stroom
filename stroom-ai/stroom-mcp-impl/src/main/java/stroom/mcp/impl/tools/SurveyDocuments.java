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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecords;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;

import jakarta.inject.Inject;

import java.util.Optional;

/// A build's survey documents: a Documentation doc per surveyed feed, named [SurveyDoc#docName(String)], in the build
/// folder. They are made through [WriteGuard], and changed only when the tools made them and they are in the build.
class SurveyDocuments {

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final DocumentWrites documentWrites;

    @Inject
    SurveyDocuments(final WriteGuard guard, final DocumentAccess documentAccess, final DocumentWrites documentWrites) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.documentWrites = documentWrites;
    }

    /// @param build The build.
    /// @param feed  The feed.
    /// @return The build's survey document for the feed, if there is one the user can read.
    Optional<SurveyRecord> load(final String build, final String feed) {
        final String name = SurveyDoc.docName(feed);
        return guard.folderContents(build).stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.type()) && name.equals(doc.name()))
                .findFirst()
                .flatMap(doc -> documentAccess.read(doc.docRef(), DocumentationDoc.class))
                .map(doc -> new SurveyRecord(doc.getUuid(), doc.getName(), BuildRecords.bodyText(doc)));
    }

    /// Writes a survey document's text, once the guard has checked it is the tools' own and in a build.
    ///
    /// @param session The session the call is made in, for the audit log.
    /// @param record  A survey document.
    /// @param text    Its new text.
    /// @return The document, written.
    /// @throws ToolRefusal if it isn't the tools' own, is not in a build, or can't be read.
    SurveyRecord write(final ToolSession session, final SurveyRecord record, final String text) {
        final DocRef ref = new DocRef(DocumentationDoc.TYPE, record.uuid(), record.name());
        guard.checkInBuild(session, ref);
        return writeText(ref, text);
    }

    /// @param session The session the calls are made in.
    /// @return The survey documents as the survey keeps them ([stroom.ai.impl.tools.sample.FeedSampling#keep]).
    SurveyRecords records(final ToolSession session) {
        return new SurveyRecords() {
            @Override
            public Optional<SurveyRecord> load(final String build, final String feed) {
                return SurveyDocuments.this.load(build, feed);
            }

            @Override
            public SurveyRecord create(final String build, final String name, final String text) {
                return guard.createFilled(DocumentationDoc.TYPE, name, build, ref -> writeText(ref, text), false,
                        null);
            }

            @Override
            public SurveyRecord write(final SurveyRecord record, final String text) {
                return SurveyDocuments.this.write(session, record, text);
            }
        };
    }

    private SurveyRecord writeText(final DocRef ref, final String text) {
        final DocumentationDoc saved = documentWrites.writeText(ref, text);
        return new SurveyRecord(saved.getUuid(), saved.getName(), text);
    }
}
