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

package stroom.ai.impl.tools.timeline;

import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.ai.impl.tools.timeline.EventChains.Entry;
import stroom.ai.impl.tools.timeline.PairInference.Candidate;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.docref.DocRef;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/// What a timeline run learned of its sources that its skill doesn't hold yet (pairs inferred and used, the call's
/// own pairs and type roles, which are the user's corrections), and the offer to record it in the timeline skill's
/// `## Event chains` section through `propose_skill`'s diff, so the next run applies it rather than infers it again.
/// Offered only when the user may change the skills doc: Edit on the doc holding the skill in use (as "Review and
/// apply" needs), or Create in a folder for a new skill; otherwise what was learned is kept for the chat and the
/// result names who could record it, with the text to give them. Borderline pairs are listed for the user to
/// confirm, never recorded unasked.
final class TimelineLearning {

    /// The skill's markdown with the entries added, for `propose_skill`'s `draft`.
    static final String SKILL_FILE = "skill-event-chains.md";
    /// What was learned, kept for the chat.
    static final String CHAINS_FILE = "event-chains.md";
    /// The template a new timeline skill starts from.
    static final String TEMPLATE = "user-activity-timeline";

    private TimelineLearning() {
        // Static only.
    }

    /// Works out what to offer.
    ///
    /// @param entries     What was learned, as the section's entries.
    /// @param borderline  The borderline pairs, with the source's index name each.
    /// @param skill       The skill in use, or null.
    /// @param createIn    A folder the user can create a skills doc in (for a new skill), or null.
    /// @param workspace   The chat's workspace.
    /// @param folder      The timeline's folder in it.
    /// @return What was learned and the offer.
    static Learned learn(final List<Entry> entries,
                         final List<NamedCandidate> borderline,
                         final SkillInUse skill,
                         final CreateIn createIn,
                         final SessionWorkspace workspace,
                         final String folder) {
        final List<Entry> fresh = new ArrayList<>();
        for (final Entry entry : entries) {
            if ((skill == null || !skill.has(entry)) && !fresh.contains(entry)) {
                fresh.add(entry);
            }
        }
        final List<String> borderlineLines = borderline.stream().map(TimelineLearning::describe).toList();
        if (fresh.isEmpty()) {
            return new Learned(List.of(), borderlineLines, null, false, borderline.isEmpty()
                    ? null
                    : "Borderline pairs were found (listed): ask the user whether any is a real pair; one they "
                      + "confirm can be given as the source's pairs and then recorded in the skill.");
        }
        final List<String> lines = fresh.stream().map(Entry::line).toList();
        if (skill != null && skill.listed() != null && skill.canEdit()) {
            final WorkspacePath path = WorkspacePath.parse(folder + SKILL_FILE);
            workspace.write(path, EventChains.withEntries(skill.listed().skill().text(), fresh), null);
            return new Learned(lines, borderlineLines, path, true, "Offer to record what was learned in the skill '"
                                                                     + skill.listed().skill().name() + "' (its Event "
                                                                     + "chains section), so the next run applies it; "
                                                                     + "if the user agrees: propose_skill "
                                                                     + "action=propose doc="
                                                                     + skill.listed().doc().getUuid() + " draft="
                                                                     + path + " (the skill with these lines added, "
                                                                     + "shown to them as a diff).");
        }
        if ((skill == null || skill.listed() == null) && createIn != null) {
            final WorkspacePath path = WorkspacePath.parse(folder + CHAINS_FILE);
            workspace.write(path, String.join("\n", lines) + "\n", null);
            return new Learned(lines, borderlineLines, path, true, "Offer to save a timeline skill holding what was "
                                                                     + "learned, so the next run applies it: "
                                                                     + "propose_skill action=template name="
                                                                     + TEMPLATE + ", with these lines in its Event "
                                                                     + "chains section, proposed in folder '"
                                                                     + createIn.path() + "' (if the user agrees).");
        }
        final WorkspacePath path = WorkspacePath.parse(folder + CHAINS_FILE);
        workspace.write(path, String.join("\n", lines) + "\n", null);
        final String who = skill != null && skill.listed() != null
                ? "someone with Edit on the skills doc '" + skill.listed().doc().getName() + "' in folder '"
                  + skill.listed().folderPath() + "' (its owner, or an administrator)"
                : "someone who can create a Documentation doc in a folder where the timeline's users find skills "
                  + "(the folder's owner, or an administrator)";
        return new Learned(lines, borderlineLines, path, false, "What was learned is kept for this chat (" + path
                                                                 + "): you can't change the skills doc, so give "
                                                                 + who + " these lines for its Event chains "
                                                                 + "section.");
    }

    /// @param candidate A borderline pair and its source's index.
    /// @return It with its figures, for the user to confirm.
    static String describe(final NamedCandidate candidate) {
        final Candidate c = candidate.candidate();
        return candidate.index() + ": " + c.start() + " -> " + c.end() + (c.by() == null
                ? " by host"
                : " by " + c.by()) + ": alternating for " + c.percent() + "% of " + c.keys() + " key(s), "
               + c.startCount() + " starts and " + c.endCount() + " ends"
               + (c.medianGapMs() > 0
                ? ", the middle span " + Math.round(c.medianGapMs() / 60_000.0) + " minute(s)"
                : "");
    }


    // --------------------------------------------------------------------------------


    /// The skill in use.
    ///
    /// @param name    The name the call gave.
    /// @param listed  The skill, or null if none of that name can be read.
    /// @param chains  Its Event chains.
    /// @param canEdit True if the user has Edit on its doc.
    record SkillInUse(String name, Listed listed, EventChains.Parsed chains, boolean canEdit) {

        /// @param entry An entry.
        /// @return True if the skill holds it already.
        boolean has(final Entry entry) {
            final String line = entry.line().toLowerCase(Locale.ROOT);
            return chains.entries().stream().anyMatch(held -> held.line().toLowerCase(Locale.ROOT).equals(line));
        }
    }


    // --------------------------------------------------------------------------------


    /// A folder a new skills doc may be created in.
    ///
    /// @param folder The folder.
    /// @param path   Its path.
    record CreateIn(DocRef folder, String path) {

    }


    // --------------------------------------------------------------------------------


    /// A borderline pair and its source's index name.
    ///
    /// @param index     The index's name.
    /// @param candidate The pair.
    record NamedCandidate(String index, Candidate candidate) {

    }


    // --------------------------------------------------------------------------------


    /// What was learned.
    ///
    /// @param lines      The entries the skill doesn't hold yet, as lines of its Event chains section.
    /// @param borderline The borderline pairs, each with its figures, for the user to confirm.
    /// @param file       The file written: the skill with the lines added, or the lines alone; null for none.
    /// @param canRecord  True if the user may record it themselves.
    /// @param offer      What the agent is to offer or say, or null for nothing.
    record Learned(List<String> lines, List<String> borderline, WorkspacePath file, boolean canRecord,
                   String offer) {

        /// Copies the lists.
        Learned {
            lines = List.copyOf(lines);
            borderline = List.copyOf(borderline);
        }
    }
}
