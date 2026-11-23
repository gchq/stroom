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
import stroom.ai.impl.tools.SkillCatalogue.Listed;
import stroom.ai.impl.tools.SkillCatalogue.Prompt;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TestSkillCatalogue {

    private static final DocRef ROOT = ExplorerConstants.SYSTEM_DOC_REF;
    private static final DocRef SECURITY = new DocRef(ExplorerConstants.FOLDER_TYPE, "security", "Security");
    private static final DocRef HUNTS = new DocRef(ExplorerConstants.FOLDER_TYPE, "hunts", "Hunts");
    private static final DocRef TOP_SKILLS = new DocRef(DocumentationDoc.TYPE, "top", "SKILLS");
    private static final DocRef HUNT_SKILLS = new DocRef(DocumentationDoc.TYPE, "hunt", "SKILLS");
    private static final DocRef PREFIXED = new DocRef(DocumentationDoc.TYPE, "prefixed", "SKILLS - timelines");
    private static final DocRef MARKED_ONLY = new DocRef(DocumentationDoc.TYPE, "marked", "Hunting procedures");
    private static final DocRef SECRET = new DocRef(DocumentationDoc.TYPE, "secret", "SKILLS");
    private static final DocRef NOTES = new DocRef(DocumentationDoc.TYPE, "notes", "Notes");

    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private SkillCatalogue catalogue;

    @BeforeEach
    void setUp() {
        // Found by name; a finder that also gives docs of other names (it shouldn't) has them left unread.
        Mockito.when(docFinder.findByName(DocumentationDoc.TYPE, "SKILLS", true))
                .thenReturn(List.of(TOP_SKILLS, HUNT_SKILLS, SECRET, NOTES, MARKED_ONLY, PREFIXED));
        doc(TOP_SKILLS, ROOT, "/", "# Timeline\n\nBuilds a user's timeline.\n\n# Who did it\n\nFinds who of a "
                                   + "group did something.\n");
        // Named as a skills doc, with its front matter.
        doc(HUNT_SKILLS, HUNTS, "/Security/Hunts", "---\nstroom: skills\n---\n\n# Timeline\n\nThe hunters' "
                                                  + "timeline, by host.\n");
        // Not skills docs: not named SKILLS, though one has the front matter and one starts with the name.
        doc(NOTES, ROOT, "/", "# Notes\n\nNot a skill.\n");
        doc(MARKED_ONLY, ROOT, "/", "---\nstroom: skills\n---\n\n# Hidden\n\nNot found.\n");
        doc(PREFIXED, ROOT, "/", "---\nstroom: skills\n---\n\n# Hidden too\n\nNot found.\n");
        // One the user can't view.
        Mockito.when(documentAccess.canView(SECRET)).thenReturn(false);
        Mockito.when(documentAccess.parent(HUNTS)).thenReturn(Optional.of(SECURITY));
        Mockito.when(documentAccess.parent(SECURITY)).thenReturn(Optional.of(ROOT));
        catalogue = new SkillCatalogue(() -> docFinder, documentAccess, () -> Mockito.mock(TabSnapshots.class),
                () -> Mockito.mock(AiService.class));
    }

    @Test
    void everyReadableSkillIsListedAndNoOther() {
        final List<Listed> readable = catalogue.readable(List.of());

        assertThat(readable).extracting(skill -> skill.skill().name() + " @ " + skill.folderPath())
                .containsExactly("Timeline @ /", "Who did it @ /", "Timeline @ /Security/Hunts");
        Mockito.verify(documentAccess, Mockito.never()).read(SECRET, DocumentationDoc.class);
    }

    @Test
    void aDocNotNamedSkillsIsNeverReadEvenWithTheFrontMatter() {
        catalogue.readable(List.of());

        Mockito.verify(documentAccess, Mockito.never()).read(NOTES, DocumentationDoc.class);
        Mockito.verify(documentAccess, Mockito.never()).read(MARKED_ONLY, DocumentationDoc.class);
        Mockito.verify(documentAccess, Mockito.never()).read(PREFIXED, DocumentationDoc.class);
        Mockito.verify(docFinder, Mockito.never()).findByName(DocumentationDoc.TYPE, "*", true);
        assertThat(SkillCatalogue.isSkillsDocName("skills")).isTrue();
        assertThat(SkillCatalogue.isSkillsDocName("SKILLS - timelines")).isFalse();
        assertThat(SkillCatalogue.isSkillsDocName("My SKILLS")).isFalse();
    }

    @Test
    void skillsNearTheContextComeFirstTheMostSpecificFirst() {
        final List<DocRef> context = catalogue.foldersOf(new DocRef("Feed", "feed", "A feed"));

        assertThat(context).containsExactly(HUNTS, SECURITY, ROOT);
        final List<Listed> readable = catalogue.readable(context);
        assertThat(readable).extracting(skill -> skill.skill().name() + " @ " + skill.folderPath())
                .containsExactly("Timeline @ /Security/Hunts", "Timeline @ /", "Who did it @ /");

        final Prompt prompt = SkillCatalogue.prompt(readable, "a timeline for jsmith");
        assertThat(prompt.text()).startsWith(SkillCatalogue.HEADING + "\n\n" + SkillCatalogue.INTRO + "\n"
                                             + "- Timeline (/Security/Hunts) [near this context]: The hunters' "
                                             + "timeline, by host.\n");
        assertThat(prompt.listed()).isEqualTo(3);
        assertThat(prompt.more()).isZero();
    }

    @Test
    void theSkillsThatMatchTheQuestionAreRecordedAsOffered() {
        final Prompt prompt = SkillCatalogue.prompt(catalogue.readable(List.of()), "who in the group did it?");

        assertThat(prompt.offered()).containsExactly("who did it");
    }

    @Test
    void overTheSizeTheNearAndMatchingAreListedThenHowManyMore() {
        final List<Listed> many = new ArrayList<>();
        IntStream.range(0, 80).forEach(i -> many.add(listed("Skill " + i, "Does thing number " + i + ".", -1)));
        many.add(listed("Firewall denies", "Counts the firewall's denied connections by host.", -1));
        many.addFirst(listed("Near one", "Something near.", 0));

        final Prompt prompt = SkillCatalogue.prompt(many, "how many firewall denies yesterday?");

        assertThat(prompt.text()).contains("- Near one (/) [near this context]: Something near.",
                "- Firewall denies (/): Counts", "… 80 more: search them with find_skills words=");
        assertThat(prompt.listed()).isEqualTo(2);
        assertThat(prompt.more()).isEqualTo(80);
    }

    @Test
    void underTheSizeEveryoneIsListed() {
        final List<Listed> some = IntStream.range(0, SkillCatalogue.MAX_LISTED)
                .mapToObj(i -> listed("Skill " + i, "Does " + i + ".", -1))
                .toList();

        final Prompt prompt = SkillCatalogue.prompt(some, "anything");

        assertThat(prompt.listed()).isEqualTo(SkillCatalogue.MAX_LISTED);
        assertThat(prompt.text()).doesNotContain("more: search");
    }

    @Test
    void aLongDescriptionIsCut() {
        final String line = SkillCatalogue.line(listed("Long", "x".repeat(500), -1));

        assertThat(line).hasSize("- Long (/): ".length() + SkillCatalogue.MAX_DESCRIPTION_CHARS).endsWith("…");
    }

    @Test
    void aDocIsReadOnceWhileItsCacheIsFresh() {
        catalogue.readable(List.of());
        catalogue.readable(List.of());

        Mockito.verify(documentAccess, Mockito.times(1)).read(TOP_SKILLS, DocumentationDoc.class);
    }

    private static Listed listed(final String name, final String description, final int near) {
        return new Listed(TOP_SKILLS, ROOT, "/", SkillDoc.parse("# " + name + "\n\n" + description + "\n")
                .skills().getFirst(), near);
    }

    private void doc(final DocRef docRef, final DocRef folder, final String folderPath, final String text) {
        Mockito.when(documentAccess.canView(docRef)).thenReturn(true);
        Mockito.when(documentAccess.read(docRef, DocumentationDoc.class)).thenReturn(Optional.of(
                DocumentationDoc.builder().uuid(docRef.getUuid()).name(docRef.getName()).version("1").data(text)
                        .build()));
        Mockito.when(documentAccess.parent(Mockito.argThat(ref -> ref != null
                                                                  && docRef.getUuid().equals(ref.getUuid()))))
                .thenReturn(Optional.of(folder));
        // A document's path is its folder's: the folders above it.
        Mockito.when(documentAccess.path(docRef)).thenReturn(folderPath);
        Mockito.when(documentAccess.parent(new DocRef("Feed", "feed", "A feed"))).thenReturn(Optional.of(HUNTS));
    }
}
