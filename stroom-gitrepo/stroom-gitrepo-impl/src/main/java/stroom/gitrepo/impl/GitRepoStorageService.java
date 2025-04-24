package stroom.gitrepo.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.gitrepo.api.GitRepoConfig;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.api.ImportExportActionHandlersApi;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;
import stroom.util.shared.Message;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Class to work out whether a given item is a descendant of a GitRepo and
 * thus should be saved to or loaded from Git as well as the DB.
 */
@Singleton
@EntityEventHandler(action = {
        //EntityAction.CREATE,
        EntityAction.POST_CREATE,
        EntityAction.UPDATE,
        EntityAction.PRE_DELETE,
        EntityAction.DELETE/*,
        EntityAction.CLEAR_CACHE,
        EntityAction.CREATE_EXPLORER_NODE,
        EntityAction.UPDATE_EXPLORER_NODE,
        EntityAction.DELETE_EXPLORER_NODE*/ })
public class GitRepoStorageService implements EntityEvent.Handler, Clearable {

    /**
     * The tree model to find parents of the event item.
     */
    private final ExplorerService explorerService;

    /**
     * Finds children of an ExplorerNode
     */
    private final ExplorerNodeService explorerNodeService;

    /**
     * Where we're going to get the GitRepoDoc from.
     */
    private final Store<GitRepoDoc> store;

    /**
     * Provides a means of getting the object that converts a Doc
     * to a Map of bytes.
     */
    private final ImportExportActionHandlersApi importExportActionHandlers;

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
     * Filename to put into folders to ensure that GIT preserves the folder.
     */
    private static final String GIT_KEEP_FILENAME = ".gitkeep";

    /**
     * Name of the Git repository directory. We need to ensure we don't delete
     * this!
     */
    private static final String GIT_REPO_DIRNAME = ".git";

