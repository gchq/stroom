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

import stroom.ai.impl.tools.ToolRefusal;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.documentation.shared.DocumentationDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.NullSafe;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/// The writes [PromoteBuildTool] makes for each step of an approved layout: a document moved, kept or discarded, a
/// working copy written back over its original after a backup, and the build's pending changes recorded as one new
/// version of each document's history.
class PromotionWrites {

    // A discarded step's target: a working copy the same as its original, discarded rather than written back.
    static final String UNCHANGED_COPY = "unchanged working copy, discarded";

    // The workspace's folder of the originals' backups, made before each is written over.
    static final String BACKUPS = "backups";
    private static final DateTimeFormatter BACKUP_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private final DocumentPlaces places;
    private final WriteGuard guard;
    private final DocumentWrites writes;
    private final DocumentAccess documentAccess;
    private final XsltHistory history;

    /// @param places         Finds each working copy's original.
    /// @param guard          Moves, renames, tags, backs up and deletes the build's documents.
    /// @param writes         Reads and writes documents' fields.
    /// @param documentAccess Reads and writes XSLTs.
    /// @param history        The clock version histories and backups are stamped with.
    PromotionWrites(final DocumentPlaces places,
                    final WriteGuard guard,
                    final DocumentWrites writes,
                    final DocumentAccess documentAccess,
                    final XsltHistory history) {
        this.places = places;
        this.guard = guard;
        this.writes = writes;
        this.documentAccess = documentAccess;
        this.history = history;
    }

    /// @param ref A document the promotion leaves in the workspace.
    /// @return What its step says was done.
    static String leftInTheWorkspace(final DocRef ref) {
        return "left " + ref.getType() + " '" + ref.getName() + "' in the workspace";
    }

    /// One step, carried out: what was done.
    ///
    /// @param step    The step of the approved layout.
    /// @param folders The folders the layout's documents go in, by path.
    /// @return What was done, for the user.
    String carryOut(final PromotionLayout.Step step, final Map<String, WriteGuard.Folder> folders) {
        final DocRef ref = PromoteBuildTool.ref(step.doc());
        return switch (step.action()) {
            case PromotionLayout.KEEP -> leftInTheWorkspace(ref);
            case PromotionLayout.DISCARD -> {
                guard.delete(ref);
                yield UNCHANGED_COPY.equals(step.target())
                        ? "discarded unchanged working copy " + ref.getType() + " '" + ref.getName() + "'"
                        : "deleted working-copy pipeline '" + ref.getName() + "'";
            }
            case PromotionLayout.MOVE -> {
                final String renamed = step.doc().get(PromotionLayout.RENAMED_FROM) == null
                        ? ""
                        : " (renamed from '" + step.doc().get(PromotionLayout.RENAMED_FROM) + "', named for the "
                          + "version it goes with)";
                if (!renamed.isEmpty()) {
                    guard.rename(ref, ref.getName());
                }
                guard.moveTo(ref, folders.get(step.target()));
                // Now production content: the agent may no longer change it directly. mcp-generated stays, so it
                // is still known as the server's own.
                guard.untag(List.of(ref), Set.of(WriteGuard.MANAGED));
                yield "moved " + ref.getType() + " '" + ref.getName() + "' to " + step.target() + renamed;
            }
            default -> writeBack(ref, step.target());
        };
    }

    /// True when a working copy's written-back content is the same as its original's now: nothing to write back.
    ///
    /// @param doc          The working copy, as the build lists it.
    /// @param originalUuid Its original's UUID.
    /// @return True if there is nothing to write back.
    boolean unchangedCopy(final Map<String, Object> doc, final String originalUuid) {
        try {
            final DocRef copy = PromoteBuildTool.ref(doc);
            return places.find(originalUuid)
                    .map(found -> DocumentWrites.same(writes.fields(copy), writes.fields(
                                    new DocRef(copy.getType(), found.getUuid(), found.getName())),
                            PromoteBuildTool.WRITTEN_BACK))
                    .orElse(false);
        } catch (final RuntimeException e) {
            // Unreadable here: decided when it is written back, as before.
            return false;
        }
    }

