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

package stroom.ai.client;

import stroom.ai.shared.AiToolAccess;
import stroom.ai.shared.AiToolCategory;
import stroom.ai.shared.AiToolCategoryGrant;
import stroom.ai.shared.AiToolEffectiveGrant;
import stroom.ai.shared.AiToolPolicy;
import stroom.ai.shared.AiToolSummary;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestAiToolPolicyText {

    private static final UserRef ALICE = UserRef.builder()
            .uuid("alice-uuid")
            .displayName("alice")
            .user()
            .build();
    private static final UserRef ADMINS = UserRef.builder()
            .uuid("admins-uuid")
            .displayName("Admins")
            .group()
            .build();

    private static final AiToolSummary FIND_DOCUMENTS =
            new AiToolSummary("find_documents", AiToolCategory.CORE, true, "Finds documents");
    private static final AiToolSummary CREATE_DOCUMENT =
            new AiToolSummary("create_document", AiToolCategory.CORE, false, "Creates a document");
    private static final AiToolSummary MEMORY =
            new AiToolSummary("memory", AiToolCategory.CORE, false, true, "Remembers things");
    private static final List<AiToolSummary> TOOLS = List.of(
            new AiToolSummary("search", AiToolCategory.SEARCH, true, "Searches"),
            FIND_DOCUMENTS,
            CREATE_DOCUMENT,
            MEMORY,
            new AiToolSummary("set_property", AiToolCategory.ADMINISTRATION, false, "Sets a property"));

    @Test
    void testCovers_allUsers() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .allUsers(true)
                .members(List.of(ALICE))
                .build();
        assertThat(AiToolPolicyText.covers(policy)).isEqualTo(AiToolPolicyText.ALL_USERS);
    }

    @Test
    void testCovers_members() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .members(List.of(ALICE, ADMINS))
                .build();
        assertThat(AiToolPolicyText.covers(policy)).isEqualTo("alice, Admins (group)");
    }

    @Test
    void testCovers_noMembers() {
        assertThat(AiToolPolicyText.covers(AiToolPolicy.builder().build())).isEmpty();
    }

    @Test
    void testToolsIn_onlyTheCategorysToolsByName() {
        assertThat(AiToolPolicyText.toolsIn(AiToolCategory.CORE, TOOLS))
                .extracting(AiToolSummary::getName)
                .containsExactly("create_document", "find_documents", "memory");
        assertThat(AiToolPolicyText.toolsIn(AiToolCategory.INDEXING, TOOLS)).isEmpty();
        assertThat(AiToolPolicyText.toolsIn(AiToolCategory.CORE, null)).isEmpty();
    }

    @Test
    void testIsGranted() {
        assertThat(AiToolPolicyText.isGranted(CREATE_DOCUMENT, AiToolAccess.READ_WRITE)).isTrue();
        assertThat(AiToolPolicyText.isGranted(CREATE_DOCUMENT, AiToolAccess.READ_ONLY)).isFalse();
        assertThat(AiToolPolicyText.isGranted(FIND_DOCUMENTS, AiToolAccess.READ_ONLY)).isTrue();
        // Granted for its calls that can't change anything.
        assertThat(AiToolPolicyText.isGranted(MEMORY, AiToolAccess.READ_ONLY)).isTrue();
    }

    @Test
    void testCanReadAndCanWrite_readAndWrite() {
        // Calls that read and calls that write.
        assertThat(AiToolPolicyText.canRead(MEMORY, AiToolAccess.READ_WRITE)).isTrue();
        assertThat(AiToolPolicyText.canWrite(MEMORY, AiToolAccess.READ_WRITE)).isTrue();
        // Only reads: nothing to show for writing.
        assertThat(AiToolPolicyText.canRead(FIND_DOCUMENTS, AiToolAccess.READ_WRITE)).isTrue();
        assertThat(AiToolPolicyText.canWrite(FIND_DOCUMENTS, AiToolAccess.READ_WRITE)).isNull();
        // Only writes: nothing to show for reading.
        assertThat(AiToolPolicyText.canRead(CREATE_DOCUMENT, AiToolAccess.READ_WRITE)).isNull();
        assertThat(AiToolPolicyText.canWrite(CREATE_DOCUMENT, AiToolAccess.READ_WRITE)).isTrue();
        // A category.
        assertThat(AiToolPolicyText.canRead(null, AiToolAccess.READ_WRITE)).isTrue();
        assertThat(AiToolPolicyText.canWrite(null, AiToolAccess.READ_WRITE)).isTrue();
    }

    @Test
    void testCanReadAndCanWrite_readOnly() {
        assertThat(AiToolPolicyText.canRead(MEMORY, AiToolAccess.READ_ONLY)).isTrue();
        assertThat(AiToolPolicyText.canWrite(MEMORY, AiToolAccess.READ_ONLY)).isFalse();
        assertThat(AiToolPolicyText.canRead(FIND_DOCUMENTS, AiToolAccess.READ_ONLY)).isTrue();
        assertThat(AiToolPolicyText.canWrite(FIND_DOCUMENTS, AiToolAccess.READ_ONLY)).isNull();
        assertThat(AiToolPolicyText.canRead(CREATE_DOCUMENT, AiToolAccess.READ_ONLY)).isNull();
        assertThat(AiToolPolicyText.canWrite(CREATE_DOCUMENT, AiToolAccess.READ_ONLY)).isFalse();
        assertThat(AiToolPolicyText.canRead(null, AiToolAccess.READ_ONLY)).isTrue();
        assertThat(AiToolPolicyText.canWrite(null, AiToolAccess.READ_ONLY)).isFalse();
    }

    @Test
    void testCanReadAndCanWrite_notGranted() {
        assertThat(AiToolPolicyText.canRead(MEMORY, null)).isFalse();
        assertThat(AiToolPolicyText.canWrite(MEMORY, null)).isFalse();
        assertThat(AiToolPolicyText.canWrite(FIND_DOCUMENTS, null)).isNull();
        assertThat(AiToolPolicyText.canRead(null, null)).isFalse();
        assertThat(AiToolPolicyText.canWrite(null, null)).isFalse();
    }

    @Test
    void testGrantedBy_readAndWrite() {
        final AiToolEffectiveGrant grant = new AiToolEffectiveGrant(AiToolCategory.CORE,
                List.of("Writers"),
                List.of("Default", "readers"));
        // The category's widest access, and all of a tool that changes things, come from the read and write
        // policies.
        assertThat(AiToolPolicyText.grantedBy(grant, null)).containsExactly("Writers");
        assertThat(AiToolPolicyText.grantedBy(grant, CREATE_DOCUMENT)).containsExactly("Writers");
        assertThat(AiToolPolicyText.grantedBy(grant, MEMORY)).containsExactly("Writers");
        // A tool that cannot change anything is granted by any of them.
        assertThat(AiToolPolicyText.grantedBy(grant, FIND_DOCUMENTS))
                .containsExactly("Default", "readers", "Writers");
    }

    @Test
    void testGrantedBy_readOnly() {
        final AiToolEffectiveGrant grant = new AiToolEffectiveGrant(AiToolCategory.CORE,
                List.of(),
                List.of("Default"));
        assertThat(AiToolPolicyText.grantedBy(grant, null)).containsExactly("Default");
        assertThat(AiToolPolicyText.grantedBy(grant, FIND_DOCUMENTS)).containsExactly("Default");
        // Only its calls that cannot change anything.
        assertThat(AiToolPolicyText.grantedBy(grant, MEMORY)).containsExactly("Default");
        // Not permitted at all.
        assertThat(AiToolPolicyText.grantedBy(grant, CREATE_DOCUMENT)).isEmpty();
        // Not granted.
        assertThat(AiToolPolicyText.grantedBy(null, FIND_DOCUMENTS)).isEmpty();
    }

    @Test
    void testMatches_nameOnlyIgnoringCase() {
        assertThat(AiToolPolicyText.matches(FIND_DOCUMENTS, "FIND")).isTrue();
        assertThat(AiToolPolicyText.matches(FIND_DOCUMENTS, " _doc ")).isTrue();
        // Its description is "Finds documents".
        assertThat(AiToolPolicyText.matches(FIND_DOCUMENTS, "finds")).isFalse();
        assertThat(AiToolPolicyText.matches(FIND_DOCUMENTS, "  ")).isFalse();
        assertThat(AiToolPolicyText.matches(FIND_DOCUMENTS, null)).isFalse();
    }

    @Test
    void testHighlight_everyMatchIgnoringCaseAndEscaped() {
        assertThat(AiToolPolicyText.highlight("Find <docs>, find more", "FIND").asString())
                .isEqualTo("<span class=\"findCell-highlight\">Find</span> &lt;docs&gt;, "
                           + "<span class=\"findCell-highlight\">find</span> more");
        assertThat(AiToolPolicyText.highlight("a<b", "").asString()).isEqualTo("a&lt;b");
        assertThat(AiToolPolicyText.highlight(null, "x").asString()).isEmpty();
    }

    @Test
    void testOneLine_joinsLinesWithTheTooltip() {
        assertThat(AiToolPolicyText.oneLine("Reads\n\n  \"streams\"", "read", "Full \"text\"").asString())
                .isEqualTo("<span title=\"Full &quot;text&quot;\">"
                           + "<span class=\"findCell-highlight\">Read</span>s &quot;streams&quot;</span>");
        assertThat(AiToolPolicyText.oneLine("x", null, null).asString()).isEqualTo("<span>x</span>");
    }

    @Test
    void testParagraphs_splitAtBlankLinesAndEscaped() {
        assertThat(AiToolPolicyText.paragraphs("Reads <b>streams</b>.\n\n  Then\nanswers.\n\n\n").asString())
                .isEqualTo("<p>Reads &lt;b&gt;streams&lt;/b&gt;.</p><p>Then<br>answers.</p>");
        assertThat(AiToolPolicyText.paragraphs(null).asString()).isEmpty();
    }

    @Test
    void testValidationError_valid() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .name("Analysts")
                .grants(List.of(AiToolCategoryGrant.of(AiToolCategory.SEARCH, AiToolAccess.READ_ONLY)))
                .build();
        assertThat(AiToolPolicyText.validationError(policy)).isNull();
    }

    @Test
    void testValidationError_noName() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .grants(List.of(AiToolCategoryGrant.of(AiToolCategory.SEARCH, AiToolAccess.READ_ONLY)))
                .build();
        assertThat(AiToolPolicyText.validationError(policy)).contains("name");
    }

    @Test
    void testValidationError_blankName() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .name("   ")
                .grants(List.of(AiToolCategoryGrant.of(AiToolCategory.SEARCH, AiToolAccess.READ_ONLY)))
                .build();
        assertThat(AiToolPolicyText.validationError(policy)).contains("name");
    }

    @Test
    void testValidationError_noCategories() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .name("Analysts")
                .build();
        assertThat(AiToolPolicyText.validationError(policy)).contains("category");
    }
}
