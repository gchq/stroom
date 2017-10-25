package stroom.annotations;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.explorer.server.ExplorerActionHandler;
import stroom.query.api.v2.DocRef;

public class StroomAnnotationsStore implements ExplorerActionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(StroomAnnotationsStore.class);

    @Override
    public DocRef createDocument(String name, String parentFolderUUID) {
        LOGGER.warn("Attempted to Create Instance of Singleton Annotations DocRef");
        return null;
    }

    @Override
    public DocRef copyDocument(String uuid, String parentFolderUUID) {
        LOGGER.warn("Attempted to Copy Instance of Singleton Annotations DocRef");
        return null;
    }

    @Override
    public DocRef moveDocument(String uuid, String parentFolderUUID) {
        LOGGER.warn("Attempted to Move Instance of Singleton Annotations DocRef");
        return null;
    }

    @Override
    public DocRef renameDocument(String uuid, String name) {
        LOGGER.warn("Attempted to Rename Instance of Singleton Annotations DocRef");
        return null;
    }

    @Override
    public void deleteDocument(String uuid) {
        LOGGER.warn("Attempted to Delete Instance of Singleton Annotations DocRef");

    }
}