    // A working copy written back over its original, after the original is backed up: never without the backup,
    // since a production change made since the copy was taken survives only there.
    private String writeBack(final DocRef copy, final String originalUuid) {
        final DocRef original = places.find(originalUuid)
                .map(found -> new DocRef(copy.getType(), found.getUuid(), found.getName()))
                .orElseThrow(() -> new ToolRefusal(copy.getType() + " " + originalUuid + " can't be read: it doesn't "
                                                   + "exist, or you don't have permission to view it"));
        if (DocumentWrites.same(writes.fields(copy), writes.fields(original), PromoteBuildTool.WRITTEN_BACK)) {
            guard.delete(copy);
            return "discarded unchanged working copy " + copy.getType() + " '" + copy.getName() + "'";
        }
        final WriteGuard.Folder backups = guard.buildFolder(BACKUPS);
        final List<DocRef> backupRefs = guard.copyTo(original, backups, original.getName() + " backup "
                                                                        + BACKUP_STAMP.format(history.now()));
        if (backupRefs.isEmpty()) {
            throw new ToolRefusal(copy.getType() + " '" + original.getName() + "' wasn't backed up to "
                                  + backups.path() + ", so it wasn't overwritten");
        }
        guard.tag(backupRefs, Set.of(WriteGuard.GENERATED));
        writes.copyFields(copy, original, PromoteBuildTool.WRITTEN_BACK);
        if (guard.tags(copy).contains(WriteGuard.KEPT_MAPPING)) {
            // Its description now holds the mapping: the safeguard that notices it deleted goes with it.
            guard.tag(List.of(original), Set.of(WriteGuard.KEPT_MAPPING));
        }
        guard.delete(copy);
        return "wrote " + copy.getType() + " '" + copy.getName() + "' back over '" + original.getName()
               + "' (backup kept)";
    }

    /// A build's pending changes, as one new line of each XSLT's version history and one new row of each doc's version
    /// control: called when the build is promoted.
    ///
    /// @param docs The build's documents.
    /// @return What was recorded.
    List<String> consolidateVersions(final List<Map<String, Object>> docs) {
        final List<String> done = new ArrayList<>();
        final Instant now = history.now();
        for (final Map<String, Object> listed : docs) {
            final DocRef ref = PromoteBuildTool.ref(listed);
            if (XsltDoc.TYPE.equals(ref.getType())) {
                final XsltDoc doc = documentAccess.read(ref, XsltDoc.class)
                        .orElseThrow(() -> new ToolRefusal("XSLT " + ref.getUuid() + " can't be read"));
                if (XsltVersionHistory.pendingOf(doc.getDescription()).filter(p -> !p.isEmpty()).isPresent()) {
                    final String code = NullSafe.string(doc.getData());
                    final String description = XsltVersionHistory.consolidate(XsltVersionHistory.adopt(
                            doc.getDescription(), code), code, now);
                    writeXslt(doc.copy().description(description).data(XsltVersionHistory.strip(code)).build());
                    done.add("recorded the build's changes as version " + XsltVersionHistory.history(description)
                            .size() + " of XSLT '" + ref.getName() + "' (its Documentation)");
                }
            } else if (DocumentationDoc.TYPE.equals(ref.getType())) {
                final DocumentationDoc doc = writes.documentation(ref);
                final Optional<String> text = DocumentationVersions.consolidate(BuildRecords.bodyText(doc), now);
                if (text.isPresent()) {
                    writes.write(BuildRecords.withBodyText(doc, text.get()));
                    done.add("recorded the build's changes as version " + DocumentationVersions.rowsOf(text.get())
                            .size() + " of documentation '" + ref.getName() + "'");
                }
            }
        }
        return done;
    }

    @SuppressWarnings("unchecked")
    private void writeXslt(final XsltDoc doc) {
        final DocumentActionHandler<XsltDoc> handler = documentAccess.handler(XsltDoc.TYPE)
                .orElseThrow(() -> new ToolRefusal("Stroom has no XSLT store"));
        handler.writeDocument(doc);
    }
}
