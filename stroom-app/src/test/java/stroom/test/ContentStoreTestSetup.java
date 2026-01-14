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
import stroom.util.io.HomeDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PullResult;
import org.eclipse.jgit.api.errors.GitAPIException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
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

    private final HomeDirProvider homeDirProvider;

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
     * Time of last pull from GIT
     */
    private static Instant timeOfLastGitPull;

    /**
     * Time between GIT pulls. We don't want to pull every time - just the first one in the test.
     * Otherwise, the tests take a lot longer. Set to 30 minutes.
     */
    private static final Duration TIME_BETWEEN_GIT_PULLS = Duration.ofMinutes(30);

    /**
     * Logger
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentStoreTestSetup.class);

    /**
     * Constructor - injected.
     *
     * @param contentStoreResourceProvider Injected service for managing content stores and packs.
     */
    @Inject
    public ContentStoreTestSetup(final Provider<ContentStoreResourceImpl> contentStoreResourceProvider,
                                 final HomeDirProvider homeDirProvider) {
        this.contentStoreResourceProvider = contentStoreResourceProvider;
        this.homeDirProvider = homeDirProvider;
        LOGGER.info("Time of last GIT pull: {}", getTime(timeOfLastGitPull));
    }

    private String getTime(final Instant instant) {
        return instant == null
                ? "never"
                : instant.toString();
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
            final String[] parts = localName.split("/");

            // Determine where we are going to store repository data.
            Path repoPath = homeDirProvider.get().resolve("content-cache");
            for (final String part : parts) {
                if (NullSafe.isNonBlankString(part)) {
                    repoPath = repoPath.resolve(part);
                }
            }
            repoPath = repoPath.toAbsolutePath();

            // Ensure directories exist
            Files.createDirectories(repoPath);
            repoPath = repoPath.toRealPath();

            // Create a lock file.
            final Path lockPath = repoPath.getParent().resolve(repoPath.getFileName().toString() + ".lock");

            // Perform read under lock.
            try (final FileChannel channel = FileChannel.open(lockPath,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
                    final FileLock lock = channel.lock()) {

                // Do we already have a git repo?
                final String[] repoContents = repoPath.toFile().list();
                if (repoContents != null && repoContents.length > 0) {
                    final Instant timeNow = Instant.now();
                    final Instant nextPull;
                    if (timeOfLastGitPull == null) {
                        nextPull = timeNow;
                    } else {
                        nextPull = timeOfLastGitPull.plus(TIME_BETWEEN_GIT_PULLS);
                    }

                    LOGGER.info(() -> LogUtil.message(
                            "Time now: {}; Time of last GIT pull: {}; Time of next GIT pull: {}",
                            getTime(timeNow),
                            getTime(timeOfLastGitPull),
                            getTime(nextPull)));

                    if (timeNow.isAfter(nextPull) || timeNow.equals(nextPull)) {
                        // Update repo
                        try (final Git git = Git.open(repoPath.toFile())) {
                            final PullResult pullResult = git.pull().call();
                            if (!pullResult.isSuccessful()) {
                                throw new IOException("Unsuccessful pull request: " + pullResult);
                            } else {
                                LOGGER.info("Successful pull from GIT repo into local content cache repo");
                                timeOfLastGitPull = timeNow;
                            }
                        }
                    } else {
                        LOGGER.info("Not pulling from GIT repo to update local content cache repo");
                    }
                } else {
                    // Clone repo to filesystem. Not a bare repo as we want access to the content-store.yml file.
                    final CloneCommand cloneCommand = Git.cloneRepository()
                            .setURI(gitRepoUrl)
                            .setDirectory(repoPath.toFile())
                            .setCloneAllBranches(true)
                            .setBare(false);

                    try (@SuppressWarnings("unused") final Git git = cloneCommand.call()) {
                        LOGGER.info("Cloned GIT repo '{}' to local repo '{}'", gitRepoUrl, repoPath);
                        timeOfLastGitPull = Instant.now();
                    }
                }

                return repoPath;

            } catch (final GitAPIException e) {
                throw new IOException("GIT error caching remote GIT repo: " + e.getMessage(), e);
            } finally {
                Files.deleteIfExists(lockPath);
            }

        } catch (final URISyntaxException e) {
            throw new IOException("Invalid remote GitRepo URI: " + e.getMessage(), e);
        } catch (final IOException e) {
            throw new IOException("IO error caching remote GIT repo: " + e.getMessage(), e);
        }
    }

    /**
     * Load the content store, if necessary.
     * Cannot be called from injected constructor.
     */
    private synchronized void cacheContentStore() throws RuntimeException {
        if (contentStoreContents == null) {
            try {
                // Repositories that hold the content
                final String contentRepo = "https://github.com/gchq/stroom-content.git";
                final String visRepo = "https://github.com/gchq/stroom-visualisations-dev.git";

                // Create local cache of the repos and get the paths to them
                final Path localContentRepo = cacheGitRepo(contentRepo);
                final Path localVisRepo = cacheGitRepo(visRepo);

                // Generate the file: URL of the content-store YAML file
                final String contentStoreFileUrl = "file://" +
                                                   localContentRepo.resolve("source/content-store.yml");

                // Hack to force the content store config to use our content store config file
                contentStoreResource = contentStoreResourceProvider.get();
                contentStoreResource.addTestUriContentStoreUrl(contentStoreFileUrl);

                // Tell the content store to get from our local GIT repo instead of the https version
                contentStoreResource.remapGitUrl("https://github.com/gchq/stroom-content.git",
                        "file://" + localContentRepo);
                contentStoreResource.remapGitUrl("https://github.com/gchq/stroom-visualisations-dev.git",
                        "file://" + localVisRepo);

                // Get all the items from the content store in one go
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
