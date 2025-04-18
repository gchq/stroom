package stroom.gitrepo.impl;

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

import java.util.Optional;

/**
 * Class to work out whether a given item is a descendant of a GitRepo and
 * thus should be saved to or loaded from Git as well as the DB.
 */
@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.POST_CREATE,
        EntityAction.UPDATE,
        EntityAction.PRE_DELETE,
        EntityAction.DELETE,
        EntityAction.CLEAR_CACHE,
        EntityAction.CREATE_EXPLORER_NODE,
        EntityAction.UPDATE_EXPLORER_NODE,
        EntityAction.DELETE_EXPLORER_NODE })
public class GitRepoStorageService implements EntityEvent.Handler, Clearable {

    /**
     * The tree model to find parents of the event item.
     */
    private final ExplorerService explorerService;

    /**
     * Logger so we can follow what is going on.
     */
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GitRepoStorageService.class);

    /**
     * Constructor so we can log when this object is constructed.
     */
    @Inject
    public GitRepoStorageService(final ExplorerService explorerService) {
        this.explorerService = explorerService;
        LOGGER.error("================================= GitRepoStorageService::ctor()");
    }

    /**
     * Called when an event happens - CRUD.
     * @param event The event.
     */
    @Override
    public void onChange(final EntityEvent event) {
        LOGGER.error("================================= GitRepoStorageService::onChange({})", event);

        /*if (event.getAction().equals(EntityAction.CREATE)) {
            // Rebuild the tree to find any new nodes
            LOGGER.error("===================== Requesting explorerService tree rebuild");
            explorerService.rebuildTree();
        }*/

        // Convert the docRef to an ExplorerNode
        Optional<ExplorerNode> oNode = explorerService.getFromDocRef(event.getDocRef());
        if (oNode.isPresent()) {
            ExplorerNode node = oNode.get();

            // Search up the tree
            ExplorerNode ancestor = explorerService.getAncestorOfDocType(GitRepoDoc.TYPE, node);

            if (ancestor != null) {
                LOGGER.error("========================= Found GitRepo ancestor '{}'", ancestor);
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
}
