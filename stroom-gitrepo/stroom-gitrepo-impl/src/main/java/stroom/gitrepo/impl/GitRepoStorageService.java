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
import stroom.importexport.api.ExportSummary;
import stroom.importexport.api.ImportExportSerializer;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

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
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
     * Constructor so we can log when this object is constructed.
     */
    @Inject
    public GitRepoStorageService(final ExplorerService explorerService,
                                 final ExplorerNodeService explorerNodeService,
                                 final GitRepoSerialiser serialiser,
                                 final StoreFactory storeFactory,
                                 final ImportExportSerializer importExportSerializer,
                                 final GitRepoConfig config,
                                 final PathCreator pathCreator) {
        this.explorerService = explorerService;
        this.explorerNodeService = explorerNodeService;
        this.store = storeFactory.createStore(serialiser, GitRepoDoc.TYPE, GitRepoDoc.class);
        this.importExportSerializer = importExportSerializer;
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
            //List<ExplorerNode> path = explorerService.getAncestorOfDocType(GitRepoDoc.TYPE, node);
            Optional<ExplorerNode> gitRepoAncestor = this.getGitRepoAncestor(node);
            if (gitRepoAncestor.isPresent()) {
                LOGGER.error("========================= Found GitRepo ancestor path '{}'", gitRepoAncestor.get());
                try {
                    this.updateGit(gitRepoAncestor.get());
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
     * @param gitRepoNode The ExplorerNode representing the GitRepo.
     * @return The GitRepoDoc that holds the GIT settings to use.
     */
    private GitRepoDoc getGitRepoDoc(ExplorerNode gitRepoNode) {
        DocRef gitRepoDocRef = gitRepoNode.getDocRef();
        GitRepoDoc gitRepoDoc = store.readDocument(gitRepoDocRef);
        LOGGER.error("Using GitRepoDoc with settings: {}", gitRepoDoc);
        return gitRepoDoc;
    }

    /**
     * Returns the GitRepo ancestor of the current node, if it exists.
     * May return the current node if that is a GitRepo.
     * @param node The node to start searching at.
     * @return Optional containing the GitRepo ancestor, or an empty
     * Optional if there is no GitRepo ancestor.
     */
    private Optional<ExplorerNode> getGitRepoAncestor(ExplorerNode node) {
        var currentNode = node;
        ExplorerNode gitRepoNode = null;
        do {
            if (currentNode.getType().equals(GitRepoDoc.TYPE)) {
                gitRepoNode = currentNode;
            }
            else {
                var optionalCurrentNode =
                        this.explorerNodeService.getParent(currentNode.getDocRef());
                currentNode = optionalCurrentNode.orElse(null);
            }
        } while (currentNode != null
                 && gitRepoNode == null
                 && !currentNode.getType().equals(ExplorerConstants.SYSTEM_TYPE));

        return Optional.ofNullable(currentNode);
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
     * Finds all the children of a GitRepo node, down to the next GitRepo node.
     * Runs recursively.
     * @param node The root node of the search.
     * @param docRefs The set of DocRefs that were found.
     */
    private void recurseExplorerNodes(final ExplorerNode node, Set<DocRef> docRefs) {
        if (!node.getType().equals(GitRepoDoc.TYPE)) {
            LOGGER.error(">>>> Adding node '{}' to export", node);
            docRefs.add(node.getDocRef());
        }
        List<ExplorerNode> children = this.explorerNodeService.getChildren(node.getDocRef());
        for (ExplorerNode child : children) {
            // Don't recurse any child GitRepoDoc nodes
            if (!child.getType().equals(GitRepoDoc.TYPE)) {
                LOGGER.error(">>>> Found child node '{}'", child);
                this.recurseExplorerNodes(child, docRefs);
            }
        }
    }

    /**
     * Exports the given node to the given export directory in the standard
     * Stroom import/export format (unzipped).
     * @param node The root node to export.
     * @param exportDir The directory to export to.
     */
    public void export(ExplorerNode node,
                       Path exportDir) {
        final Set<DocRef> docRefs = new HashSet<>();
        this.recurseExplorerNodes(node, docRefs);

        final ExportSummary exportSummary =
                importExportSerializer.write(exportDir, docRefs, true);
        // TODO - report the exportSummary
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
     * @param gitRepoExplorerNode The node of the GitRepo that we're
     *                           going to update.
     */
    private void updateGit(ExplorerNode gitRepoExplorerNode)
            throws IOException {

        // Get git repository settings from GitRepo
        GitRepoDoc gitRepoDoc = this.getGitRepoDoc(gitRepoExplorerNode);

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

            // Export everything
            this.export(gitRepoExplorerNode, gitWork);

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
