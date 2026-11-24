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

package stroom.ai.impl.tools.skills;

import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.skills.SkillCatalogue.Listed;
import stroom.ai.impl.tools.skills.SkillCatalogue.SkillsDoc;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerConstants;
import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class TestCheckSkillTool {

    private static final DocRef HUNTS = new DocRef(ExplorerConstants.FOLDER_TYPE, "hunts", "Hunts");
    private static final DocRef SKILLS = new DocRef(DocumentationDoc.TYPE, "skills", SkillDoc.DOC_NAME);
    private static final DocRef OTHER = new DocRef(DocumentationDoc.TYPE, "other", "Hunting procedures");

    private final SkillCatalogue catalogue = Mockito.mock(SkillCatalogue.class);
    private final SkillFacts facts = Mockito.mock(SkillFacts.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private CheckSkillTool tool;

    @BeforeEach
    void setUp() {
        final SkillDoc doc = SkillDoc.parse(TestSkillDoc.TIMELINE);
        Mockito.when(catalogue.readable(Mockito.any())).thenReturn(List.of(
                new Listed(SKILLS, HUNTS, "/Hunts", doc.skills().getFirst(), -1)));
        Mockito.when(catalogue.readDoc(SKILLS)).thenReturn(Optional.of(new SkillsDoc(SKILLS, HUNTS, "/Hunts", "1",
                doc)));
        Mockito.when(documentAccess.findDocRef("skills")).thenReturn(Optional.of(SKILLS));
        Mockito.when(documentAccess.findDocRef("other")).thenReturn(Optional.of(OTHER));
        Mockito.when(facts.forSkill(Mockito.any(), Mockito.any(), Mockito.anyBoolean()))
                .thenReturn(SkillTestFacts.permissive());
        Mockito.when(facts.forSkill(Mockito.any(), Mockito.any(), Mockito.anyBoolean(), Mockito.any()))
                .thenReturn(SkillTestFacts.permissive());
        tool = new CheckSkillTool(catalogue, facts, documentAccess);
    }

    @Test
    void aSavedSkillIsCheckedByName() {
        assertThat(run(Map.of("name", "User activity timeline")))
                .startsWith("Skill 'User activity timeline' (folder /Hunts):")
                .contains("parameter_unstated");
    }

    @Test
    void aSkillsDocsSkillsAreAllChecked() {
        assertThat(run(Map.of("doc", "skills"))).startsWith("Skills doc in folder /Hunts:")
                .contains("Skill 'User activity timeline':");
    }

    @Test
    void aDocNotNamedSkillsIsToldToBeRenamed() {
        assertThat(run(Map.of("doc", "other"))).startsWith("'Hunting procedures' isn't a skills doc")
                .contains("rename it SKILLS");
        Mockito.verify(catalogue, Mockito.never()).readDoc(OTHER);
    }

    @Test
    void aDraftIsChecked() {
        assertThat(run(Map.of("markdown", "# Draft\n\nDoes a thing.\n"))).startsWith("Draft skill 'Draft':")
                .contains("no_steps");
    }

    private String run(final Map<String, Object> arguments) {
        return tool.execute(new ToolCall("c1", CheckSkillTool.NAME, JsonUtil.writeValueAsString(arguments)),
                ToolCalls.outsideChat()).text();
    }
}
