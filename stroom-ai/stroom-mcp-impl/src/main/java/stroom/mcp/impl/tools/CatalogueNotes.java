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
import stroom.ai.impl.tools.JsonValues;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.ToolText;
import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueDiff;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/// The build's catalogue of event types in its source notes doc, for the lifecycle after the first build: a feed's
/// next version starts from the promoted catalogue (the promoted notes doc carried into the build as a working copy,
/// which promotion writes back over it), what changed since is read against that promoted doc, and a hand edit read
/// back from the XSLT (rebuild_mapping) is written into the catalogue. Only the notes' catalogue keys (`event_types`,
/// `catch_all`, `scope`) are written; the rest of the notes, and their storage, are record_source_notes'.
class CatalogueNotes {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CatalogueNotes.class);

    // The notes block of a source notes doc ([SourceNotes#block]).
    private static final Pattern BLOCK = Pattern.compile("<!-- stroom-mcp source notes\n.*?\n-->", Pattern.DOTALL);
    // How many folders up from a promoted pipeline its source's notes are looked for: its role folder (Events), the
    // source's own folder, a version's folder above.
    private static final int FOLDERS_UP = 3;

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    private final DocumentWrites documentWrites;
    private final FeedNames feedNames;

    /// @param guard          Finds the build's documents and creates them.
    /// @param documentAccess Reads documents and the explorer tree, as the user.
    /// @param documentWrites Reads and writes Documentation docs.
    /// @param feedNames      Finds feeds by name.
    @Inject
    CatalogueNotes(final WriteGuard guard,
                   final DocumentAccess documentAccess,
                   final DocumentWrites documentWrites,
                   final FeedNames feedNames) {
        this.guard = guard;
        this.documentAccess = documentAccess;
        this.documentWrites = documentWrites;
        this.feedNames = feedNames;
    }

    /// @param build A build.
    /// @return Its catalogue (its source notes doc's), or empty when it has none.
    Optional<Catalogue> catalogue(final String build) {
        return notesDoc(build).map(this::notesOf).map(SourceNotes::catalogue);
    }

    /// The catalogue the build's notes were carried from: the promoted notes doc its notes are a working copy of.
    ///
    /// @param build A build.
    /// @return The promoted catalogue, or empty when the build's notes aren't a promoted doc's working copy, or it
    /// holds none.
    Optional<Catalogue> promoted(final String build) {
        return notesDoc(build)
                .flatMap(doc -> guard.copyOf(doc.docRef()))
                .flatMap(documentAccess::findDocRef)
                .map(this::notesOf)
                .map(SourceNotes::catalogue);
    }

    /// What changed in the build's catalogue since the promoted one it started from, for catalogue_event_types'
    /// listing and the build's review.
    ///
    /// @param build A build.
    /// @return The event types added, changed and removed ([CatalogueDiff]); empty when the build didn't start from
    /// a promoted catalogue.
    Optional<CatalogueDiff.Diff> changedSincePromoted(final String build) {
        final Optional<Catalogue> promoted = promoted(build);
        return promoted.map(was -> CatalogueDiff.between(was, catalogue(build).orElse(null)));
    }

    /// A next version's build starts from the promoted catalogue: the source notes beside the promoted pipeline (in
    /// its folder or one above, where promotion put them) carried into the build as a working copy, unless the build
    /// has notes of its own already.
    ///
    /// @param build            The build of the next version.
    /// @param promotedPipeline The promoted pipeline the next version is of.
    /// @return What was carried, said; empty when nothing was (the build has notes, or none were found).
    Optional<String> carryInto(final String build, final DocRef promotedPipeline) {
        try {
            if (notesDoc(build).isPresent()) {
                return Optional.empty();
            }
            return promotedNotes(promotedPipeline).map(promoted -> carried(build, promoted));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No source notes carried into build {}: {}", build, e.getMessage()),
                    e);
            return Optional.empty();
        }
    }

    /// A next version's build starts from the promoted catalogue whether or not copy_pipeline has run (seen in eval
    /// case 70: the agent catalogued the next version before copying the pipeline, found no catalogue and started
    /// over, the confirmed event types lost): the source notes beside a promoted feed the build works from carried
    /// in as [#carryInto] carries them, unless the build has notes of its own already, so a later copy_pipeline
    /// carries nothing more.
    ///
    /// @param build The build.
    /// @param feeds The existing feeds it works from.
    /// @return What was carried, said; empty when nothing was (the build has notes, or none were found).
    Optional<String> carryBesideFeeds(final String build, final List<String> feeds) {
        try {
            if (feeds.isEmpty() || notesDoc(build).isPresent()) {
                return Optional.empty();
            }
            for (final String feed : feeds) {
                for (final DocRef ref : feedNames.feedsNamed(feed)) {
                    if (guard.buildOf(ref).isPresent()) {
                        // A feed of a build is no promoted feed.
                        continue;
                    }
                    final Optional<DocRef> promoted = promotedNotes(ref)
                            .filter(notes -> guard.buildOf(notes).isEmpty());
                    if (promoted.isPresent()) {
                        return Optional.of(carried(build, promoted.get()));
                    }
                }
            }
            return Optional.empty();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("No source notes carried into build {} from feeds {}: {}", build,
                    feeds, e.getMessage()), e);
            return Optional.empty();
        }
    }

    // The promoted notes carried into the build as a working copy (written back at promotion): what was carried,
    // said.
    private String carried(final String build, final DocRef promoted) {
        final DocRef copy = guard.create(DocumentationDoc.TYPE, promoted.getName(), build, promoted.getUuid(), false,
                null);
        documentWrites.copyFields(promoted, copy, BuildDocumentation.DOC_FIELDS);
        final Catalogue catalogue = SourceNotes.catalogue(notesOf(copy));
        return "the source notes '" + promoted.getName() + "' carried into the build (a working copy, written back "
               + "at promotion)" + (catalogue == null
                ? ""
                : ", so its catalogue starts from the promoted one: " + catalogue.eventTypes().size()
                  + " event type(s)");
    }

    /// Writes the catalogue into the build's source notes, its other notes kept: the doc the build has, else a new
    /// one named for the source.
    ///
    /// @param session   The session (the doc must be the build's).
    /// @param build     The build.
    /// @param source    The source's name, for a new doc's.
    /// @param catalogue The catalogue.
    void write(final ToolSession session, final String build, final String source, final Catalogue catalogue) {
        final Optional<WriteGuard.BuildDoc> there = notesDoc(build);
        final String name = there.map(WriteGuard.BuildDoc::name).orElse(source + SourceNotes.NOTES_SUFFIX);
        final String text = there.map(doc -> documentWrites.text(doc.docRef())).orElse("");
        final Map<String, Object> notes = new LinkedHashMap<>(NullSafe.map(SourceNotes.readNotes(text)));
        if (NullSafe.isBlankString(ToolText.str(notes.getOrDefault("summary", "")))) {
            // A doc written by hand: its prose under the heading kept as the summary.
            prose(text).ifPresent(summary -> notes.put("summary", summary));
        }
        // Written as catalogue_event_types writes it: the JSON block and the "Event types" table together.
        // The records last counted are left out: the catalogue changed by hand, they are counted again by the next
        // check.
        final Map<String, Object> given = new LinkedHashMap<>(catalogue.toJson());
        final Map<String, Object> confirming = new LinkedHashMap<>(NullSafe.map(JsonValues.map(
                notes.get(RecordSourceNotesTool.CONFIRMING))));
        confirming.remove(RecordSourceNotesTool.RECORDS);
        given.put(RecordSourceNotesTool.CONFIRMING, confirming);
        final String written = RecordSourceNotesTool.textWithCatalogue(NullSafe.nonBlankStringElse(
                ToolText.str(notes.getOrDefault("source", "")), source), notes, given);
        if (there.isPresent()) {
            guard.checkInBuild(session, there.get().docRef());
            documentWrites.writeText(there.get().docRef(), written);
        } else {
            guard.createFilled(DocumentationDoc.TYPE, name, build, ref -> documentWrites.writeText(ref, written),
                    false, null);
        }
    }

    /// @param text  A notes doc's text.
    /// @param block The notes block to hold.
    /// @return The text with that block in place of its own, or after it when it has none.
    static String withBlock(final String text, final String block) {
        final Matcher found = BLOCK.matcher(text);
        if (found.find()) {
            return text.substring(0, found.start()) + block + text.substring(found.end());
        }
        return text.stripTrailing() + "\n\n" + block + "\n";
    }

    // A notes doc's prose: what is under its heading, before its first section or its block.
    private static Optional<String> prose(final String text) {
        final List<String> kept = new ArrayList<>();
        for (final String line : text.lines().toList()) {
            if (line.startsWith("## ") || line.startsWith("<!--")) {
                break;
            }
            if (!line.startsWith("# ")) {
                kept.add(line);
            }
        }
        final String prose = String.join("\n", kept).strip();
        return prose.isEmpty()
                ? Optional.empty()
                : Optional.of(prose);
    }

    // The build's source notes doc (one a build), at its top.
    private Optional<WriteGuard.BuildDoc> notesDoc(final String build) {
        return guard.folderContents(build).stream()
                .filter(doc -> DocumentationDoc.TYPE.equals(doc.type())
                               && doc.name().endsWith(SourceNotes.NOTES_SUFFIX))
                .findFirst();
    }

    private Map<String, Object> notesOf(final WriteGuard.BuildDoc doc) {
        return notesOf(doc.docRef());
    }

    private Map<String, Object> notesOf(final DocRef ref) {
        return NullSafe.map(SourceNotes.readNotes(documentWrites.text(ref)));
    }

    // The source notes doc beside a promoted pipeline (or feed): in its folder, or one of the folders above it.
    private Optional<DocRef> promotedNotes(final DocRef promoted) {
        Optional<DocRef> folder = documentAccess.parent(promoted);
        for (int up = 0; up < FOLDERS_UP && folder.isPresent(); up++) {
            final Optional<DocRef> notes = documentAccess.children(folder.get()).stream()
                    .map(ExplorerNode::getDocRef)
                    .filter(Objects::nonNull)
                    .filter(child -> DocumentationDoc.TYPE.equals(child.getType())
                                     && NullSafe.string(child.getName()).endsWith(SourceNotes.NOTES_SUFFIX))
                    .findFirst();
            if (notes.isPresent()) {
                return notes;
            }
            folder = documentAccess.parent(folder.get());
        }
        return Optional.empty();
    }
}
