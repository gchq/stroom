/*
 * Copyright 2025 Stroomworks Limited
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
 *
 */

package stroom.importexport;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.gitrepo.api.GitRepoStore;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.importexport.api.ImportExportSerializer;
import stroom.pipeline.PipelineStore;
import stroom.pipeline.shared.PipelineDoc;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.io.FileUtil;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class to test the additions and changes for the GitRepo import/export.
 */
class TestImportExportGitRepo extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestImportExportGitRepo.class);

    @SuppressWarnings("unused")
    @Inject
    private CommonTestControl commonTestControl;
    @SuppressWarnings("unused")
    @Inject
    private GitRepoStore gitRepoStore;
    @SuppressWarnings("unused")
    @Inject
    private ImportExportSerializer importExportSerializer;
    @SuppressWarnings("unused")
    @Inject
    private PipelineStore pipelineStore;
    @SuppressWarnings("unused")
    @Inject
    private ExplorerNodeService explorerNodeService;
    @SuppressWarnings("unused")
    @Inject
    private ExplorerService explorerService;

    /**
     * Class to visit the files in the git repository to check what is there and what is missing.
     * Uses regex to match filenames as they contain UUIDs.
     */
    private static class GitFileVisitor extends SimpleFileVisitor<Path> {

        private final Path root;
        private final Map<Pattern, Boolean> pathsFound;
        private final List<String> pathsNotMatched;

        /**
         * Constructor.
         *
         * @param root            Root of the thing we're searching. Used to make paths relative.
         * @param pathsFound      Map of patterns and whether they've been found
         * @param pathsNotMatched List of paths we've found that weren't matched.
         */
        public GitFileVisitor(Path root,
                              Map<Pattern, Boolean> pathsFound,
                              List<String> pathsNotMatched) {
            this.root = root;
            this.pathsFound = pathsFound;
            this.pathsNotMatched = pathsNotMatched;
        }

        private void checkPath(final Path path) {
            String pathName = root.relativize(path).toString();
            boolean foundIt = false;
            for (Map.Entry<Pattern, Boolean> entry : pathsFound.entrySet()) {
                if (entry.getKey().matcher(pathName).matches()) {
                    entry.setValue(Boolean.TRUE);
                    foundIt = true;
                    break;
                }
            }
            if (!foundIt) {
                pathsNotMatched.add(pathName);
            }
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {
            checkPath(dir);
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
            checkPath(file);
            return FileVisitResult.CONTINUE;
        }
    }

    private void testGitFilesOnDisk(final String testName,
                                    final Path testDataDir,
                                    final List<String> pathPatterns)
            throws IOException {

        final Map<Pattern, Boolean> pathsFound = new HashMap<>();
        for (var pathPattern : pathPatterns) {
            pathsFound.put(Pattern.compile(pathPattern), Boolean.FALSE);
        }

        final List<String> pathsNotMatched = new ArrayList<>();

        final GitFileVisitor visitor = new GitFileVisitor(testDataDir, pathsFound, pathsNotMatched);

        Files.walkFileTree(testDataDir, visitor);

        assertThat(pathsFound)
                .as("%s: Should be true to show pattern matched", testName)
                .doesNotContainValue(Boolean.FALSE);

        assertThat(pathsNotMatched)
                .as("%s: Paths not matched should be empty", testName)
                .isEmpty();
    }

    /**
     * Basic test - a few elements exported.
     * @throws IOException If something goes really wrong
     */
    @Test
    void testExport1() throws IOException {

        final ExplorerNode systemNode = explorerNodeService.getRoot();
        final DocRef systemDocRef = systemNode != null
                ? systemNode.getDocRef()
                : null;
        assertThat(explorerNodeService.getDescendants(systemDocRef).size()).isEqualTo(1);

        // GitRepo
        final ExplorerNode gitRepoNode = explorerService.create(GitRepoDoc.TYPE,
                "GitRepo",
                systemNode,
                null);
        GitRepoDoc gitRepoDoc = gitRepoStore.readDocument(gitRepoNode.getDocRef());
        gitRepoDoc.setDescription("Original Description");
        gitRepoStore.writeDocument(gitRepoDoc);

        final ExplorerNode folder1 = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                "folder1",
                gitRepoNode,
                null);
        final ExplorerNode folder2 = explorerService.create(ExplorerConstants.FOLDER_TYPE,
                "folder2",
                gitRepoNode,
                null);

        assertThat(explorerNodeService.getDescendants(systemDocRef).size()).isEqualTo(4);

        final ExplorerNode pipelineNode = explorerService.create(PipelineDoc.TYPE,
                "Pipeline",
                folder2,
                null);
        final PipelineDoc pipelineDoc = pipelineStore.readDocument(pipelineNode.getDocRef());
        pipelineDoc.setDescription("Pipeline Description");
        pipelineStore.writeDocument(pipelineDoc);

        // Something under the GitRepo to export
        final ExplorerNode feedNode = explorerService.create(FeedDoc.TYPE,
                "FEED",
                folder1,
                null);

        commonTestControl.createRequiredXMLSchemas();

        final Path testDataDir = getCurrentTestDir().resolve("ExportGitRepoTest");
        LOGGER.info("Writing to test dir: {}", testDataDir);

        FileUtil.deleteDir(testDataDir);
        Files.createDirectories(testDataDir);

        // List of paths to remove from the exported paths
        // Basically the path to the GitRepoDoc node
        List<ExplorerNode> rootNodePath = List.of(ExplorerConstants.SYSTEM_NODE, gitRepoNode);

        // List of nodes to export.
        Set<DocRef> docRefsToExport = Set.of(
                gitRepoDoc.asDocRef(),
                feedNode.getDocRef(),
                pipelineNode.getDocRef());

        // List of doctypes to ignore - GitRepos so we ignore recursive repos
        Set<String> docTypesToIgnore = Set.of(GitRepoDoc.TYPE);

        // Run the export to disk
        importExportSerializer.write(
                rootNodePath,
                testDataDir,
                docRefsToExport,
                docTypesToIgnore,
                true);

        List<String> pathPatterns = List.of(
                "",
                "folder1",
                "folder1/FEED\\.Feed\\.[-a-f0-9]*.meta",
                "folder1/FEED\\.Feed\\.[-a-f0-9]*.node",
                "folder2",
                "folder2/Pipeline\\.Pipeline\\.[-a-f0-9]*.meta",
                "folder2/Pipeline\\.Pipeline\\.[-a-f0-9]*.node",
                "folder2/Pipeline\\.Pipeline\\.[-a-f0-9]*.xml");

        this.testGitFilesOnDisk("testExport1",
                testDataDir,
                pathPatterns);

    }

}
