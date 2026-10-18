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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestReadDocumentTool {

    private static final DocRef XSLT = new DocRef("XSLT", "xslt-uuid", "Event translation");
    private static final DocRef TOP = new DocRef(DictionaryDoc.TYPE, "top-uuid", "Malicious IPs");
    private static final DocRef MIDDLE = new DocRef(DictionaryDoc.TYPE, "middle-uuid", "Known bad");
    private static final DocRef BOTTOM = new DocRef(DictionaryDoc.TYPE, "bottom-uuid", "Botnets");
    private static final DocRef SECRET = new DocRef(DictionaryDoc.TYPE, "secret-uuid", "Secret IPs");

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final WordListProvider wordListProvider = Mockito.mock(WordListProvider.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private final Map<String, DictionaryDoc> dictionaries = new HashMap<>();
    private final Set<String> viewable = new HashSet<>();
    private ReadDocumentTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        final DocumentAccess documentAccess = new DocumentAccess(() -> explorerNodeService, securityContext,
                () -> handlers, auditLog);
        tool = new ReadDocumentTool(workspace, documentAccess, () -> wordListProvider);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.VIEW)))
                .thenAnswer(invocation -> viewable.contains(((DocRef) invocation.getArgument(0)).getUuid()));
        Mockito.when(explorerNodeService.getNodeByUuid(Mockito.anyString())).thenAnswer(invocation -> {
            final String uuid = invocation.getArgument(0);
            return List.of(XSLT, TOP, MIDDLE, BOTTOM, SECRET).stream()
                    .filter(docRef -> docRef.getUuid().equals(uuid))
                    .findFirst()
                    .map(docRef -> ExplorerNode.builder().docRef(docRef).build());
        });
        Mockito.when(explorerNodeService.getPath(Mockito.any())).thenReturn(List.of());

        final DocumentActionHandler<?> dictionaryHandler = Mockito.mock(DocumentActionHandler.class);
        Mockito.when(dictionaryHandler.readDocument(Mockito.any())).thenAnswer(invocation ->
                dictionaries.get(((DocRef) invocation.getArgument(0)).getUuid()));
        handlers.put(new DocumentTypeName(DictionaryDoc.TYPE), dictionaryHandler);
    }

    @Test
    void readsTheCodeOfAnXslt() {
        viewable.add(XSLT.getUuid());
        xsltContent("<xsl:stylesheet/>");

        final String result = run("{\"uuid\":\"xslt-uuid\"}");

        assertThat(result)
                .contains("XSLT 'Event translation', /.")
                .contains("## Part 'xsl'\n<xsl:stylesheet/>");
        Mockito.verify(auditLog).documentViewed(Mockito.eq(1), Mockito.eq(XSLT), Mockito.anyString());
        Mockito.verify(workspace, Mockito.never()).writeDocument(Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    void aDocumentTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        xsltContent("<xsl:stylesheet/>");

        assertThat(run("{\"uuid\":\"xslt-uuid\"}")).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(run("{\"uuid\":\"no-such-uuid\"}")).isEqualTo(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aMissingPartSaysWhatThereIs() {
        viewable.add(XSLT.getUuid());
        xsltContent("<xsl:stylesheet/>");

        assertThat(run("{\"uuid\":\"xslt-uuid\",\"part\":\"json\"}"))
                .contains("has no part 'json'. Its parts are: xsl.");
    }

    @Test
    void aLargeDocumentIsSavedToTheWorkspaceWithAPreview() {
        viewable.add(XSLT.getUuid());
        final StringBuilder code = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            code.append("<xsl:template match=\"e").append(i).append("\"/>\n");
        }
        xsltContent(code.toString());

        final String result = run("{\"uuid\":\"xslt-uuid\"}");

        final ArgumentCaptor<WorkspacePath> path = ArgumentCaptor.forClass(WorkspacePath.class);
        Mockito.verify(workspace).writeDocument(Mockito.eq(1), path.capture(), Mockito.eq(code.toString()),
                Mockito.eq(XSLT.getUuid()));
        assertThat(path.getValue().path()).isEqualTo("/documents/xslt-uuid/xsl.xsl");
        assertThat(result)
                .contains("too large to show in full")
                .contains("/documents/xslt-uuid/xsl.xsl")
                .contains("<xsl:template match=\"e0\"/>")
                .doesNotContain("e1999");
        assertThat(result.length()).isLessThan(new AiWorkspaceConfig().getMaxCharsPerRead());
    }

    @Test
    void aDictionarysOwnWordsOnly() {
        viewable.add(TOP.getUuid());
        dictionary(TOP, "1.2.3.4\n\n5.6.7.8\n", MIDDLE);

        final String result = run("{\"uuid\":\"top-uuid\",\"part\":\"own\"}");

        assertThat(result)
                .contains("2 word(s) of its own")
                .contains("Words:\n1.2.3.4\n5.6.7.8");
        Mockito.verifyNoInteractions(wordListProvider);
    }

    @Test
    void aDictionaryWithImportsNestedTwoDeepIsCombinedWithSources() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid(), BOTTOM.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "2.2.2.2", BOTTOM);
        dictionary(BOTTOM, "3.3.3.3\n1.1.1.1");
        // As Stroom combines them: imports first, duplicates removed.
        combined(TOP, List.of("3.3.3.3", "1.1.1.1", "2.2.2.2"), List.of(BOTTOM, BOTTOM, MIDDLE));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("It has 3 word(s) in all")
                .contains("1 are its own")
                .contains("- Dictionary 'Known bad' (uuid middle-uuid): 1 word(s) of its own\n"
                          + "  - Dictionary 'Botnets' (uuid bottom-uuid): 2 word(s) of its own")
                .contains("3.3.3.3  (from Dictionary 'Botnets')")
                .contains("1.1.1.1  (from Dictionary 'Botnets')")
                .contains("2.2.2.2  (from Dictionary 'Known bad')")
                .doesNotContain("incomplete");
        // The imports whose words are given are logged as viewed, as well as the dictionary asked for.
        Mockito.verify(auditLog).documentViewed(Mockito.eq(1), Mockito.eq(TOP), Mockito.anyString());
        Mockito.verify(auditLog).documentViewed(Mockito.eq(1), Mockito.eq(MIDDLE), Mockito.contains("as an import"));
        Mockito.verify(auditLog).documentViewed(Mockito.eq(1), Mockito.eq(BOTTOM), Mockito.contains("as an import"));
    }

    @Test
    void aCircularImportIsOnlyFollowedOnce() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "2.2.2.2", TOP);
        combined(TOP, List.of("2.2.2.2", "1.1.1.1"), List.of(MIDDLE, TOP));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("- Dictionary 'Known bad' (uuid middle-uuid): 1 word(s) of its own\n"
                          + "  - Dictionary 'Malicious IPs': already included above")
                .contains("It has 2 word(s) in all");
    }

    @Test
    void duplicatesAcrossImportsKeepTheSourceStroomGivesThem() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid(), BOTTOM.getUuid()));
        dictionary(TOP, "9.9.9.9", MIDDLE, BOTTOM);
        dictionary(MIDDLE, "9.9.9.9");
        dictionary(BOTTOM, "9.9.9.9");
        combined(TOP, List.of("9.9.9.9"), List.of(MIDDLE));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("It has 1 word(s) in all")
                .contains("9.9.9.9  (from Dictionary 'Known bad')");
    }

    @Test
    void anImportTheUserCantViewIsReportedAsNotIncluded() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE, SECRET);
        dictionary(MIDDLE, "2.2.2.2");
        dictionary(SECRET, "6.6.6.6");
        // Stroom leaves it out without saying so.
        combined(TOP, List.of("2.2.2.2", "1.1.1.1"), List.of(MIDDLE, TOP));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("- Dictionary 'Secret IPs': not included: no permission")
                .contains("The list is incomplete: 1 import(s) couldn't be included")
                .doesNotContain("6.6.6.6");
        // Nothing is logged as viewed for an import that wasn't read.
        Mockito.verify(auditLog, Mockito.never()).documentViewed(Mockito.anyInt(), Mockito.eq(SECRET),
                Mockito.anyString());
    }

    @Test
    void aLargeListIsWrittenAsATableWithItsSource() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "many");
        final WordList.Builder builder = WordList.builder(true);
        for (int i = 0; i < 3_000; i++) {
            builder.addWord("10.0." + (i / 256) + "." + (i % 256), MIDDLE);
        }
        builder.addWord("1.1.1.1", TOP);
        Mockito.when(wordListProvider.getCombinedWordList(TOP)).thenReturn(builder.build());

        final String result = run("{\"uuid\":\"top-uuid\"}");

        final ArgumentCaptor<String> table = ArgumentCaptor.forClass(String.class);
        Mockito.verify(workspace).writeDocument(Mockito.eq(1),
                Mockito.eq(WorkspacePath.parse("/documents/top-uuid/words.md")), table.capture(),
                Mockito.eq(TOP.getUuid()));
        assertThat(table.getValue())
                .startsWith("| Word | Source |\n| --- | --- |\n| 10.0.0.0 | Dictionary 'Known bad' |\n")
                .contains("| 1.1.1.1 | Dictionary 'Malicious IPs' |");
        assertThat(result)
                .contains("It has 3001 word(s) in all")
                .contains("/documents/top-uuid/words.md")
                .contains("table tool");
    }

    private String run(final String arguments) {
        return tool.execute(ToolExecutionRequest.builder()
                .name(ReadDocumentTool.NAME)
                .arguments(arguments)
                .build(), 1);
    }

    private void xsltContent(final String code) {
        final DocumentActionHandler<?> handler = Mockito.mock(DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ContentIndexable.class));
        Mockito.when(((ContentIndexable) handler).getIndexableData(XSLT)).thenReturn(Map.of("xsl", code));
        handlers.put(new DocumentTypeName("XSLT"), handler);
    }

    private void dictionary(final DocRef docRef, final String words, final DocRef... imports) {
        dictionaries.put(docRef.getUuid(), new DictionaryDoc(docRef.getUuid(), docRef.getName(), null, null, null,
                null, null, null, words, List.of(imports)));
    }

    private void combined(final DocRef docRef, final List<String> words, final List<DocRef> sources) {
        final WordList.Builder builder = WordList.builder(true);
        for (int i = 0; i < words.size(); i++) {
            builder.addWord(words.get(i), sources.get(i));
        }
        Mockito.when(wordListProvider.getCombinedWordList(docRef)).thenReturn(builder.build());
    }
}
