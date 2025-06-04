package stroom.gitrepo.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.gitrepo.api.GitRepoConfig;
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
import jakarta.inject.Singleton;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
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
public class GitRepoStorageService {

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
    private final GitRepoConfig config;

    /**
     * Object to create paths for local storage.
     */
    private final PathCreator pathCreator;

    /**
     * Logger so we can follow what is going on.
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoStorageService.class);

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
     * Constructor so we can log when this object is constructed.
     * Called by injection system.
     */
    @SuppressWarnings("unused")
    @Inject
    public GitRepoStorageService(final ExplorerService explorerService,
                                 final ExplorerNodeService explorerNodeService,
                                 final ImportExportSerializer importExportSerializer,
                                 final GitRepoConfig config,
                                 final PathCreator pathCreator) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.importExportSerializer = importExportSerializer;
        this.config = config;
        this.pathCreator = pathCreator;
    }

    /**
     * Called by pressing the Git Settings 'Push to Git' button.
     * Synchronised to avoid multiple threads writing to the same
     * directory structure.
     *
     * @param gitRepoDoc    The document that we're pushing the button on.
     *                      Must not be null.
     * @param commitMessage The Git commit message. Must not be null.
     * @param calledFromUi True if the method is being called from the UI over
     *                     REST, false if being called from a Job.
     *                     Affects how some errors are handled.
     * @return The export summary. Might return if the export hasn't yet taken
     * place.
     * @throws IOException if something goes wrong
     */
    public synchronized List<Message> exportDoc(final GitRepoDoc gitRepoDoc,
                                                final String commitMessage,
                                                final boolean calledFromUi)
            throws IOException {
        LOGGER.debug("Exporting document '{}' to GIT; UUID is '{}'", gitRepoDoc.getUrl(), gitRepoDoc.getUuid());
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
            final Path localDir = pathCreator.toAppPath(config.getLocalDir());
            final Path gitWorkDir = localDir.resolve(gitRepoDoc.getUuid());

            // Create Git object for the gitWork directory
            try (final Git git = this.gitConstructForPush(gitRepoDoc, gitWorkDir)) {

                // The export directory is somewhere within the gitWorkDir,
                // defined by the path the user specified
                final Path exportDir = addDirectoryToPath(gitWorkDir, Paths.get(gitRepoDoc.getPath()));
                this.ensureDirectoryExists(exportDir);

                // Delete all the files that are currently in the repo
                // so we can overwrite any changes and detect deletions.
                // We keep the README.md and the .git/ directory.
                this.deleteFileTree(exportDir, true);

                // Export everything from Stroom to local git repo dir
                final ExportSummary exportSummary = this.export(
                        gitRepoNodePath,
                        gitRepoExplorerNode,
                        exportDir);
                messages.addAll(exportSummary.getMessages());
                messages.add(new Message(Severity.INFO, "Export to disk successful"));

                // Has anything changed against the remote?
                final Status gitStatus = git.status().call();
                if (!gitStatus.isClean()) {

                    this.gitStatusToMessages(gitStatus, messages);

                    // Add everything to commit & commit locally
                    // Match already tracked files - detects deletions
                    git.add().setUpdate(true).addFilepattern(".").call();
                    // Match new files
                    git.add().setUpdate(false).addFilepattern(".").call();

                    git.commit()
                            .setCommitter(GIT_USERNAME, gitRepoDoc.getUsername())
                            .setMessage(commitMessage)
                            .call();
                    messages.add(new Message(Severity.INFO, "Local commit successful"));

                    // Push to remote
                    git.push().setCredentialsProvider(this.getGitCreds(gitRepoDoc)).call();
                    messages.add(new Message(Severity.INFO, "Pushed to Git"));
                } else {
                    // Jobs don't need to know that this didn't do anything
                    if (calledFromUi) {
                        throw new IOException("No local changes; therefore not pushing to Git");
                    } else {
                        LOGGER.info("{}: No local changes; not pushing to Git", gitRepoDoc.getName());
                    }
                }
            } catch (final GitAPIException e) {
                this.throwException("Couldn't commit and push GIT", e, messages);
            } catch (final IOException e) {
                this.throwException("Error pushing to GIT", e, messages);
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
     * Puts the git status into the messages to send back to the user.
     *
     * @param gitStatus The status of the repo after export.
     * @param messages  The list of messages to return to the user.
     */
    private void gitStatusToMessages(final Status gitStatus,
                                     final List<Message> messages) {
        for (final var filename : gitStatus.getUncommittedChanges()) {
            messages.add(new Message(Severity.INFO, "Changed: " + filename));
        }
        for (final var dirname : gitStatus.getUntrackedFolders()) {
            messages.add(new Message(Severity.INFO, "New folder: " + dirname));
        }
        for (final var filename : gitStatus.getUntracked()) {
            messages.add(new Message(Severity.INFO, "New file: " + filename));
        }
        for (final var filename : gitStatus.getMissing()) {
            messages.add(new Message(Severity.INFO, "Deleted: " + filename));
        }
        for (final var filename : gitStatus.getModified()) {
            messages.add(new Message(Severity.INFO, "Modified: " + filename));
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
    public synchronized List<Message> importDoc(final GitRepoDoc gitRepoDoc) throws IOException {
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
            final Path localDir = pathCreator.toAppPath(config.getLocalDir());
            final Path gitWork = localDir.resolve(gitRepoDoc.getUuid());

            // Grab everything from server - it won't be too big
            this.gitCloneForPull(gitRepoDoc, gitWork);
            messages.add(new Message(Severity.INFO, "Cloned from Git repository"));

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
                    .build();

            // Set (remote) directory
            final Path pathToImport;
            if (gitRepoDoc.getPath() != null && !gitRepoDoc.getPath().isEmpty()) {
                pathToImport = addDirectoryToPath(gitWork, Paths.get(gitRepoDoc.getPath()));
            } else {
                pathToImport = gitWork;
            }

            final Set<DocRef> docRefs = importExportSerializer.read(pathToImport, importStates, importSettings);
            for (final var docRef : docRefs) {
                // ImportExportSerializerImpl adds the System docref to the returned set,
                // but we don't use that here, so ignore it
                if (!docRef.equals(ExplorerConstants.SYSTEM_DOC_REF)) {
                    messages.add(new Message(Severity.INFO, "Imported '" + docRef.getName() + "'"));
                }
            }
            messages.add(new Message(Severity.INFO, "Completed Git Pull"));

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
        final var buf = new StringBuilder(errorMessage);
        if (cause != null) {
            buf.append("\n    ");
            buf.append(cause.getMessage());
        }
        if (!messages.isEmpty()) {
            buf.append("\n\nAdditional information:");
            for (final var m : messages) {
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
                                      + "' for GIT document");
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
            public FileVisitResult preVisitDirectory(final Path dir, final BasicFileAttributes attrs) {

                // Ignore any .git subtree
                if (keepGitStuff && dir.endsWith(GIT_REPO_DIRNAME)) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(final Path p, final BasicFileAttributes attrs)
                    throws IOException {

                // Don't delete README files
                if (!(keepGitStuff && p.endsWith(GIT_README_MD))) {
                    Files.delete(p);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(final Path dir, final IOException ex)
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
     * @param gitRepoDoc Where we get the credential data from. Must not be null.
     * @return Credentials to log into a remote GIT repo. Never returns null.
     */
    private CredentialsProvider getGitCreds(final GitRepoDoc gitRepoDoc) {
        final String username = gitRepoDoc.getUsername();
        final String password = gitRepoDoc.getPassword();
        return new UsernamePasswordCredentialsProvider(username, password);
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
        return Git.cloneRepository()
                .setURI(gitRepoDoc.getUrl())
                .setDirectory(gitWorkDir.toFile())
                .setCredentialsProvider(this.getGitCreds(gitRepoDoc))
                .setDepth(1)
                .setBranch(gitRepoDoc.getBranch())
                .setCloneAllBranches(false)
                .call();
    }

    /**
     * Clones the GIT repository represented by gitRepoDoc
     * into the local gitWorkDir.
     *
     * @param gitRepoDoc Holds the settings of the Git Repo.
     * @param gitWorkDir Where to put the Git repo files.
     * @throws IOException If something goes wrong.
     */
    private void gitCloneForPull(final GitRepoDoc gitRepoDoc, final Path gitWorkDir)
            throws IOException {
        LOGGER.info("Cloning repository '{}' to '{}' for pull", gitRepoDoc.getUrl(), gitWorkDir);

        // Delete everything under gitWork (including all git stuff)
        this.ensureDirectoryExists(gitWorkDir);
        this.deleteFileTree(gitWorkDir, false);

        try (final Git git = Git.cloneRepository()
                .setURI(gitRepoDoc.getUrl())
                .setDirectory(gitWorkDir.toFile())
                .setCredentialsProvider(this.getGitCreds(gitRepoDoc))
                .setDepth(1)
                .setBranch(gitRepoDoc.getBranch())
                .setCloneAllBranches(false)
                .call()) {

            // No code - close automatically
        } catch (final GitAPIException e) {
            throw new IOException("Git error cloning repository "
                                  + gitRepoDoc.getUrl(), e);
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
            throw new IOException("Parent directory '" + parent + "' does not exist");
        }

        final Path subDirPath = realPath.resolve(subDirectory);
        final Path canonicalSubDirPath = subDirPath.toFile().getCanonicalFile().toPath();

        if (!canonicalSubDirPath.startsWith(realPath)) {
            throw new IOException("Invalid sub directory: '" + subDirectory);
        }
        return canonicalSubDirPath;
    }

}
