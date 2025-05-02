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

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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
     *
     * @param gitRepoDoc    The document that we're pushing the button on.
     *                      Must not be null.
     * @param commitMessage The Git commit message. Must not be null.
     * @return The export summary. Might return if the export hasn't yet taken
     *         place.
     * @throws IOException if something goes wrong
     */
    public List<Message> exportDoc(GitRepoDoc gitRepoDoc,
                                   final String commitMessage)
            throws IOException {
        LOGGER.info("Exporting document '{}' to GIT; UUID is '{}'", gitRepoDoc, gitRepoDoc.getUuid());
        List<Message> messages = new ArrayList<>();

        DocRef gitRepoDocRef = GitRepoDoc.getDocRef(gitRepoDoc.getUuid());
        Optional<ExplorerNode> optGitRepoExplorerNode = explorerService.getFromDocRef(gitRepoDocRef);
        ExplorerNode gitRepoExplorerNode = optGitRepoExplorerNode.orElseThrow(IOException::new);

        // Work out where the GitRepo node is in the explorer tree
        List<ExplorerNode> gitRepoNodePath = this.explorerNodeService.getPath(gitRepoDocRef);
        gitRepoNodePath.add(gitRepoExplorerNode);

        // Only try to do anything if the settings exist
        if (!gitRepoDoc.getUrl().isEmpty()) {
            // Find the path to the root of the local Git repository
            final Path localDir = pathCreator.toAppPath(config.getLocalDir());
            final Path gitWork = localDir.resolve(gitRepoDoc.getUuid());

            // Delete everything under gitWork (but not the .git directory)
            this.ensureDirectoryExists(gitWork);
            this.deleteFileTree(gitWork, true);

            // Create Git object for the gitWork directory
            try (Git git = this.gitConstruct(gitRepoDoc, gitWork)) {

                // Export everything
                ExportSummary exportSummary =
                        this.export(gitRepoNodePath, gitRepoExplorerNode, gitWork);
                messages.addAll(exportSummary.getMessages());
                messages.add(new Message(Severity.INFO, "Export to disk successful"));

                // Add everything to commit & commit locally
                // We add as 'update' and 'not as update' to catch deleted files.
                git.add().setUpdate(false).addFilepattern(".").call();
                git.add().setUpdate(true).addFilepattern(".").call();
                git.commit()
                        .setCommitter("Anonymous", gitRepoDoc.getUsername())
                        .setMessage(commitMessage)
                        .call();
                messages.add(new Message(Severity.INFO, "Local commit successful"));

                // Push to remote
                git.push().setCredentialsProvider(this.getGitCreds(gitRepoDoc)).call();
                messages.add(new Message(Severity.INFO, "Pushed to Git"));
            } catch (GitAPIException e) {
                this.throwException("Couldn't commit and push GIT", e, messages);
            } catch (IOException e) {
                this.throwException("Error pushing to GIT", e, messages);
            }
        } else {
            throw new IOException("Git repository URL isn't configured; cannot push");
        }

        return messages;
    }

    /**
     * Called when the user presses the Pull from Git button in the UI.
     * @param gitRepoDoc The document holding the Git repo settings
     * @return A list of messages about the import
     * @throws IOException if something goes wrong
     */
    public List<Message> importDoc(GitRepoDoc gitRepoDoc) throws IOException {
        List<Message> messages = new ArrayList<>();

        DocRef gitRepoDocRef = GitRepoDoc.getDocRef(gitRepoDoc.getUuid());
        Optional<ExplorerNode> optGitRepoExplorerNode = explorerService.getFromDocRef(gitRepoDocRef);
        ExplorerNode gitRepoExplorerNode = optGitRepoExplorerNode.orElseThrow(IOException::new);

        // Work out where the GitRepo node is in the explorer tree
        List<ExplorerNode> gitRepoNodePath = this.explorerNodeService.getPath(gitRepoDocRef);
        gitRepoNodePath.add(gitRepoExplorerNode);

        // Only try to do anything if the settings exist
        if (!gitRepoDoc.getUrl().isEmpty()) {
            // Find the path to the root of the local Git repository
            final Path localDir = pathCreator.toAppPath(config.getLocalDir());
            final Path gitWork = localDir.resolve(gitRepoDoc.getUuid());

            // Delete everything under gitWork (including all git stuff)
            this.ensureDirectoryExists(gitWork);
            this.deleteFileTree(gitWork, false);

            // Grab everything from server - it won't be too big
            // Create Git object for the gitWork directory
            this.gitClone(gitRepoDoc, gitWork);
            messages.add(new Message(Severity.INFO, "Cloned from Git repository"));

            // ImportSettings.auto() is used in a few places. This consists of
            // .importMode(ImportMode.IGNORE_CONFIRMATION)
            // .enableFilters(true)
            // We want to make sure that the import goes to the root of the
            // gitRepoDocRef rather than where it might have been in the
            // original export.

            List<ImportState> importStates = new ArrayList<>();
            ImportSettings importSettings = ImportSettings.builder()
                    .importMode(ImportMode.IGNORE_CONFIRMATION)
                    .enableFilters(false)
                    .useImportFolders(true)
                    .useImportNames(true)
                    .rootDocRef(gitRepoDocRef)
                    .build();
            Set<DocRef> docRefs = importExportSerializer.read(gitWork, importStates, importSettings);
            for (var docRef : docRefs) {
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
     * @param errorMessage The message that describes the problem. Must not
     *                     be null.
     * @param cause The exception that caused this error. Can be null if no
     *              triggering exception.
     * @param messages Any messages from the export. Never null. Can be empty.
     * @throws IOException suitable for throwing to indicate to the caller
     * that an error has occurred.
     */
    private void throwException(String errorMessage,
                                Exception cause,
                                List<Message> messages)
    throws IOException {
        LOGGER.error("{}, {}, {}", errorMessage, cause, messages);
        var buf = new StringBuilder(errorMessage);
        if (cause != null) {
            buf.append("\n    ");
            buf.append(cause.getMessage());
        }
        if (!messages.isEmpty()) {
            buf.append("\n\nAdditional information:");
            for (var m : messages) {
                buf.append("\n    ");
                buf.append(m);
            }
        }
        throw new IOException(buf.toString(), cause);
    }

    /**
     * Ensures that the given path exists. The path is assumed to be all
     * directories - there is no terminal filename at the end.
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
     * @param root Delete everything under this directory. Must not be null.
     * @param keepGitStuff If true then keep Git key files - .git/, README.md.
     *                     If false then delete everything.
     * @throws IOException if something goes wrong.
     */
    private void deleteFileTree(final Path root, final boolean keepGitStuff)
            throws IOException {

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult
            preVisitDirectory(Path dir,
                              BasicFileAttributes attrs) {

                // Ignore any .git subtree
                if (keepGitStuff && dir.endsWith(GIT_REPO_DIRNAME)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                else {
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult
            visitFile(Path p, BasicFileAttributes attrs)
                   throws IOException {
                // Don't delete README files
                if ( !(keepGitStuff && p.endsWith(GIT_README_MD)) ) {
                    Files.delete(p);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult
            postVisitDirectory(Path dir, IOException ex)
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
     * @param node The root node of the search.
     * @param docRefs The set of DocRefs that were found.
     */
    private void recurseExplorerNodes(final ExplorerNode node, Set<DocRef> docRefs) {
        if (!node.getType().equals(GitRepoDoc.TYPE)) {
            docRefs.add(node.getDocRef());
        }
        List<ExplorerNode> children = this.explorerNodeService.getChildren(node.getDocRef());
        for (ExplorerNode child : children) {
            // Don't recurse any child GitRepoDoc nodes
            if (!child.getType().equals(GitRepoDoc.TYPE)) {
                this.recurseExplorerNodes(child, docRefs);
            }
        }
    }

    /**
     * Exports the given node to the given export directory in the standard
     * Stroom import/export format (unzipped).
     * @param node The root node to export.
     * @param exportDir The directory to export to.
     * @return The export summary.
     */
    private ExportSummary export(List<ExplorerNode> gitRepoNodePath,
                       ExplorerNode node,
                       Path exportDir) {
        final Set<DocRef> docRefs = new HashSet<>();
        this.recurseExplorerNodes(node, docRefs);

        return importExportSerializer.write(
                gitRepoNodePath,
                exportDir,
                docRefs,
                true);
    }

    /**
     * Returns the credentials to log into Git.
     * @param gitRepoDoc Where we get the credential data from.
     * @return Credentials to log into a remote GIT repo.
     */
    private CredentialsProvider getGitCreds(GitRepoDoc gitRepoDoc) {
        String username = gitRepoDoc.getUsername();
        String password = gitRepoDoc.getPassword();
        return new UsernamePasswordCredentialsProvider(username, password);
    }

    /**
     * Creates a git object. Either opens an existing repo or inits a new one.
     * Note that the returned object is auto-closeable (try with resources).
     * @param gitWorkDir The directory that is the root of the repo.
     * @return An auto-closeable Git object to use when accessing the repo.
     * @throws IOException if something goes wrong.
     */
    private Git gitConstruct(final GitRepoDoc gitRepoDoc, final Path gitWorkDir)
            throws IOException, GitAPIException {

        // This refers to the .git directory within the Work directory
        Path gitRepoDir = gitWorkDir.resolve(GIT_REPO_DIRNAME);

        // Git root object
        final Git git;

        // Initialise the git repo if necessary
        if (!Files.exists(gitRepoDir)) {
            // Clone the remote repo
            // Note depth is 1 - we only want the latest items not the history
            LOGGER.info("Cloning repository '{}' to '{}'", gitRepoDoc.getUrl(), gitWorkDir);
            git = Git.cloneRepository()
                    .setURI(gitRepoDoc.getUrl())
                    .setDirectory(gitWorkDir.toFile())
                    .setCredentialsProvider(this.getGitCreds(gitRepoDoc))
                    .setDepth(1)
                    .call();
        }
        else {
            git = Git.open(gitWorkDir.toFile());
        }

        return git;
    }

    /**
     * Clones the GIT repository represented by gitRepoDoc
     * into the local gitWorkDir.
     * @param gitRepoDoc Holds the settings of the Git Repo.
     * @param gitWorkDir Where to put the Git repo files.
     * @throws IOException If something goes wrong.
     */
    private void gitClone(final GitRepoDoc gitRepoDoc, final Path gitWorkDir)
        throws IOException {
        LOGGER.info("Cloning repository '{}' to '{}'", gitRepoDoc.getUrl(), gitWorkDir);
        try (Git git = Git.cloneRepository()
                .setURI(gitRepoDoc.getUrl())
                .setDirectory(gitWorkDir.toFile())
                .setCredentialsProvider(this.getGitCreds(gitRepoDoc))
                .setDepth(1)
                .call()) {
            // No code - close automatically
        }
        catch (GitAPIException e) {
            throw new IOException("Git error cloning repository "
                                  + gitRepoDoc.getUrl(), e);
        }
    }

}
