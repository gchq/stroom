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

package stroom.ai.impl.workspace;

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.WorkspaceArguments.Expansion;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestWorkspaceArguments {

    private static final int CHAT_ID = 7;

    @TempDir
    Path tempDir;

    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final WorkspaceArguments workspaceArguments = new WorkspaceArguments(workspace);
    private AiWorkspaceConfig limits = new AiWorkspaceConfig();

    @BeforeEach
    void setUp() {
        Mockito.when(workspace.limits()).thenAnswer(invocation -> limits);
        Mockito.when(workspace.withTimeLimit(Mockito.any()))
                .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(0).get());
    }

    @Test
    void testArgumentsWithoutAReferenceAreLeftAsTheyAre() {
        final String arguments = "{\"name\":\"x\",  \"count\": 2}";

        final Expansion expansion = workspaceArguments.expand(CHAT_ID, arguments);

        assertThat(expansion.arguments()).isEqualTo(arguments);
        assertThat(expansion.isExpanded()).isFalse();
        assertThat(expansion.error()).isNull();
        Mockito.verifyNoInteractions(workspace);
    }

    @Test
    void testArgumentsThatAreNotAnObjectAreLeftForTheToolToRefuse() {
        assertThat(workspaceArguments.expand(CHAT_ID, "{\"$workspace\": ").arguments())
                .isEqualTo("{\"$workspace\": ");
        assertThat(workspaceArguments.expand(CHAT_ID, "[\"$workspace:/outputs/a\"]").isExpanded()).isFalse();
        assertThat(workspaceArguments.expand(CHAT_ID, null).arguments()).isNull();
    }

    @Test
    void testAnObjectReferenceToJsonIsPutInAsJson() {
        file("/outputs/mapping.json", "{\"rules\": [{\"field\": \"src\"}]}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"name\":\"fw\",\"mapping\":{\"$workspace\":\"/outputs/mapping.json\"}}");

        assertThat(expansion.error()).isNull();
        assertThat(expansion.references()).isEqualTo(1);
        assertThat(expansion.arguments()).isEqualTo("{\"name\":\"fw\",\"mapping\":{\"rules\":[{\"field\":\"src\"}]}}");
    }

    @Test
    void testAStringReferenceToTextIsPutInAsText() {
        file("/outputs/translation.xsl", "<xsl:stylesheet/>\n");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"xslt\":\"$workspace:/outputs/translation.xsl\"}");

        assertThat(expansion.error()).isNull();
        assertThat(json(expansion).get("xslt").textValue()).isEqualTo("<xsl:stylesheet/>\n");
    }

    @Test
    void testAStringReferenceToJsonIsPutInAsJson() {
        file("/outputs/template.json", "{\"index_patterns\":[\"logs-*\"]}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"template\":\"$workspace:/outputs/template.json\"}");

        assertThat(json(expansion).get("template").get("index_patterns").get(0).textValue()).isEqualTo("logs-*");
    }

    @Test
    void testTextThatOnlyStartsLikeJsonIsPutInAsText() {
        file("/outputs/notes.txt", "{\"a\":1} and then some");
        file("/outputs/empty.txt", "");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":\"$workspace:/outputs/notes.txt\",\"b\":\"$workspace:/outputs/empty.txt\"}");

        assertThat(json(expansion).get("a").textValue()).isEqualTo("{\"a\":1} and then some");
        assertThat(json(expansion).get("b").textValue()).isEmpty();
    }

    @Test
    void testAJqQueryGivesPartOfTheFile() {
        file("/outputs/draft.json", "{\"mapping\":{\"a\":\"b\"},\"notes\":\"long\"}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"mapping\":{\"$workspace\":\"/outputs/draft.json\",\"jq\":\".mapping\"}}");

        assertThat(expansion.arguments()).isEqualTo("{\"mapping\":{\"a\":\"b\"}}");
    }

    @Test
    void testAJqQueryWithSeveralResultsGivesAnArrayOfThem() {
        file("/outputs/events.json", "{\"host\":\"fw1\"}\n{\"host\":\"fw2\"}\n");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"hosts\":{\"$workspace\":\"/outputs/events.json\",\"jq\":\".host\"}}");

        assertThat(expansion.arguments()).isEqualTo("{\"hosts\":[\"fw1\",\"fw2\"]}");
    }

    @Test
    void testNestedReferencesArePutIn() {
        file("/outputs/a.txt", "A");
        file("/outputs/b.json", "[1,2]");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"changes\":[{\"op\":\"set\",\"value\":\"$workspace:/outputs/a.txt\"},"
                + "{\"op\":\"add\",\"value\":{\"deep\":{\"$workspace\":\"/outputs/b.json\"}}}]}");

        assertThat(expansion.references()).isEqualTo(2);
        assertThat(expansion.arguments())
                .isEqualTo("{\"changes\":[{\"op\":\"set\",\"value\":\"A\"},"
                           + "{\"op\":\"add\",\"value\":{\"deep\":[1,2]}}]}");
    }

    @Test
    void testAReferenceInAFilesContentIsNotPutInAgain() {
        file("/outputs/a.json", "{\"next\":\"$workspace:/outputs/b.txt\"}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID, "{\"value\":\"$workspace:/outputs/a.json\"}");

        assertThat(expansion.references()).isEqualTo(1);
        assertThat(json(expansion).get("value").get("next").textValue()).isEqualTo("$workspace:/outputs/b.txt");
        Mockito.verify(workspace, Mockito.never()).localFile(CHAT_ID, WorkspacePath.parse("/outputs/b.txt"));
    }

    @Test
    void testAMissingFileIsRefused() {
        Mockito.when(workspace.size(CHAT_ID, WorkspacePath.parse("/outputs/gone.json")))
                .thenThrow(new WorkspaceException("There is no file /outputs/gone.json."));

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"changes\":[{\"$workspace\":\"/outputs/gone.json\"}]}");

        assertThat(expansion.error()).isEqualTo("changes[0]: There is no file /outputs/gone.json.");
        assertThat(expansion.isExpanded()).isFalse();
        assertThat(expansion.arguments()).isEqualTo("{\"changes\":[{\"$workspace\":\"/outputs/gone.json\"}]}");
    }

    @Test
    void testAFileThatIsNoLongerAvailableIsRefused() {
        // E.g. it has expired, or the user can no longer read its source document.
        Mockito.when(workspace.size(CHAT_ID, WorkspacePath.parse("/documents/pipeline.xml")))
                .thenThrow(new WorkspaceFileUnavailableException(3, "/documents/pipeline.xml",
                        "you no longer have access to its source document"));

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"xml\":\"$workspace:/documents/pipeline.xml\"}");

        assertThat(expansion.error())
                .startsWith("xml: ")
                .contains("/documents/pipeline.xml")
                .contains("no longer have access");
    }

    @Test
    void testAnInvalidPathIsRefused() {
        final Expansion expansion = workspaceArguments.expand(CHAT_ID, "{\"a\":\"$workspace:/elsewhere/x\"}");

        assertThat(expansion.error()).startsWith("a: ").contains("is not in the workspace");
        Mockito.verify(workspace, Mockito.never()).size(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAFileLargerThanTheLimitIsRefused() {
        limits = limits(10L);
        file("/outputs/big.txt", "x".repeat(11));

        final Expansion expansion = workspaceArguments.expand(CHAT_ID, "{\"a\":\"$workspace:/outputs/big.txt\"}");

        assertThat(expansion.error()).isEqualTo("a: /outputs/big.txt is 11 bytes, but a tool call's arguments may "
                                                + "be at most 10. Pass a part of it with \"jq\".");
        Mockito.verify(workspace, Mockito.never()).localFile(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testArgumentsLargerThanTheLimitOnceExpandedAreRefused() {
        limits = limits(30L);
        file("/outputs/a.txt", "x".repeat(10));
        file("/outputs/b.txt", "y".repeat(10));

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":\"$workspace:/outputs/a.txt\",\"b\":\"$workspace:/outputs/b.txt\"}");

        assertThat(expansion.error()).contains("would be 35 bytes, but may be at most 30");
    }

    @Test
    void testAnInvalidJqQueryIsRefused() {
        file("/outputs/a.json", "{}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":{\"$workspace\":\"/outputs/a.json\",\"jq\":\".[\"}}");

        assertThat(expansion.error()).startsWith("a: ").contains("is not a valid jq query");
    }

    @Test
    void testAJqQueryOfAFileThatIsNotJsonIsRefused() {
        file("/outputs/a.txt", "not json");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":{\"$workspace\":\"/outputs/a.txt\",\"jq\":\".x\"}}");

        assertThat(expansion.error()).startsWith("a: /outputs/a.txt is not JSON that jq can read");
    }

    @Test
    void testAJqQueryWithNoResultIsRefused() {
        file("/outputs/a.json", "{\"items\":[]}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":{\"$workspace\":\"/outputs/a.json\",\"jq\":\".items[]\"}}");

        assertThat(expansion.error()).isEqualTo("a: The jq query gave no result for /outputs/a.json.");
    }

    @Test
    void testAJqQueryThatFailsIsRefused() {
        file("/outputs/a.json", "{\"a\":\"text\"}");

        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":{\"$workspace\":\"/outputs/a.json\",\"jq\":\".a | tonumber\"}}");

        assertThat(expansion.error()).startsWith("a: The jq query failed");
    }

    @Test
    void testAReferenceWithOtherKeysIsRefused() {
        final Expansion expansion = workspaceArguments.expand(CHAT_ID,
                "{\"a\":{\"$workspace\":\"/outputs/a.json\",\"path\":\".x\"}}");

        assertThat(expansion.error()).isEqualTo("a: a $workspace reference may only have \"jq\" beside it, not "
                                                + "\"path\".");
        Mockito.verify(workspace, Mockito.never()).size(Mockito.anyInt(), Mockito.any());
    }

    @Test
    void testAReferenceWhosePathOrQueryIsNotAStringIsRefused() {
        assertThat(workspaceArguments.expand(CHAT_ID, "{\"a\":{\"$workspace\":3}}").error())
                .startsWith("a: $workspace must be a workspace path");
        assertThat(workspaceArguments.expand(CHAT_ID, "{\"a\":{\"$workspace\":\"/outputs/a\",\"jq\":true}}")
                .error())
                .startsWith("a: \"jq\" must be a jq query");
    }

    private void file(final String path, final String content) {
        try {
            final Path file = Files.createTempFile(tempDir, "file", ".txt");
            Files.writeString(file, content);
            final WorkspacePath workspacePath = WorkspacePath.parse(path);
            Mockito.when(workspace.size(CHAT_ID, workspacePath)).thenReturn((long) content.length());
            Mockito.when(workspace.localFile(CHAT_ID, workspacePath)).thenReturn(file);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static JsonNode json(final Expansion expansion) {
        assertThat(expansion.error()).isNull();
        try {
            return new ObjectMapper().readTree(expansion.arguments());
        } catch (final JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static AiWorkspaceConfig limits(final Long maxToolArgumentBytes) {
        return new AiWorkspaceConfig(null, null, null, null, null, null, null, null, null, null, null, null,
                maxToolArgumentBytes);
    }
}
