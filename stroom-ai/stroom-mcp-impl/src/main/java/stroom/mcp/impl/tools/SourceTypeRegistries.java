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
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.sample.FeedSampling.SurveyRecord;
import stroom.ai.impl.tools.sample.SourceTypeRegistry;
import stroom.ai.impl.tools.sample.SurveyDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import jakarta.inject.Inject;

import java.util.Map;
import java.util.Optional;

/// A feed's documents kept beside it once promoted: its source type registry ([SourceTypeRegistry]) and its survey
/// doc ([SurveyDoc]). A build makes or changes them in its own folder, through [WriteGuard]: one beside the feed
/// already is changed through a working copy, which promotion writes back over it, as any promoted document is.
class SourceTypeRegistries {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceTypeRegistries.class);

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final DocumentWrites documentWrites;
    private final FeedNames feedNames;
    private final DocumentPlaces places;

    @Inject
    SourceTypeRegistries(final WriteGuard guard,
                         final DocumentAccess documentAccess,
                         final DocumentWrites documentWrites,
                         final FeedNames feedNames,
                         final DocumentPlaces places) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.documentWrites = documentWrites;
        this.feedNames = feedNames;
        this.places = places;
    }

    /// @param build A build.
    /// @param name  A Documentation doc's name.
    /// @return The build's Documentation doc of that name, if the user can read it.
    Optional<SurveyRecord> inBuild(final String build, final String name) {
        return guard.folderContents(build).stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.type()) && name.equals(doc.name()))
                .findFirst()
                .flatMap(doc -> read(doc.docRef()));
    }

    /// @param feed A feed's name.
    /// @param name A Documentation doc's name.
    /// @return The Documentation doc of that name in the folder of the feed (one outside any build), if the user
    /// can read it: a registry or survey promoted beside the feed.
    Optional<SurveyRecord> besideFeed(final String feed, final String name) {
        try {
            for (final DocRef ref : feedNames.feedsNamed(feed)) {
                if (guard.buildOf(ref).isPresent()) {
                    continue;
                }
                final Optional<SurveyRecord> found = places.documentationBeside(ref, name)
                        .filter(doc -> guard.buildOf(doc).isEmpty())
                        .flatMap(this::read);
                if (found.isPresent()) {
                    return found;
                }
            }
        } catch (final RuntimeException e) {
            // Advice: a feed whose folder can't be read is surveyed as if nothing were beside it.
            LOGGER.debug(() -> LogUtil.message("Nothing read beside feed {}: {}", feed, e.getMessage()));
        }
        return Optional.empty();
    }

    /// @param build A build, or null.
    /// @param feed  A feed's name.
    /// @return The feed's registry: the build's own, else the one promoted beside the feed; empty when neither is.
    Optional<Found> registry(final String build, final String feed) {
        final String name = SourceTypeRegistry.docName(feed);
        final Optional<SurveyRecord> own = build == null
                ? Optional.empty()
                : inBuild(build, name);
        if (own.isPresent()) {
            return own.map(doc -> new Found(doc, false, SourceTypeRegistry.read(doc.text())))
                    .filter(found -> found.state() != null);
        }
        return besideFeed(feed, name).map(doc -> new Found(doc, true, SourceTypeRegistry.read(doc.text())))
                .filter(found -> found.state() != null);
    }

    /// Writes a feed's registry in the build: over the build's own, else made new, as a working copy of the one
    /// beside the feed when there is one.
    ///
    /// @param session  The session, for the audit log.
    /// @param build    The build.
    /// @param feed     The feed.
    /// @param existing The registry found ([#registry]), or null.
    /// @param text     The registry's text.
    /// @return The registry as written.
    SurveyRecord writeRegistry(final ToolSession session,
                               final String build,
                               final String feed,
                               final Found existing,
                               final String text) {
        if (existing != null && !existing.promoted()) {
            final DocRef ref = new DocRef(DocumentationDoc.TYPE, existing.doc().uuid(), existing.doc().name());
            guard.checkInBuild(session, ref);
            return write(ref, text);
        }
        final DocRef made = guard.create(DocumentationDoc.TYPE, SourceTypeRegistry.docName(feed), build,
                existing == null
                        ? null
                        : existing.doc().uuid(), false, null);
        return write(made, text);
    }

    /// @param feed A feed's name.
    /// @return Its survey doc promoted beside it, with the state it keeps, if there is one the user can read.
    Optional<SurveyRecord> promotedSurvey(final String feed) {
        return besideFeed(feed, SurveyDoc.docName(feed)).filter(doc -> SurveyDoc.readState(doc.text()) != null);
    }

    private Optional<SurveyRecord> read(final DocRef ref) {
        return documentAccess.read(ref, DocumentationDoc.class)
                .map(doc -> new SurveyRecord(doc.getUuid(), doc.getName(), BuildRecords.bodyText(doc)));
    }

    private SurveyRecord write(final DocRef ref, final String text) {
        final DocumentationDoc saved = documentWrites.writeText(ref, text);
        return new SurveyRecord(saved.getUuid(), saved.getName(), text);
    }


    // --------------------------------------------------------------------------------


    /// A registry found.
    ///
    /// @param doc      Its document.
    /// @param promoted True when it is beside the feed, not in the build: changed through a working copy.
    /// @param state    Its machine block.
    record Found(SurveyRecord doc, boolean promoted, Map<String, Object> state) {

    }
}
