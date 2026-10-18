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

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.explorer.shared.DocContentMatch;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.shared.ResultPage;

import event.logging.Data;
import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class TestFindInContentTool {

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final ContentIndex contentIndex = Mockito.mock(ContentIndex.class);
    private final ContentIndexable xslts = Mockito.mock(ContentIndexable.class);
    private final ContentIndexable dictionaries = Mockito.mock(ContentIndexable.class);
    private final WordListProvider wordListProvider = Mockito.mock(WordListProvider.class);
    private final Map<String, String> texts = new HashMap<>();

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        final DocRef users = xslt("users", "Users XSLT", """
                <xsl:stylesheet>
                  <xsl:value-of select="stroom:lookup('USER_TO_EMAIL', .)"/>
                </xsl:stylesheet>""", true);
        final DocRef hosts = xslt("hosts", "Hosts XSLT", "<xsl:stylesheet>HOST_TO_IP</xsl:stylesheet>", true);
        final DocRef hidden = xslt("hidden", "Secret XSLT", "USER_TO_EMAIL in a secret", false);
        Mockito.when(xslts.getType()).thenReturn(XsltDoc.TYPE);
        Mockito.when(xslts.listDocuments()).thenReturn(Set.of(users, hosts, hidden));
        Mockito.when(xslts.getIndexableData(Mockito.any())).thenAnswer(invocation ->
                Map.of("xsl", texts.get(invocation.<DocRef>getArgument(0).getUuid())));

        final DocRef admins = fixture.add(new DocRef(DictionaryDoc.TYPE, "admins", "Admins"), null, true);
        final DocRef imported = new DocRef(DictionaryDoc.TYPE, "base", "Base admins");
        Mockito.when(dictionaries.getType()).thenReturn(DictionaryDoc.TYPE);
        Mockito.when(wordListProvider.getCombinedWordList(admins)).thenReturn(WordList.builder(true)
                .addWord("alice", imported)
                .addWord("bob", admins)
                .build());
    }

    @Test
    void givenUuidsJustThoseDocumentsAreRead() {
        final String result = run("{\"pattern\":\"user_to_email\",\"uuids\":[\"users\",\"hosts\"]}");

        assertThat(result)
                .contains("1 document(s) matched 'user_to_email'. It read the documents given, and read 2 "
                          + "document(s).")
                .contains("- XSLT 'Users XSLT' (users) in /Lookups:")
                .contains("xsl line 2: <xsl:value-of select=\"stroom:lookup('USER_TO_EMAIL', .)\"/>");
        Mockito.verifyNoInteractions(contentIndex);
    }

    @Test
    void documentsInFoldersAnAgentsDocIgnoresAreLeftOutUnlessAskedFor() {
        // Users XSLT is in /Lookups/Archive, which the AGENTS doc in /Lookups ignores.
        fixture.folders.put("users", List.of("Lookups", "Archive"));
        fixture.addInstructions("agents", "## Ignore\n- folders: Archive\n", true, "Lookups");

        assertThat(run("{\"pattern\":\"user_to_email\",\"uuids\":[\"users\",\"hosts\"]}"))
                .contains("0 document(s) matched")
                .contains("Left out 1 document(s) in folders an AGENTS doc says to ignore: /Lookups/Archive "
                          + "(ignored by the AGENTS doc in /Lookups); pass includeIgnored only if the user asks "
                          + "for them.");
        Mockito.verify(xslts, Mockito.never()).getIndexableData(Mockito.argThat(docRef ->
                "users".equals(docRef.getUuid())));

        assertThat(run("{\"pattern\":\"user_to_email\",\"uuids\":[\"users\"],\"includeIgnored\":true}"))
                .contains("1 document(s) matched")
                .doesNotContain("Left out");
    }

    @Test
    void aDocumentTheUserCantViewIsNeitherReadNorRevealed() {
        final String hidden = run("{\"pattern\":\"USER\",\"uuids\":[\"hidden\"]}");
        final String missing = run("{\"pattern\":\"USER\",\"uuids\":[\"missing\"]}");

        assertThat(hidden.replace("hidden", "X")).isEqualTo(missing.replace("missing", "X"));
        assertThat(hidden).contains("0 document(s) matched").doesNotContain("Secret");
        Mockito.verify(xslts, Mockito.never()).getIndexableData(Mockito.argThat(docRef ->
                "hidden".equals(docRef.getUuid())));
    }

    @Test
    void givenTypesTheDocumentsAreReadDirectlyWhileTheIndexIsBuilt() {
        Mockito.when(contentIndex.isReady()).thenReturn(false);

        final String result = run("{\"pattern\":\"USER_TO_EMAIL\",\"types\":[\"xslt\"],\"matchCase\":true}");

        assertThat(result)
                .contains("1 document(s) matched")
                .contains("read the documents directly, as the content index isn't ready yet")
                .contains("read 2 document(s)")
                .contains("Users XSLT")
                .doesNotContain("Secret");
        // The index is built in the background, without waiting for it.
        Mockito.verify(contentIndex, Mockito.timeout(5_000)).findInContent(Mockito.any());
    }

    @Test
    void givenTypesTheIndexIsUsedWhenItIsReady() {
        Mockito.when(contentIndex.isReady()).thenReturn(true);
        Mockito.when(contentIndex.findInContent(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                match(new DocRef(XsltDoc.TYPE, "users", "Users XSLT")),
                match(new DocRef(XsltDoc.TYPE, "hidden", "Secret XSLT")),
                match(new DocRef(DictionaryDoc.TYPE, "admins", "Admins")))));

        final String result = run("{\"pattern\":\"USER_TO_EMAIL\",\"types\":[\"XSLT\"]}");

        assertThat(result)
                .contains("1 document(s) matched")
                .contains("searched the content index")
                .contains("read 1 document(s)")
                .doesNotContain("Secret")
                .doesNotContain("Admins");
        Mockito.verify(xslts, Mockito.never()).listDocuments();
    }

    @Test
    void aDictionaryIsSearchedWithItsImports() {
        final String result = run("{\"pattern\":\"alice\",\"uuids\":[\"admins\"]}");

        assertThat(result)
                .contains("1 document(s) matched")
                .contains("words line 1: alice (from Dictionary 'Base admins')");
    }

    @Test
    void theResultsStopAtTheLimit() {
        Mockito.when(contentIndex.isReady()).thenReturn(false);

        final String result = run("{\"pattern\":\"xsl\",\"types\":[\"XSLT\"],\"limit\":1}");

        assertThat(result)
                .contains("1 document(s) matched")
                .contains("stopped at the limit of 1 matching documents");
    }

    @Test
    void theSearchIsLoggedWithThePatternAndHowManyDocumentsWereRead() {
        run("{\"pattern\":\"HOST\",\"uuids\":[\"users\",\"hosts\"]}");

        final List<SearchEventAction> events = fixture.logged(SearchEventAction.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getQuery().getRaw()).isEqualTo("'HOST' in 2 document(s) given");
        assertThat(events.getFirst().getData())
                .extracting(Data::getName, Data::getValue)
                .contains(tuple("DocumentsRead", "2"));
    }

    @Test
    void badArgumentsAreRefused() {
        assertThatThrownBy(() -> run("{\"pattern\":\"x\"}"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("Give uuids or types");
        assertThatThrownBy(() -> run("{\"pattern\":\"x\",\"types\":[\"Nonsense\"]}"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("The types are: Dictionary, XSLT");
        assertThatThrownBy(() -> run("{\"pattern\":\"(\",\"regex\":true,\"uuids\":[\"users\"]}"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("not a valid regular expression");
    }

    private String run(final String arguments) {
        final FindInContentTool tool = new FindInContentTool(fixture.workspace, () -> contentIndex,
                () -> Set.of(xslts, dictionaries), () -> wordListProvider, fixture.documentAccess(),
                fixture.agentInstructions(), fixture.auditLog());
        return tool.run(ToolTestFixture.request(FindInContentTool.NAME, arguments), 1);
    }

    private DocRef xslt(final String uuid, final String name, final String text, final boolean canView) {
        texts.put(uuid, text);
        return fixture.add(new DocRef(XsltDoc.TYPE, uuid, name), null, canView);
    }

    private static DocContentMatch match(final DocRef docRef) {
        return DocContentMatch.builder().docRef(docRef).build();
    }
}
