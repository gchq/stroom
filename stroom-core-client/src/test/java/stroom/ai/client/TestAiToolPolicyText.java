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

    private static final List<AiToolSummary> TOOLS = List.of(
            new AiToolSummary("search", AiToolCategory.SEARCH, true, "Searches"),
            new AiToolSummary("find_documents", AiToolCategory.CORE, true, "Finds documents"),
            new AiToolSummary("create_document", AiToolCategory.CORE, false, "Creates a document"),
            new AiToolSummary("memory", AiToolCategory.CORE, false, true, "Remembers things"),
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
    void testCategories_inDeclaredOrder() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .categories(List.of(AiToolCategory.ADMINISTRATION, AiToolCategory.CORE, AiToolCategory.SEARCH))
                .build();
        assertThat(AiToolPolicyText.categories(policy)).isEqualTo("Core, Search, Administration");
    }

    @Test
    void testGrantedTools_notFetched() {
        assertThat(AiToolPolicyText.grantedTools(List.of(AiToolCategory.CORE), AiToolAccess.READ_ONLY, null)
                .asString())
                .contains("Fetching");
    }

    @Test
    void testGrantedTools_noCategories() {
        assertThat(AiToolPolicyText.grantedTools(List.of(), AiToolAccess.READ_ONLY, TOOLS).asString())
                .contains("Tick a category");
    }

    @Test
    void testGrantedTools_readOnly() {
        final String html = AiToolPolicyText.grantedTools(
                List.of(AiToolCategory.CORE), AiToolAccess.READ_ONLY, TOOLS).asString();
        assertThat(html)
                .contains("Core")
                .contains("<li>find_documents</li>")
                .contains("<li>create_document (not granted")
                // Granted for its calls that can't change anything.
                .contains("<li>memory (only its calls that cannot change anything")
                .doesNotContain("search")
                .doesNotContain("set_property");
        // Sorted by name
        assertThat(html.indexOf("create_document")).isLessThan(html.indexOf("find_documents"));
    }

    @Test
    void testGrantedTools_readWrite() {
        final String html = AiToolPolicyText.grantedTools(
                List.of(AiToolCategory.ADMINISTRATION, AiToolCategory.CORE), AiToolAccess.READ_WRITE, TOOLS)
                .asString();
        assertThat(html)
                .contains("<li>create_document</li>")
                .contains("<li>set_property</li>")
                .doesNotContain("not granted");
        // Categories in the order they are declared.
        assertThat(html.indexOf("Core")).isLessThan(html.indexOf("Administration"));
    }

    @Test
    void testGrantedTools_categoryWithNoTools() {
        assertThat(AiToolPolicyText.grantedTools(
                List.of(AiToolCategory.INDEXING), AiToolAccess.READ_WRITE, TOOLS).asString())
                .contains("Indexing")
                .contains("No tools.");
    }

    @Test
    void testGrantedTools_escapesNames() {
        final List<AiToolSummary> tools = List.of(
                new AiToolSummary("<b>bad</b>", AiToolCategory.CORE, true, "Bad"));
        assertThat(AiToolPolicyText.grantedTools(List.of(AiToolCategory.CORE), AiToolAccess.READ_ONLY, tools)
                .asString())
                .contains("&lt;b&gt;bad&lt;/b&gt;")
                .doesNotContain("<b>bad</b>");
    }

    @Test
    void testValidationError_valid() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .name("Analysts")
                .categories(List.of(AiToolCategory.SEARCH))
                .build();
        assertThat(AiToolPolicyText.validationError(policy)).isNull();
    }

    @Test
    void testValidationError_noName() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .categories(List.of(AiToolCategory.SEARCH))
                .build();
        assertThat(AiToolPolicyText.validationError(policy)).contains("name");
    }

    @Test
    void testValidationError_blankName() {
        final AiToolPolicy policy = AiToolPolicy.builder()
                .name("   ")
                .categories(List.of(AiToolCategory.SEARCH))
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
