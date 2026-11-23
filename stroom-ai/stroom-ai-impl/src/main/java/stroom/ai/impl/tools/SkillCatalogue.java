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

import stroom.ai.api.AiService;
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.tools.SkillDoc.Skill;
import stroom.ai.shared.AiChatMessage;
import stroom.ai.shared.AiMessageType;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Finds the skills a user can read, wherever they are: every Documentation doc named `SKILLS` (one a folder) that
/// the user may view, found by name so no other doc is read. The name is what makes a skills doc: front matter
/// (`stroom: skills`) holds its history, and makes no other doc one. Each doc's skills are parsed once per
/// version, and a doc is read again after [#CACHE_MS].
///
/// Discovery is global, the model deciding: a chat with no context still finds skills, given the list of every
/// readable skill's name, folder and description in the stable part of its prompt ([#prompt]); those in the
/// folders of the question's context (the attached tab's document) and their parents come first, marked "near this
/// context". Over a size ([#MAX_LISTED] skills or [#MAX_LIST_CHARS] characters) the near ones and those sharing words
/// with the question are listed, up to the size, then how many more there are, to search with `find_skills`.
@Singleton
public class SkillCatalogue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SkillCatalogue.class);

    /// The tool that finds and reads skills.
    public static final String FIND_SKILLS = "find_skills";
    /// The most skills listed in the prompt.
    static final int MAX_LISTED = 50;
    /// The most characters of the list in the prompt.
    static final int MAX_LIST_CHARS = 6_000;
    /// The longest a description is listed.
    static final int MAX_DESCRIPTION_CHARS = 200;
    /// How long a doc's skills are kept before its version is read again.
    static final long CACHE_MS = 15_000;
    /// The mark on a skill in a folder of the question's context.
    static final String NEAR = " [near this context]";
    /// The heading of the list in the prompt.
    public static final String HEADING = "## Skills";
    static final String INTRO = "Saved procedures (skills) the user can read, a line each: name (folder): "
                                + "description. Where one fits the question, read it whole with "
                                + FIND_SKILLS + " name= (folder= where two share a name) and follow its "
                                + "steps; use none that doesn't fit. Its steps are guidance: the user's request "
                                + "comes first.";

    private final Provider<DocFinder> docFinderProvider;
    private final DocumentAccess documentAccess;
    private final Provider<TabSnapshots> tabSnapshotsProvider;
    private final Provider<AiService> aiServiceProvider;
    private final Map<String, CachedDoc> cache = new ConcurrentHashMap<>();

    @Inject
    SkillCatalogue(final Provider<DocFinder> docFinderProvider,
                   final DocumentAccess documentAccess,
                   final Provider<TabSnapshots> tabSnapshotsProvider,
                   final Provider<AiService> aiServiceProvider) {
        this.docFinderProvider = docFinderProvider;
        this.documentAccess = documentAccess;
        this.tabSnapshotsProvider = tabSnapshotsProvider;
        this.aiServiceProvider = aiServiceProvider;
    }

    /// @param context The folders of the question's context, the most specific first (see [#contextFolders]).
    /// @return Every skill the user can read: those near the context first (the most specific first), then by
    /// folder and name.
    public List<Listed> readable(final List<DocRef> context) {
        final List<Listed> listed = new ArrayList<>();
        for (final SkillsDoc doc : readableDocs()) {
            final int near = context.indexOf(doc.folder());
            for (final Skill skill : doc.parsed().skills()) {
                listed.add(new Listed(doc.doc(), doc.folder(), doc.folderPath(), skill, near));
            }
        }
        listed.sort(Comparator.comparingInt((Listed skill) -> skill.near() < 0
                        ? Integer.MAX_VALUE
                        : skill.near())
                .thenComparing(skill -> skill.folderPath().toLowerCase(Locale.ROOT))
                .thenComparing(skill -> skill.skill().name().toLowerCase(Locale.ROOT)));
        return List.copyOf(listed);
    }

    /// @param name A Documentation doc's name.
    /// @return True if it is a skills doc's name: `SKILLS` (ignoring case, as the name lookup does).
    public static boolean isSkillsDocName(final String name) {
        return SkillDoc.DOC_NAME.equalsIgnoreCase(NullSafe.trim(name));
    }

    /// @return Every skills doc the user can view, read.
    public List<SkillsDoc> readableDocs() {
        final List<DocRef> found;
        try {
            // By name only: no other Documentation doc is read. Allowing wild cards makes the match ignore case.
            found = NullSafe.list(docFinderProvider.get().findByName(DocumentationDoc.TYPE, SkillDoc.DOC_NAME,
                    true));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to list the Documentation docs", e);
            return List.of();
        }
        final List<SkillsDoc> docs = new ArrayList<>();
        final long nowMs = System.currentTimeMillis();
        for (final DocRef docRef : found) {
            if (!isSkillsDocName(docRef.getName()) || !documentAccess.canView(docRef)) {
                continue;
            }
            final CachedDoc cached = cache.get(docRef.getUuid());
            final SkillsDoc doc;
            if (cached != null && nowMs - cached.readAtMs() < CACHE_MS) {
                doc = cached.doc();
            } else {
                doc = read(docRef, cached).orElse(null);
                cache.put(docRef.getUuid(), new CachedDoc(doc, nowMs));
            }
            if (doc != null) {
                docs.add(doc);
            }
        }
        return List.copyOf(docs);
    }

    /// @param docRef A Documentation doc.
    /// @return It, read as a skills doc, if it is one the user can view.
    public Optional<SkillsDoc> readDoc(final DocRef docRef) {
        return read(docRef, null);
    }

    /// Forgets the docs read, so the next question reads them again (e.g. after a test changes one).
    public void clearCache() {
        cache.clear();
    }

    /// @param chatId A chat.
    /// @return The folders of its latest question's context: the folder of the document of the tab attached (or the
    /// folder itself), then each folder above it, to the top. Empty if no tab is attached.
    public List<DocRef> contextFolders(final int chatId) {
        try {
            return tabSnapshotsProvider.get().forLatestQuestion(chatId)
                    .map(TabSnapshots.StoredTabSnapshot::docRef)
                    .map(this::foldersOf)
                    .orElse(List.of());
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the context of chat " + chatId, e);
            return List.of();
        }
    }

    /// @param docRef A document or folder.
    /// @return Its folder (or itself, for a folder), then each folder above it, to the top.
    public List<DocRef> foldersOf(final DocRef docRef) {
        final List<DocRef> folders = new ArrayList<>();
        DocRef current = ExplorerConstants.isFolder(docRef)
                ? docRef
                : documentAccess.parent(docRef).orElse(null);
        final Set<String> seen = new HashSet<>();
        while (current != null && seen.add(current.getUuid())) {
            folders.add(current);
            current = documentAccess.parent(current).orElse(null);
        }
        return folders;
    }

    /// @param chatId A chat.
    /// @return The skills list for the prompt of its latest question, with the skills that matched it, if the user
    /// can read any skills.
    public Optional<Prompt> prompt(final int chatId) {
        final List<DocRef> context = contextFolders(chatId);
        final List<Listed> skills = readable(context);
        if (skills.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(prompt(skills, latestQuestion(chatId)));
    }

    /// Records the skills a question was offered, once a question.
    ///
    /// @param chatId A chat.
    /// @param prompt The list it was given.
    public void recordOffered(final int chatId, final Prompt prompt) {
        if (prompt.offered().isEmpty()) {
            return;
        }
        try {
            final AiService aiService = aiServiceProvider.get();
            final List<AiChatMessage> messages = aiService.getMessages(chatId);
            for (final AiChatMessage message : messages.reversed()) {
                if (message.getMessageType() == AiMessageType.SKILLS_OFFERED) {
                    return;
                }
                if (message.getMessageType() == AiMessageType.USER_MESSAGE) {
                    break;
                }
            }
            aiService.storeMessage(chatId, AiMessageType.SKILLS_OFFERED, SkillUsage.offeredMessage(prompt.offered()));
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to record the skills offered in chat " + chatId, e);
        }
    }

    /// @param skills   Every skill the user can read, near the context first.
    /// @param question The question, or null.
    /// @return The list for the prompt: all of them under the size, else the near ones and those sharing words with
    /// the question, up to it, then how many more there are.
    static Prompt prompt(final List<Listed> skills, final String question) {
        final Set<String> asked = SkillChecks.words(question);
        final List<String> all = skills.stream().map(SkillCatalogue::line).toList();
        final int allChars = all.stream().mapToInt(line -> line.length() + 1).sum();
        final List<Listed> listed;
        if (skills.size() <= MAX_LISTED && allChars <= MAX_LIST_CHARS) {
            listed = skills;
        } else {
            final List<Listed> candidates = new ArrayList<>(skills.stream().filter(skill -> skill.near() >= 0)
                    .toList());
            skills.stream()
                    .filter(skill -> skill.near() < 0)
                    .filter(skill -> score(skill, asked, true) > 0)
                    .sorted(Comparator.comparingInt((Listed skill) -> score(skill, asked, true)).reversed())
                    .forEach(candidates::add);
            listed = new ArrayList<>();
            int chars = 0;
            for (final Listed skill : candidates) {
                final int length = line(skill).length() + 1;
                if (listed.size() >= MAX_LISTED || chars + length > MAX_LIST_CHARS) {
                    break;
                }
                listed.add(skill);
                chars += length;
            }
        }
        final StringBuilder sb = new StringBuilder(HEADING).append("\n\n").append(INTRO).append('\n');
        listed.forEach(skill -> sb.append(line(skill)).append('\n'));
        final int more = skills.size() - listed.size();
        if (more > 0) {
            sb.append("… ").append(more).append(" more: search them with ").append(FIND_SKILLS)
                    .append(" words=\n");
        }
        // Offered: the near ones, and those whose description or "When to use" share words with the question.
        final List<String> offered = listed.stream()
                .filter(skill -> skill.near() >= 0 || score(skill, asked, false) > 0)
                .map(skill -> skill.skill().name().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        return new Prompt(sb.toString().stripTrailing(), listed.size(), more, offered);
    }

    /// @param skill    A skill.
    /// @param asked    The question's words.
    /// @param withName True to count its name's words too.
    /// @return How many of the question's words its description and "When to use" (and name) share.
    static int score(final Listed skill, final Set<String> asked, final boolean withName) {
        final Set<String> words = SkillChecks.words(skill.skill().description() + " "
                                                    + String.join(" ", skill.skill().whenToUse())
                                                    + (withName
                                                            ? " " + skill.skill().name()
                                                            : ""));
        words.retainAll(asked);
        return words.size();
    }

    /// @param skill A skill.
    /// @return Its line in the list.
    static String line(final Listed skill) {
        final String description = NullSafe.nonBlankStringElse(skill.skill().description(), "(no description)");
        return "- " + skill.skill().name() + " (" + skill.folderPath() + ")" + (skill.near() >= 0
                ? NEAR
                : "") + ": " + (description.length() > MAX_DESCRIPTION_CHARS
                ? description.substring(0, MAX_DESCRIPTION_CHARS - 1) + "…"
                : description);
    }

    private String latestQuestion(final int chatId) {
        try {
            for (final AiChatMessage message : aiServiceProvider.get().getMessages(chatId).reversed()) {
                if (message.getMessageType() == AiMessageType.USER_MESSAGE) {
                    return message.getMessage();
                }
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> "Unable to read the latest question of chat " + chatId, e);
        }
        return null;
    }

    private Optional<SkillsDoc> read(final DocRef docRef, final CachedDoc cached) {
        final Optional<DocumentationDoc> read = documentAccess.read(docRef, DocumentationDoc.class);
        if (read.isEmpty()) {
            return Optional.empty();
        }
        final DocumentationDoc doc = read.get();
        if (cached != null && cached.doc() != null && doc.getVersion() != null
            && doc.getVersion().equals(cached.doc().version())) {
            return Optional.of(cached.doc());
        }
        if (!isSkillsDocName(doc.getName())) {
            return Optional.empty();
        }
        final SkillDoc parsed = SkillDoc.parse(doc.getData());
        final DocRef named = docRef.copy().name(doc.getName()).build();
        final DocRef folder = documentAccess.parent(named).orElse(ExplorerConstants.SYSTEM_DOC_REF);
        // A document's path is that of its folder (the folders above it).
        return Optional.of(new SkillsDoc(named, folder, documentAccess.path(named), doc.getVersion(), parsed));
    }


    // --------------------------------------------------------------------------------


    /// A skills doc the user can view.
    ///
    /// @param doc        The doc.
    /// @param folder     Its folder.
    /// @param folderPath Its folder's path.
    /// @param version    Its version, as read.
    /// @param parsed     It, read.
    public record SkillsDoc(DocRef doc, DocRef folder, String folderPath, String version, SkillDoc parsed) {

    }

    /// A skill the user can read.
    ///
    /// @param doc        Its doc.
    /// @param folder     Its doc's folder.
    /// @param folderPath Its folder's path.
    /// @param skill      The skill.
    /// @param near       Where its folder is among the folders of the question's context, 0 the most specific; -1
    ///                   if it isn't one.
    public record Listed(DocRef doc, DocRef folder, String folderPath, Skill skill, int near) {

    }

    /// The skills list for a question's prompt.
    ///
    /// @param text    The list, with its heading.
    /// @param listed  How many skills it lists.
    /// @param more    How many more there are.
    /// @param offered The names (in lower case) of those listed that matched the question.
    public record Prompt(String text, int listed, int more, List<String> offered) {

    }

    private record CachedDoc(SkillsDoc doc, long readAtMs) {

    }
}
