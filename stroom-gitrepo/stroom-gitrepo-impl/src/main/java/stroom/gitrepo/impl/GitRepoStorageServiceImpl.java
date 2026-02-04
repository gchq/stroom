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

package stroom.gitrepo.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.api.StoredSecrets;
import stroom.credentials.shared.AccessTokenSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.SshKeySecret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.gitrepo.api.GitRepoConfig;
import stroom.gitrepo.api.GitRepoStorageService;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportSerializer;
import stroom.importexport.shared.ImportSettings;
import stroom.importexport.shared.ImportSettings.ImportMode;
import stroom.importexport.shared.ImportState;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Message;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.PushCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.TransportCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.jspecify.annotations.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Class to call the ImportExport mechanism to handle the import and export to
 * local Git repositories, then to sync the local repo with a remote repo.
 */
@Singleton
public class GitRepoStorageServiceImpl implements GitRepoStorageService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoStorageServiceImpl.class);

    /**
     * The tree model to find parents of the event item.
     */
    private final ExplorerService explorerService;

    /**
     * Finds children of an ExplorerNode
     */
    private final ExplorerNodeService explorerNodeService;

    /**
     * Provides the ability to import and export stuff to disk.
     */
    private final ImportExportSerializer importExportSerializer;

    /**
     * Where we get configuration from.
     */
    private final Provider<GitRepoConfig> config;

    /**
     * Object to create paths for local storage.
     */
    private final PathCreator pathCreator;

    /**
     * Where to write changes of the GitRepoDoc
     */
    private final GitRepoDao gitRepoDao;

    /**
     * Provides credential information
     */
    private final StoredSecrets storedSecrets;

    /**
     * System temporary directory for SSH home and SSH directories. Not used.
     */
    private static final File tempDir;

    /**
     * Name of the Git repository directory. We need to ensure we don't delete
     * this!
     */
    private static final String GIT_REPO_DIRNAME = ".git";

    /**
     * Name of any README file. We don't delete this.
     */
    private static final String GIT_README_MD = "README.md";

    /**
     * The username to use in the commit to Git
     */
    private static final String GIT_USERNAME = "Stroom";

    /**
     * The email address to use in the commit to Git - empty string
     */
    private static final String GIT_EMAIL = "";

    /**
     * Empty string
     */
    private static final String EMPTY = "";

    /**
     * Name of the system property holding the temporary directory.
     */
    private static final String TEMPDIR_PROPERTY_NAME = "java.io.tmpdir";

    /**
     * Default value of temporary directory (shouldn't be needed but just in case)
     */
    private static final String DEFAULT_TEMPDIR = "/tmp";

    /*
     * Initialise the temp directory
     */
    static {
        tempDir = Paths.get(System.getProperty(TEMPDIR_PROPERTY_NAME, DEFAULT_TEMPDIR)).toFile();
    }

    /**
     * Constructor so we can log when this object is constructed.
     * Called by injection system.
     */
    @SuppressWarnings("unused")
    @Inject
    public GitRepoStorageServiceImpl(final ExplorerService explorerService,
                                     final ExplorerNodeService explorerNodeService,
                                     final ImportExportSerializer importExportSerializer,
                                     final Provider<GitRepoConfig> config,
                                     final PathCreator pathCreator,
                                     final GitRepoDao gitRepoDao,
                                     final StoredSecrets storedSecrets) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportSerializer = importExportSerializer;
        this.config = config;
        this.pathCreator = pathCreator;
        this.gitRepoDao = gitRepoDao;
        this.storedSecrets = storedSecrets;
    }

    /**
     * Called by pressing the Git Settings 'Push to Git' button.
     * Synchronised to avoid multiple threads writing to the same
     * directory structure.
     *
     * @param gitRepoDoc    The document that we're pushing the button on.
     *                      Must not be null.
     * @param commitMessage The Git commit message. Must not be null.
     * @param calledFromUi  True if the method is being called from the UI over
     *                      REST, false if being called from a Job.
     *                      Affects how some errors are handled.
     * @return The export summary. Might return if the export hasn't yet taken
     * place.
     * @throws IOException if something goes wrong
     */
    @Override
    public synchronized List<Message> exportDoc(final GitRepoDoc gitRepoDoc,
                                                final String commitMessage,
                                                final boolean calledFromUi)
            throws IOException {
        LOGGER.debug("Exporting document '{}' to Git; UUID is '{}'", gitRepoDoc.getUrl(), gitRepoDoc.getUuid());
        final List<Message> messages = new ArrayList<>();

        final DocRef gitRepoDocRef = GitRepoDoc.getDocRef(gitRepoDoc.getUuid());
        final Optional<ExplorerNode> optGitRepoExplorerNode = explorerService.getFromDocRef(gitRepoDocRef);
        final ExplorerNode gitRepoExplorerNode = optGitRepoExplorerNode.orElseThrow(IOException::new);

        // Work out where the GitRepo node is in the explorer tree
        final List<ExplorerNode> gitRepoNodePath = this.explorerNodeService.getPath(gitRepoDocRef);
        gitRepoNodePath.add(gitRepoExplorerNode);

        // Only try to do anything if the settings exist
        if (!gitRepoDoc.getUrl().isEmpty()) {
            // Find the path to the root of the local Git repository
            final Path localDir = pathCreator.toAppPath(config.get().getLocalDir());
            try (final AutoDeletingTempDirectory gitWorkDir
                    = new AutoDeletingTempDirectory(localDir, "gitrepo-")) {

                // Create Git object for the gitWork directory
                try (final Git git = this.gitConstructForPush(gitRepoDoc, gitWorkDir.getDirectory())) {

                    // The export directory is somewhere within the gitWorkDir,
                    // defined by the path the user specified
                    final Path exportDir = addDirectoryToPath(gitWorkDir.getDirectory(),
                            Paths.get(gitRepoDoc.getPath()));
                    this.ensureDirectoryExists(exportDir);

                    // Delete all the files that are currently in the repo
                    // so we can overwrite any changes and detect deletions.
                    // We keep the README.md and the .git/ directory.
                    this.deleteFileTree(exportDir, true);

                    // Export everything from Stroom to local git repo dir
                    final ExportSummary exportSummary = this.export(gitRepoNodePath,
                            gitRepoExplorerNode,
                            exportDir);
                    messages.addAll(exportSummary.getMessages());
                    addMessage(messages, Severity.INFO, "Export to disk successful");

                    // Has anything changed against the remote?
                    final Status gitStatus = git.status().call();
                    if (!gitStatus.isClean()) {

                        this.gitStatusToMessages(gitStatus, messages);

                        // Add everything to commit & commit locally
                        // Match already tracked files - detects deletions
                        git.add().setUpdate(true).addFilepattern(".").call();
                        // Match new files
                        git.add().setUpdate(false).addFilepattern(".").call();

                        final RevCommit gitCommit = git.commit()
                                .setCommitter(GIT_USERNAME, GIT_EMAIL)
                                .setMessage(commitMessage)
                                .call();
                        addMessage(messages, Severity.INFO, "Local commit successful");

                        // Push to remote
                        final PushCommand pushCommand = git.push();
                        setGitCreds(gitRepoDoc, pushCommand);
                        pushCommand.call();
                        addMessage(messages, Severity.INFO, "Pushed to Git");

                        // Store the commit version
                        gitRepoDao.storeHash(gitRepoDoc.getUuid(), gitCommit.getName());
                    } else {
                        // Jobs don't need to know that this didn't do anything
                        if (calledFromUi) {
                            throw new IOException("No local changes; therefore not pushing to Git");
                        } else {
                            LOGGER.info("{}: No local changes; not pushing to Git", gitRepoDoc.getName());
                        }
                    }
                } catch (final GitAPIException e) {
                    this.throwException("Couldn't commit and push Git", e, messages);
                } catch (final IOException e) {
                    this.throwException("Error pushing to Git", e, messages);
                }
            }
        } else {
            if (calledFromUi) {
                throw new IOException("Git repository URL isn't configured; cannot push");
            } else {
                LOGGER.warn("{}: Git URL isn't configured; not pushing", gitRepoDoc.getName());
            }
        }

        return messages;
    }

    /**
     * Adds a message to the list of messages, logging the message at the appropriate level.
     *
     * @param messages The list of messages
     * @param severity How severe the issue is
     * @param message  The message to add / log.
     */
    private void addMessage(final List<Message> messages,
                            final Severity severity,
                            final String message) {
        Objects.requireNonNull(messages);
        Objects.requireNonNull(severity);

        switch (severity) {
            case Severity.INFO:
                LOGGER.info(message);
                break;
            case Severity.WARNING:
                LOGGER.warn(message);
                break;
            case Severity.ERROR:
            case Severity.FATAL_ERROR:
                LOGGER.error(message);
        }

        messages.add(new Message(severity, message));
    }

    /**
     * Puts the git status into the messages to send back to the user.
     *
     * @param gitStatus The status of the repo after export.
     * @param messages  The list of messages to return to the user.
     */
    private void gitStatusToMessages(final Status gitStatus,
                                     final List<Message> messages) {
        for (final String filename : gitStatus.getUncommittedChanges()) {
            addMessage(messages, Severity.INFO, "Changed: " + filename);
        }
        for (final String dirname : gitStatus.getUntrackedFolders()) {
            addMessage(messages, Severity.INFO, "New folder: " + dirname);
        }
        for (final String filename : gitStatus.getUntracked()) {
            addMessage(messages, Severity.INFO, "New file: " + filename);
        }
        for (final String filename : gitStatus.getMissing()) {
            addMessage(messages, Severity.INFO, "Deleted: " + filename);
        }
        for (final String filename : gitStatus.getModified()) {
            addMessage(messages, Severity.INFO, "Modified: " + filename);
        }
    }

    /**
     * Called when the user presses the Pull from Git button in the UI.
     * Synchronised to avoid multiple threads writing to the same
     * directory structure.
     *
     * @param gitRepoDoc The document holding the Git repo settings
     * @return A list of messages about the import
     * @throws IOException if something goes wrong
     */
    @Override
    public synchronized List<Message> importDoc(
            final GitRepoDoc gitRepoDoc,
            final boolean isMockEnvironment)
            throws IOException {
        final List<Message> messages = new ArrayList<>();

        final DocRef gitRepoDocRef = GitRepoDoc.getDocRef(gitRepoDoc.getUuid());

        if (!isMockEnvironment) {
            final Optional<ExplorerNode> optGitRepoExplorerNode = explorerService.getFromDocRef(gitRepoDocRef);
            if (optGitRepoExplorerNode.isPresent()) {
                final ExplorerNode gitRepoExplorerNode = optGitRepoExplorerNode.get();

                // Work out where the GitRepo node is in the explorer tree
                final List<ExplorerNode> gitRepoNodePath = this.explorerNodeService.getPath(gitRepoDocRef);
                gitRepoNodePath.add(gitRepoExplorerNode);
            }
        }

        // Only try to do anything if the settings exist
        if (!gitRepoDoc.getUrl().isEmpty()) {
            // Find the path to the root of the local Git repository
            final Path localDir = pathCreator.toAppPath(config.get().getLocalDir());
            try (final AutoDeletingTempDirectory gitWorkDir = new AutoDeletingTempDirectory(localDir, "gitrepo-")) {

                // Grab everything from server - it won't be too big
                this.gitCloneForPull(gitRepoDoc, gitWorkDir.getDirectory());
                addMessage(messages, Severity.INFO, "Cloned from Git repository");

                // ImportSettings.auto() is used in a few places. This consists of
                // .importMode(ImportMode.IGNORE_CONFIRMATION)
                // .enableFilters(true)
                // We want to make sure that the import goes to the root of the
                // gitRepoDocRef rather than where it might have been in the
                // original export.

                final List<ImportState> importStates = new ArrayList<>();
                final ImportSettings importSettings = ImportSettings.builder()
                        .importMode(ImportMode.IGNORE_CONFIRMATION)
                        .enableFilters(false)
                        .useImportFolders(true)
                        .useImportNames(true)
                        .rootDocRef(gitRepoDocRef)
                        .isMockEnvironment(isMockEnvironment)
                        .build();

                // Set (remote) directory
                final Path pathToImport;
                if (gitRepoDoc.getPath() != null && !gitRepoDoc.getPath().isEmpty()) {
                    pathToImport = addDirectoryToPath(gitWorkDir.getDirectory(), Paths.get(gitRepoDoc.getPath()));
                    // Sanity check to avoid mystery import failures
                    if (!pathToImport.toFile().exists()) {
                        throw new IOException("The path to import '"
                                              + gitRepoDoc.getPath()
                                              + "' doesn't exist within the Git repository.");
                    }
                } else {
                    pathToImport = gitWorkDir.getDirectory();
                }

                final Set<DocRef> docRefs = importExportSerializer.read(
                        pathToImport,
                        importStates,
                        importSettings);

                for (final DocRef docRef : docRefs) {
                    // ImportExportSerializerImpl adds the System docref to the returned set,
                    // but we don't use that here, so ignore it
                    if (!docRef.equals(ExplorerConstants.SYSTEM_DOC_REF)) {
                        addMessage(messages, Severity.INFO, "Imported '" + docRef.getName() + "'");
                    }
                }
                addMessage(messages, Severity.INFO, "Completed Git Pull");
            }
        } else {
            throw new IOException("Git repository URL isn't configured; cannot pull");
        }

        return messages;
    }

    /**
     * Creates an exception with as much context info as possible for display
     * to the user in the UI.
     *
     * @param errorMessage The message that describes the problem. Must not
     *                     be null.
     * @param cause        The exception that caused this error. Can be null if no
     *                     triggering exception.
     * @param messages     Any messages from the export. Never null. Can be empty.
     * @throws IOException to indicate to the caller that an error has occurred.
     */
    private void throwException(final String errorMessage,
                                final Exception cause,
                                final List<Message> messages)
            throws IOException {

        LOGGER.error("{}, {}, {}", errorMessage, cause, messages);
        final StringBuilder buf = new StringBuilder(errorMessage);

        if (cause != null) {
            buf.append("\n    ");
            buf.append(walkExceptions(cause));
        }
        if (!messages.isEmpty()) {
            buf.append("\n\nAdditional information:");
            for (final Message m : messages) {
                buf.append("\n    ");
                buf.append(m);
            }
        }
        throw new IOException(buf.toString(), cause);
    }

    /**
     * Ensures that the given path exists. The path is assumed to be all
     * directories - there is no terminal filename at the end.
     *
     * @param path The path of directories that should exist.
     * @throws IOException If the directories could not be created.
     */
    private void ensureDirectoryExists(final Path path)
            throws IOException {

        if (!path.toFile().exists()) {
            if (!path.toFile().mkdirs()) {
                throw new IOException("Could not create directories '"
                                      + path
                                      + "' for Git document");
            }
        }
    }

    /**
     * Deletes a file tree recursively. Does not delete any .git
     * directories nor any README.md.
     *
     * @param root         Delete everything under this directory. Must not be null.
     * @param keepGitStuff If true then keep Git key files - .git/, README.md.
     *                     If false then delete everything.
     * @throws IOException if something goes wrong.
     */
    private void deleteFileTree(final Path root, final boolean keepGitStuff)
            throws IOException {

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public @NonNull FileVisitResult preVisitDirectory(@NonNull final Path dir,
                                                              @NonNull final BasicFileAttributes attrs) {

                // Ignore any .git subtree
                if (keepGitStuff && dir.endsWith(GIT_REPO_DIRNAME)) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public @NonNull FileVisitResult visitFile(@NonNull final Path p,
                                                      @NonNull final BasicFileAttributes attrs)
                    throws IOException {

                // Don't delete README files
                if (!(keepGitStuff && p.endsWith(GIT_README_MD))) {
                    Files.delete(p);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public @NonNull FileVisitResult postVisitDirectory(@NonNull final Path dir,
                                                               final IOException ex)
                    throws IOException {

                // Don't delete the root dir or the .git dir
                if (!dir.equals(root) && !(keepGitStuff && dir.endsWith(GIT_REPO_DIRNAME))) {
                    Files.delete(dir);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Finds all the children of a GitRepo node, down to the next GitRepo node.
     * Runs recursively.
     *
     * @param node    The root node of the search.
     * @param docRefs The set of DocRefs that were found.
     */
    private void recurseExplorerNodes(final ExplorerNode node, final Set<DocRef> docRefs) {
        if (!node.getType().equals(GitRepoDoc.TYPE)) {
            docRefs.add(node.getDocRef());
        }
        final List<ExplorerNode> children = this.explorerNodeService.getChildren(node.getDocRef());
        for (final ExplorerNode child : children) {
            // Don't recurse any child GitRepoDoc nodes
            if (!child.getType().equals(GitRepoDoc.TYPE)) {
                this.recurseExplorerNodes(child, docRefs);
            }
        }
    }

    /**
     * Exports the given node to the given export directory in the standard
     * Stroom import/export format (unzipped).
     *
     * @param node      The root node to export.
     * @param exportDir The directory to export to.
     * @return The export summary.
     */
    private ExportSummary export(final List<ExplorerNode> gitRepoNodePath,
                                 final ExplorerNode node,
                                 final Path exportDir) {
        final Set<DocRef> docRefs = new HashSet<>();
        this.recurseExplorerNodes(node, docRefs);
        final Set<String> docTypesToIgnore = Set.of(GitRepoDoc.TYPE);

        return importExportSerializer.write(
                gitRepoNodePath,
                exportDir,
                docRefs,
                docTypesToIgnore,
                true);
    }

    /**
     * Returns the credentials to log into Git.
     *
     * @param gitRepoDoc       Where we get the credential ID from. Must not be null.
     * @param transportCommand Where to put the credentials. Accepts any kind of TransportCommand.
     */
    private void setGitCreds(final GitRepoDoc gitRepoDoc, final TransportCommand<?, ?> transportCommand)
            throws IOException {
        if (gitRepoDoc.needsCredentials()) {
            final String credentialsId = gitRepoDoc.getCredentialName();

            try {
                // Grab the credentials from the database
                final StoredSecret storedSecret = storedSecrets.get(credentialsId);
                final Credential credential = storedSecret.credential();

                // Note any field of the secrets might be null so handle that
                switch (credential.getCredentialType()) {
                    case USERNAME_PASSWORD -> {
                        if (storedSecret.secret() instanceof final UsernamePasswordSecret usernamePasswordSecret) {
                            String username = usernamePasswordSecret.getUsername();
                            if (username == null) {
                                username = EMPTY;
                            }
                            String password = usernamePasswordSecret.getPassword();
                            if (password == null) {
                                password = EMPTY;
                            }
                            transportCommand.setCredentialsProvider(
                                    new UsernamePasswordCredentialsProvider(username, password));
                        }
                    }
                    case ACCESS_TOKEN -> {
                        if (storedSecret.secret() instanceof final AccessTokenSecret accessTokenSecret) {
                            String accessToken = accessTokenSecret.getAccessToken();
                            if (accessToken == null) {
                                accessToken = EMPTY;
                            }
                            transportCommand.setCredentialsProvider(
                                    new UsernamePasswordCredentialsProvider(GIT_USERNAME, accessToken));
                        }
                    }
                    case SSH_KEY -> {
                        if (storedSecret.secret() instanceof final SshKeySecret sshKeySecret) {
                            transportCommand.setTransportConfigCallback(
                                    new CredentialsJgitSshTransportCallback(storedSecret, sshKeySecret, tempDir));
                        }
                    }
                    default -> throw new IOException("Unknown type of credentials: " + credential.getCredentialType());
                }
            } catch (final IOException e) {
                throw new IOException("Error getting Git credentials: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a git object by cloning the remote repository.
     * Note that the returned object is auto-closeable (try with resources).
     *
     * @param gitWorkDir The directory that is the root of the repo.
     * @return An auto-closeable Git object to use when accessing the repo.
     * @throws IOException if something goes wrong.
     */
    private Git gitConstructForPush(final GitRepoDoc gitRepoDoc, final Path gitWorkDir)
            throws IOException, GitAPIException {

        this.ensureDirectoryExists(gitWorkDir);

        // Wipe everything from the local git Work dir
        this.deleteFileTree(gitWorkDir, false);

        // Clone the remote repo
        // Note depth is 1 - we only want the latest items not the history
        LOGGER.debug("Cloning repository '{}' to '{}' for push", gitRepoDoc.getUrl(), gitWorkDir);
        final CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(gitRepoDoc.getUrl())
                .setDirectory(gitWorkDir.toFile())
                .setDepth(1)
                .setBranch(gitRepoDoc.getBranch())
                .setCloneAllBranches(false);

        setGitCreds(gitRepoDoc, cloneCommand);

        return cloneCommand.call();
    }

    /**
     * Clones the Git repository represented by gitRepoDoc
     * into the local gitWorkDir.
     *
     * @param gitRepoDoc Holds the settings of the Git Repo.
     * @param gitWorkDir Where to put the Git repo files.
     * @throws IOException If something goes wrong.
     */
    private void gitCloneForPull(final GitRepoDoc gitRepoDoc,
                                 final Path gitWorkDir)
            throws IOException {
        LOGGER.info("Cloning repository '{}' to '{}' for pull", gitRepoDoc.getUrl(), gitWorkDir);

        // Delete everything under gitWork (including all git stuff)
        this.ensureDirectoryExists(gitWorkDir);
        this.deleteFileTree(gitWorkDir, false);

        final String gitCommit = gitRepoDoc.getCommit();
        if (gitCommit == null || gitCommit.isEmpty()) {
            // No commit data so just do clone
            final CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(gitRepoDoc.getUrl())
                    .setDirectory(gitWorkDir.toFile())
                    .setDepth(1)
                    .setBranch(gitRepoDoc.getBranch())
                    .setCloneAllBranches(false);
            setGitCreds(gitRepoDoc, cloneCommand);

            try (final Git git = cloneCommand.call()) {

                // Store the commit we've got
                gitRepoDao.storeHash(gitRepoDoc.getUuid(), this.gitGetCurrentRevCommitName(git));

            } catch (final GitAPIException e) {
                LOGGER.error("Git error cloning repository '{}': {}", gitRepoDoc.getUrl(), e.getMessage(), e);
                throw new IOException("Git error cloning repository "
                                      + gitRepoDoc.getUrl() + ":\n"
                                      + walkExceptions(e), e);
            }
        } else {
            // We want a particular commit so get everything - all commits
            final CloneCommand cloneCommand = Git.cloneRepository()
                    .setURI(gitRepoDoc.getUrl())
                    .setDirectory(gitWorkDir.toFile())
                    .setBranch(gitRepoDoc.getBranch());
            setGitCreds(gitRepoDoc, cloneCommand);

            try (final Git git = cloneCommand.call()) {
                // Ok that worked; now find the commit in the repo
                git.checkout()
                        .setName(gitCommit)
                        .call();

                // Store the commit we've got
                gitRepoDao.storeHash(gitRepoDoc.getUuid(), this.gitGetCurrentRevCommitName(git));

            } catch (final GitAPIException e) {
                LOGGER.error("Error cloning git commit '{}': {}", gitCommit, e.getMessage(), e);
                throw new IOException("Git error cloning / checking out commit '"
                                      + gitCommit
                                      + "':\n" + walkExceptions(e), e);
            }

        }
    }

    /**
     * Generates an error message by walking the causes of an exception.
     *
     * @param e The root exception to look at.
     * @return The error messages from all the causes of the exception.
     */
    String walkExceptions(final Exception e) {
        final StringBuilder buf = new StringBuilder(e.getMessage());
        Throwable cause = e.getCause();
        while (cause != null) {
            buf.append(":\n");
            buf.append(cause.getMessage());
            cause = cause.getCause();
        }

        return buf.toString();
    }

    /**
     * Determines whether updates are available, by looking at the latest
     * commit in the downloaded data from Git and comparing it to the stored
     * value.
     *
     * @param messages   A list of messages to add to. Can be null.
     *                   If present then a diff will be inserted as a sequence of messages.
     * @param gitWorkDir The directory that is the root of the repo.
     * @return An auto-closeable Git object to use when accessing the repo.
     * @throws IOException if something goes wrong.
     */
    private boolean gitUpdatesAvailable(final List<String> messages,
                                        final GitRepoDoc gitRepoDoc,
                                        final Path gitWorkDir)
            throws IOException, GitAPIException {

        this.ensureDirectoryExists(gitWorkDir);

        // Wipe everything from the local git Work dir
        this.deleteFileTree(gitWorkDir, false);

        // Clone the remote repo
        // Note depth is 1 - we only want the latest items not the history
        LOGGER.debug(
                "Cloning repository '{}' to '{}' for whether updates are available",
                gitRepoDoc.getUrl(),
                gitWorkDir);

        // Depth to clone - as little as possible to save bandwidth
        int gitCloneDepth = 1;
        if (messages != null) {
            gitCloneDepth = Integer.MAX_VALUE;
        }

        final CloneCommand cloneCommand = Git.cloneRepository()
                .setURI(gitRepoDoc.getUrl())
                .setDirectory(gitWorkDir.toFile())
                .setDepth(gitCloneDepth)
                .setBranch(gitRepoDoc.getBranch())
                .setCloneAllBranches(false);
        setGitCreds(gitRepoDoc, cloneCommand);

        try (final Git git = cloneCommand.call()) {

            final String newGitCommitName = this.gitGetCurrentRevCommitName(git);
            final String oldGitCommitName = gitRepoDao.getHash(gitRepoDoc.getUuid());

            LOGGER.info("Stroom commit: {}; git commit available: {}; match: {}",
                    oldGitCommitName,
                    newGitCommitName,
                    Objects.equals(oldGitCommitName, newGitCommitName));
            if (messages != null) {
                if (oldGitCommitName == null) {
                    messages.add("Nothing downloaded; remote content available");
                } else {
                    this.generateGitDiff(messages,
                            git,
                            oldGitCommitName,
                            newGitCommitName);
                }
            }
            return !Objects.equals(oldGitCommitName, newGitCommitName);
        }
    }

    /**
     * Returns the current commit name for the currently cloned code
     * - whatever is in the local Git repo on disk.
     *
     * @param git The Git instance to query. Must not be null.
     * @return The string that identifies the commit. Never null.
     * @throws GitAPIException if something goes wrong.
     */
    private String gitGetCurrentRevCommitName(final Git git) throws GitAPIException {
        return git
                .log()
                .setMaxCount(1)
                .call()
                .iterator().next().getName();
    }

    /**
     * Populates the messages with a diff between the two commit hashes.
     *
     * @param messages            The list of messages to add to. Must not be null.
     * @param git                 Represents the Git repo. Must not be null.
     * @param gitRemoteCommitName The hash of the commit we've already got.
     *                            Might be null.
     * @param repoCommitName      The latest hash available in the remote store.
     */
    private void generateGitDiff(final List<String> messages,
                                 final Git git,
                                 final String gitRemoteCommitName,
                                 final String repoCommitName)
            throws IOException {

        // Git/jgit magic to covert a hash into a tree-ish thing
        final String HASH_SUFFIX = "^{tree}";

        // Don't show context lines to avoid UI clutter
        final int NUMBER_OF_CONTEXT_LINES = 0;

        try {
            try (final ObjectReader objReader = git.getRepository().newObjectReader()) {
                final Repository repo = git.getRepository();

                // Parser for the old version
                final CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
                final ObjectId oldTree = repo.resolve(gitRemoteCommitName + HASH_SUFFIX);
                oldTreeIter.reset(objReader, oldTree);

                // Parser for the new version
                final CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
                final ObjectId newTree = repo.resolve(repoCommitName + HASH_SUFFIX);
                newTreeIter.reset(objReader, newTree);

                // Generate diffs
                final OutputStream diffStream = new ByteArrayOutputStream();
                git.diff()
                        .setOldTree(oldTreeIter)
                        .setNewTree(newTreeIter)
                        .setSourcePrefix("Current/")
                        .setDestinationPrefix("Content Pack/")
                        .setContextLines(NUMBER_OF_CONTEXT_LINES)
                        .setOutputStream(diffStream)
                        .call();
                messages.add(diffStream.toString());
            }
        } catch (final GitAPIException e) {
            LOGGER.error("Error generating a diff: {}", e, e);
            throw new IOException("Error generating a diff for updates: " + walkExceptions(e), e);

        } catch (final IOException e) {
            LOGGER.error("Error generating a diff: {}", e, e);
            throw e;
        }
    }

    /**
     * Checks if any updates are available in the Git Repo.
     *
     * @param messages   List of messages; used to return the diff. Can be null.
     * @param gitRepoDoc The thing we want to check for updates. Must not be null.
     * @return true if updates are available, false if not.
     * @throws IOException if something goes wrong.
     */
    @Override
    public boolean areUpdatesAvailable(final List<String> messages, final GitRepoDoc gitRepoDoc) throws IOException {
        LOGGER.info("Checking if updates are available for '{}'", gitRepoDoc.getUrl());

        if (!gitRepoDoc.getUrl().isEmpty()) {
            final Path localDir = pathCreator.toAppPath(config.get().getLocalDir());
            try (final AutoDeletingTempDirectory gitWorkDir = new AutoDeletingTempDirectory(localDir, "gitrepo-")) {

                try {
                    return this.gitUpdatesAvailable(messages, gitRepoDoc, gitWorkDir.getDirectory());

                } catch (final GitAPIException e) {
                    LOGGER.error("Error checking for updates for GitRepo '{}': {}",
                            gitRepoDoc.getName(), e.getMessage(), e);
                    throw new IOException("Error checking for updates: " + walkExceptions(e), e);
                }
            }
        } else {
            return false;
        }
    }

    /**
     * Safely adds a subdirectory to the parent path given.
     * Ensures that the real path to the resulting 'subdirectory' starts
     * with the real path 'parent'.
     * Note that the 'parent' of the directory you are trying to resolve must
     * already exist on disk, so this isn't a general purpose function.
     * <p>
     * Package public static to allow testing.
     *
     * @param parent       The path that subDirectory should be under. Assumed to
     *                     be safe. Must not be null. Must exist on disk.
     * @param subDirectory The subdirectory path that should be under path.
     *                     Might not be safe so must be checked.
     *                     Must not be null. Might not exist on disk.
     * @return The path on disk to the subdirectory.
     */
    static Path addDirectoryToPath(final Path parent, final Path subDirectory)
            throws IOException {
        Objects.requireNonNull(parent);
        Objects.requireNonNull(subDirectory);

        final Path realPath;
        try {
            realPath = parent.toRealPath();
        } catch (final IOException e) {
            throw new IOException("Parent directory '" + parent + "' does not exist: ");
        }

        final Path subDirPath = realPath.resolve(subDirectory);
        final Path canonicalSubDirPath = subDirPath.toFile().getCanonicalFile().toPath();

        if (!canonicalSubDirPath.startsWith(realPath)) {
            throw new IOException("Invalid sub directory: '" + subDirectory);
        }
        return canonicalSubDirPath;
    }

    /**
     * Class to ensure that the temporary directory gets deleted (if possible).
     */
    public static class AutoDeletingTempDirectory implements AutoCloseable {

        /**
         * The directory that will automatically be deleted.
         */
        private final Path directory;

        /**
         * Constructor.
         *
         * @param location Where to create the temporary directory.
         * @param prefix   The start of the directory name.
         */
        public AutoDeletingTempDirectory(final Path location, final String prefix)
                throws IOException {
            // Try to create parent directories
            if (!location.toFile().exists()) {
                if (!location.toFile().mkdirs()) {
                    throw new IOException("Could not create directories for Git repository");
                }
            }

            // Create the temporary directory
            this.directory = Files.createTempDirectory(location, prefix);
        }

        /**
         * @return The temporary directory.
         */
        public Path getDirectory() {
            return directory;
        }

        /**
         * Automatically deletes the temporary directory.
         *
         * @throws IOException if something goes wrong.
         */
        @Override
        public void close() throws IOException {

            Files.walkFileTree(directory, new SimpleFileVisitor<>() {
                @Override
                public @NonNull FileVisitResult preVisitDirectory(@NonNull final Path dir,
                                                                  @NonNull final BasicFileAttributes attrs) {
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult visitFile(@NonNull final Path p,
                                                          @NonNull final BasicFileAttributes attrs)
                        throws IOException {

                    Files.delete(p);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NonNull FileVisitResult postVisitDirectory(@NonNull final Path dir,
                                                                   final IOException ex)
                        throws IOException {

                    Files.delete(dir);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }
}
