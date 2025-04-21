package stroom.gitrepo.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.explorer.api.ExplorerService;
import stroom.explorer.shared.ExplorerNode;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Clearable;

import jakarta.inject.Singleton;
import jakarta.inject.Inject;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

import java.io.File;
import java.io.IOException;
import java.util.List;
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
     * Where we're going to get the GitRepoDoc from.
     */
    private final Store<GitRepoDoc> store;

    /**
     * Logger so we can follow what is going on.
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoStorageService.class);

    /**
     * Constructor so we can log when this object is constructed.
     */
    @Inject
    public GitRepoStorageService(final ExplorerService explorerService,
                                 final GitRepoSerialiser serialiser,
                                 final StoreFactory storeFactory) {
        this.explorerService = explorerService;
        this.store = storeFactory.createStore(serialiser, GitRepoDoc.TYPE, GitRepoDoc.class);
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
                this.updateGit(event, path);
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
     * Updates GIT based on the data received from the event system.
     * @param event
     * @param path
     */
    private void updateGit(final EntityEvent event, final List<ExplorerNode> path) {
        // TODO
        // Get git repository settings from GitRepo
        ExplorerNode gitRepoNode = path.getFirst();
        DocRef gitRepoDocRef = gitRepoNode.getDocRef();
        GitRepoDoc gitRepoDoc = store.readDocument(gitRepoDocRef);
        LOGGER.error("Using GitRepoDoc with settings: {}", gitRepoDoc);

        // Build the filename of the object that needs updating
        StringBuilder sb = new StringBuilder();
        List<ExplorerNode> nodePath = path.subList(1, path.size());
        nodePath.forEach(node -> {
            sb.append(File.separator);
            sb.append(node.getName());
        });
        File filePath = new File(sb.toString());
        LOGGER.error("Storing object at '{}'", filePath);

        // TODO Find the local GIT repository
        File gitWorkDir = new File("~/stroom-git");

        try (Repository repo = new FileRepositoryBuilder()
                .setWorkTree(gitWorkDir).build()) {

            try (Git git = Git.wrap(repo)) {


            }
        }
        catch (IOException e) {
            // Do something!
            LOGGER.error("Error storing object in GIT: {}", e, e);
        }
    }
}