    /**
     * Constructor so we can log when this object is constructed.
     */
    @Inject
    public GitRepoStorageService(final ExplorerService explorerService,
                                 final ExplorerNodeService explorerNodeService,
                                 final GitRepoSerialiser serialiser,
                                 final StoreFactory storeFactory,
                                 final ImportExportActionHandlersApi importExportActionHandlers,
                                 final GitRepoConfig config,
                                 final PathCreator pathCreator) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.store = storeFactory.createStore(serialiser, GitRepoDoc.TYPE, GitRepoDoc.class);
        this.importExportActionHandlers = importExportActionHandlers;
        this.config = config;
        this.pathCreator = pathCreator;
        LOGGER.error("================================= GitRepoStorageService::ctor()");
    }

    /**
     * Called when an event happens - CRUD.
     * @param event The event.
     */
    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.error("================================= GitRepoStorageService::onChange({})", event);

        // Convert the docRef to an ExplorerNode
        Optional<ExplorerNode> oNode = explorerService.getFromDocRef(event.getDocRef());
        if (oNode.isPresent()) {
            ExplorerNode node = oNode.get();

            // Search up the tree
            List<ExplorerNode> path = explorerService.getAncestorOfDocType(GitRepoDoc.TYPE, node);

            if (! path.isEmpty()) {
                LOGGER.error("========================= Found GitRepo ancestor path '{}'", path);
                try {
                    this.updateGit(event, path);
                }
                catch (IOException e) {
                    // TODO Handle this exception properly
                    LOGGER.error("Could not update GIT", e);
                }
            } else {
                LOGGER.error("========================= No GitRepo ancestor!");
            }
        } else {
            LOGGER.error("================= Cannot convert event's DocRef to ExplorerNode: '{}'", event);
        }
    }

    /**
     * ?
     */
    @Override
    public void clear() {
        LOGGER.error("================================= GitRepoStorageService::clear()");
    }

    /**
     * Returns the GitRepoDoc that is represented by the first element in the path.
     * @param path The path to the changed object. Should start with the
     *             ancestor GitRepo.
     * @return The GitRepoDoc that holds the GIT settings to use.
     */
    private GitRepoDoc getGitRepoDoc(final List<ExplorerNode> path) {
        ExplorerNode gitRepoNode = path.getFirst();
        DocRef gitRepoDocRef = gitRepoNode.getDocRef();
        GitRepoDoc gitRepoDoc = store.readDocument(gitRepoDocRef);
        LOGGER.error("Using GitRepoDoc with settings: {}", gitRepoDoc);
        return gitRepoDoc;
    }

    /**
     * Returns the path to where the given file should be on disk.
     * @param gitWorkDir Path to the GIT work directory - the root of the
     *                   GIT directories containing the .git directory.
     *                   Must not be null.
     * @param path Path from the GitRepoDoc to the object we're interested in.
     * @return Path on filesystem to the object we're interested in.
     */
    private Path getDocPath(final Path gitWorkDir, final List<ExplorerNode> path) {
        // Remove the first element from the list as this is the GitRepoDoc
        List<ExplorerNode> nodePath = path.subList(1, path.size());

        // Put everything else on the path
        Path docPath = gitWorkDir;
        for (final ExplorerNode explorerNode : nodePath) {
            docPath = docPath.resolve(explorerNode.getName());
        }

        return docPath;
    }

    /**
     * Checks to see if a path is secure; i.e. is within the Git work directory.
     * Throws an exception if the path is not secure.
     * There may be object names such as /etc/passwd or ../.ssh/ - we need
     * to ensure these cannot be used to break security.
     * @param gitWorkDir The docPath must be under this directory.
     * @param docPath The path to check
     * @throws IOException if the docPath isn't under gitWorkDir.
     */
    private void checkPathIsWithinDirectory(final Path gitWorkDir,
                                            final Path docPath)
            throws IOException {

        // Get the fully resolved path
        final Path resolvedPath = docPath.toFile().getCanonicalFile().toPath();

        if (! resolvedPath.startsWith(gitWorkDir)) {
            throw new IOException("Document path '" + docPath
            + "' is not within the git working directory '"
            + gitWorkDir + "'");
        }
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
            } else {
                LOGGER.error("Created directories for {}", path);
            }
        }
    }

    /**
     * Saves the docRef to the given path.
     * The document is stored as a number of files with different extensions.
     * @param docRef The document we want to save to disk.
     * @param docPath The directory we're going to save the document in.
     * @param objectName The name of the document.
     * @throws IOException if something goes wrong with the save.
     */
    private void saveDocToDir(final DocRef docRef,
                              final Path docPath,
                              final String objectName)
            throws IOException {

        if (docRef.getType().equals(ExplorerConstants.FOLDER_TYPE)) {
            // Folders map to filesystem directories
            // so we'll put a .gitkeep file into the directory to ensure
            // git keeps them. There isn't an ImportExportActionHandler
            // for Folders.
            Path gitKeepPath = docPath.resolve(GIT_KEEP_FILENAME);
            if (! Files.exists(gitKeepPath)) {
                try {
                    Files.createFile(gitKeepPath);
                }
                catch (IOException e) {
                    LOGGER.warn("Error creating file '{}'", docPath, e);
                }
            }
        } else {
            // Find the thing that will export this document type
            Map<String, ImportExportActionHandler> actionHandlers =
                    this.importExportActionHandlers.getHandlers();
            ImportExportActionHandler actionHandler =
                    actionHandlers.get(docRef.getType());
            if (actionHandler == null) {
                throw new RuntimeException(
                        "No import/export action handler available for type '"
                        + docRef.getType() + "'.");
            }

            // Export the document to a Map
            List<Message> messageList = new ArrayList<>();
            Map<String, byte[]> exportedDoc =
                    actionHandler.exportDocument(docRef,
                            true,
                            messageList);

            // Write the Map to disk
            for (Map.Entry<String, byte[]> entry : exportedDoc.entrySet()) {
                Path entryPath = docPath.resolve(objectName + "." + entry.getKey());
                this.checkPathIsWithinDirectory(docPath, entryPath);
                try (FileOutputStream fos = new FileOutputStream(entryPath.toFile())) {
                    fos.write(entry.getValue());
                    LOGGER.error("==== Wrote file {}", entryPath);
                }
            }
        }
    }

    /**
     * Deletes a file tree recursively. Does not delete any .git
     * directories.
     * @param root Delete everything under this directory.
     * @throws IOException if something goes wrong.
     */
    private void deleteFileTree(Path root) throws IOException {
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult
            preVisitDirectory(Path dir,
                              BasicFileAttributes attrs) {

                // Ignore any .git subtree
                if (dir.endsWith(GIT_REPO_DIRNAME)) {
                    LOGGER.error(">>>>> Ignoring .git subtree");
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
                LOGGER.error(">>>>>> Deleting file '{}'", p);
                Files.delete(p);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult
            postVisitDirectory(Path dir, IOException ex)
                   throws IOException {

                // Don't delete the root dir or the .git dir
                if (!dir.equals(root) && !dir.endsWith(GIT_REPO_DIRNAME)) {
                        LOGGER.error(">>>>>> Deleting directory '{}'", dir);
                        Files.delete(dir);
                } else {
                    LOGGER.error(">>>>> Not deleting directory {}", dir);
                }

                return FileVisitResult.CONTINUE;
            }
        });
    }

    /**
     * Recurses the ExplorerNode tree, writing out nodes to disk.
     * Does not recurse past GitRepoDoc nodes as these are responsible for
     * their own contents.
     * @param node The root node to recurse. Must not be null.
     * @param diskPath Where to write the root node on disk. Must not be null.
     * @throws IOException if something goes wrong.
     */
    private void recurseExplorerNodes(final ExplorerNode node, final Path diskPath)
            throws IOException {

        // Save this node to disk
        LOGGER.error("Saving node '{}' to disk at '{}'", node, diskPath);

        // Save the document to directory, but don't save the GitRepoDoc itself
        this.ensureDirectoryExists(diskPath);
        if (!node.getType().equals(GitRepoDoc.TYPE)) {
            this.saveDocToDir(node.getDocRef(),
                    diskPath,
                    node.getName());
        }

        // Recurse children
        List<ExplorerNode> children = this.explorerNodeService.getChildren(node.getDocRef());
        for (ExplorerNode child : children) {
            // Don't recurse any child GitRepoDoc nodes
            if (!child.getType().equals(GitRepoDoc.TYPE)) {
                Path childPath = diskPath.resolve(child.getName());
                this.recurseExplorerNodes(child, childPath);
            }
        }
    }

    /**
     * Returns the credentials to log into Git.
     * @param gitRepoDoc Where we get the credential data from.
     * @return Credentials to log into a remote GIT repo.
     */
    private CredentialsProvider getGitCreds(GitRepoDoc gitRepoDoc) {
        String username = gitRepoDoc.getUsername();
        String password = gitRepoDoc.getPassword();
        LOGGER.error("\\\\\\\\ Creds: {}: {}", username, password);
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
        Path gitRepoDir = gitWorkDir.resolve(GIT_REPO_DIRNAME);

        Git git;

        // Initialise the git repo if necessary
        if (!Files.exists(gitRepoDir)) {
            // Checkout from repo

            // Clone the remote repo
            git = Git.cloneRepository()
                    .setURI(gitRepoDoc.getUrl())
                    .setDirectory(gitWorkDir.toFile())
                    .setCredentialsProvider(this.getGitCreds(gitRepoDoc))
                    .call();
        }
        else {
            git = Git.open(gitWorkDir.toFile());
        }

        return git;
    }

    /**
     * Updates GIT based on the data received from the event system.
     * @param event The event that occurred.
     * @param path The path from the GitRepoDoc node to the node that
     *             changed.
     */
    private void updateGit(final EntityEvent event,
                           final List<ExplorerNode> path)
            throws IOException {

        // Get git repository settings from GitRepo
        GitRepoDoc gitRepoDoc = this.getGitRepoDoc(path);

        // Find the path to the root of the local Git repository
        final Path localDir = pathCreator.toAppPath(config.getLocalDir());
        LOGGER.error("Local directory is '{}'", localDir);
        final Path gitWork = localDir.resolve(gitRepoDoc.getUuid());
        LOGGER.error("Git work directory is '{}'", gitWork);

        // Delete everything under gitWork (but not the .git directory)
        this.ensureDirectoryExists(gitWork);
        this.deleteFileTree(gitWork);

        // Create Git object for the gitWork directory
        try (Git git = this.gitConstruct(gitRepoDoc, gitWork)) {

            // Start at the root node, recurse down
            this.recurseExplorerNodes(path.getFirst(), gitWork);

            // Add everything to commit & commit locally
            git.add().addFilepattern(".").call();
            git.commit()
                    .setCommitter("Anonymous", gitRepoDoc.getUsername())
                    .setMessage("Automatic commit")
                    .call();

            // Push to remote
            git.push().setCredentialsProvider(this.getGitCreds(gitRepoDoc)).call();
            LOGGER.error("Pushed to git");
        }
        catch (GitAPIException e) {
            throw new IOException("Couldn't commit and push GIT", e);
        }
    }
}
