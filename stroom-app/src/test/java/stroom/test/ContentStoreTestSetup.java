/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.test;

import stroom.contentstore.impl.ContentStoreResourceImpl;
import stroom.contentstore.shared.ContentStoreContentPack;
import stroom.contentstore.shared.ContentStoreContentPackWithDynamicState;
import stroom.contentstore.shared.ContentStoreCreateGitRepoRequest;
import stroom.contentstore.shared.ContentStoreResponse;
import stroom.contentstore.shared.ContentStoreResponse.Status;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Pulls Content Store items into Stroom for integration tests
 * and development.
 * Called from SetupSampleData.
 */
public class ContentStoreTestSetup {

    /**
     * Provider of the Thing to do the actual import
     */
    private final Provider<ContentStoreResourceImpl> contentStoreResourceProvider;

    /**
     * Thing to do the actual work
     */
    private ContentStoreResourceImpl contentStoreResource;

    /**
     * Stuff in the Content Store
     */
    private List<ContentStoreContentPackWithDynamicState> contentStoreContents;

    /**
     * Every ID in the content store - SetupSampleData installs all of them
     */
    private final List<String> samplePackIds = new ArrayList<>();

    /**
     * Standard packs, used by integration tests.
     */
    public static final List<String> STANDARD_PACK_IDS = Arrays.asList(
            "core-xml-schemas",
            "event-logging-xml-schema",
            "standard-pipelines",
            "template-pipelines"
    );

    /**
     * Constructor - injected.
     *
     * @param contentStoreResourceProvider Injected service for managing content stores and packs.
     */
    @Inject
    public ContentStoreTestSetup(
            final Provider<ContentStoreResourceImpl> contentStoreResourceProvider) {

        this.contentStoreResourceProvider = contentStoreResourceProvider;
    }

    private Path cacheGitRepo(final String gitRepoUrl) throws IOException {
        try {
            // Derive the local repo path from the URL
            final URI uri = new URI(gitRepoUrl);
            String localName = uri.getPath();
            final int iDot = localName.lastIndexOf('.');
            if (iDot != -1) {
                localName = localName.substring(0, iDot);
            }

            final Path repoPath = Path.of("../content-cache", localName).toAbsolutePath();

            // Ensure directories exist
            Files.createDirectories(repoPath);
            final Path realRepoPath = repoPath.toRealPath();

            // Do we already have a git repo?
            final String[] repoContents = repoPath.toFile().list();
            if (repoContents != null && repoContents.length > 0) {
                // Update repo
                try (final Git git = Git.open(repoPath.toFile())) {
                    git.fetch().call();
                }
            } else {
                // Clone repo to filesystem. Note bare as new repo is used as a remote repo.
                final CloneCommand cloneCommand = Git.cloneRepository()
                        .setURI(gitRepoUrl)
                        .setDirectory(repoPath.toFile())
                        .setCloneAllBranches(true)
                        .setBare(false);

                //noinspection EmptyTryBlock
                try (@SuppressWarnings("unused") final Git git = cloneCommand.call()) {
                    // No code
                }
            }

            return realRepoPath;

        } catch (final URISyntaxException e) {
            throw new IOException("Invalid remote GitRepo URI: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new IOException("IO error caching remote GIT repo: " + e.getMessage(), e);
        } catch (final GitAPIException e) {
            throw new IOException("GIT error caching remote GIT repo: " + e.getMessage(), e);
        }

    }

    /**
     * Load the content store, if necessary.
     * Cannot be called from injected constructor.
     */
    private synchronized void cacheContentStore() throws RuntimeException {
        if (contentStoreContents == null) {
            try {
                // Find local repository - create if necessary
                final String contentRepo = "https://github.com/gchq/stroom-content.git";
                final String visRepo = "https://github.com/gchq/stroom-visualisations-dev.git";
                final Path localContentRepo = cacheGitRepo(contentRepo);
                final Path localVisRepo = cacheGitRepo(visRepo);

                final String contentStoreFileUrl = "file://" + localContentRepo.resolve("source/content-store.yml");

                // Hack to force the content store config to use our content store config file
                contentStoreResource = contentStoreResourceProvider.get();
                contentStoreResource.addTestUriContentStoreUrl(contentStoreFileUrl);

                // Tell the content pack to get from our local GIT repo instead of the https version
                contentStoreResource.remapGitUrl("https://github.com/gchq/stroom-content.git",
                        "file://" + localContentRepo);
                contentStoreResource.remapGitUrl("https://github.com/gchq/stroom-visualisations-dev.git",
                        "file://" + localVisRepo);

                // Get all the items in one go
                final PageRequest pageRequest = new PageRequest(0, Integer.MAX_VALUE);
                final ResultPage<ContentStoreContentPackWithDynamicState> page =
                        contentStoreResource.list(pageRequest);

                contentStoreContents = page.stream().toList();

                // List out all the IDs so we can install them if necessary
                for (final ContentStoreContentPackWithDynamicState cpds : contentStoreContents) {
                    samplePackIds.add(cpds.getContentPack().getId());
                }
            } catch (final IOException e) {
                throw new RuntimeException("Error caching content store: " + e.getMessage(), e);
            }
        }

    }

    /**
     * Installs the content pack with the given ID.
     *
     * @param contentPackId The ID of the content pack.
     * @throws RuntimeException If something goes wrong.
     */
    public void install(final String contentPackId) throws RuntimeException {
        this.cacheContentStore();
        // Find the content pack
        ContentStoreContentPack contentPack = null;
        for (final ContentStoreContentPackWithDynamicState contentPackWithDynamicState : this.contentStoreContents) {
            if (contentPackWithDynamicState.getContentPack().getId().equals(contentPackId)) {
                contentPack = contentPackWithDynamicState.getContentPack();
                break;
            }
        }

        if (contentPack == null) {
            throw new RuntimeException("Cannot find content pack with ID '" + contentPackId + "'");
        }

        if (contentPack.getGitNeedsAuth()) {
            throw new RuntimeException("Cannot import content pack with ID '"
                                       + contentPackId
                                       + "': authentication is required");
        }

        // Install the pack
        final ContentStoreCreateGitRepoRequest request =
                new ContentStoreCreateGitRepoRequest(contentPack, null);
        final ContentStoreResponse response = contentStoreResource.create(request);
        final ContentStoreResponse.Status status = response.getStatus();
        if (!(status.equals(Status.OK) || status.equals(Status.ALREADY_EXISTS))) {
            throw new RuntimeException("Couldn't create content pack with ID '"
                                       + contentPackId + "': " + response.getMessage());
        }

    }

    /**
     * Installs a number of content packs.
     *
     * @param contentPackIds Collection of content pack IDs.
     * @throws RuntimeException If something goes wrong.
     */
    public void install(final Collection<String> contentPackIds) throws RuntimeException {
        for (final String id : contentPackIds) {
            install(id);
        }
    }

    /**
     * Installs all the content packs for SetupSampleData.
     *
     * @throws RuntimeException if something goes wrong.
     */
    public void installSampleDataPacks() throws RuntimeException {
        this.cacheContentStore();
        install(samplePackIds);
    }

    /**
     * Installs the standard packs, as used in integration tests.
     */
    public void installStandardPacks() throws RuntimeException {
        install(STANDARD_PACK_IDS);
    }

    /**
     * Installs visualisations.
     */
    public void installVisualisations() throws RuntimeException {
        install("stroom-visualisations");
    }
}
