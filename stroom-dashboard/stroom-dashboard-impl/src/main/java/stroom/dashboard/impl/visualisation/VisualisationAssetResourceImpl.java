package stroom.dashboard.impl.visualisation;

import stroom.event.logging.api.DocumentEventLog;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.visualisation.shared.VisualisationAssetContent;
import stroom.visualisation.shared.VisualisationAssetResource;
import stroom.visualisation.shared.VisualisationAssetUpdateContent;
import stroom.visualisation.shared.VisualisationAssetUpdateDelete;
import stroom.visualisation.shared.VisualisationAssetUpdateNewFile;
import stroom.visualisation.shared.VisualisationAssetUpdateRename;
import stroom.visualisation.shared.VisualisationAssets;

import com.google.common.io.Files;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;

import static stroom.event.logging.rs.api.AutoLogged.OperationType.ALLOCATE_AUTOMATICALLY;

/**
 * Serverside REST handling for Visualisation Assets.
 */
@AutoLogged(ALLOCATE_AUTOMATICALLY)
public class VisualisationAssetResourceImpl implements VisualisationAssetResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationAssetResourceImpl.class);

    /** Service that backs the resource */
    private final Provider<VisualisationAssetService> serviceProvider;

    /** Configuration */
    private final Provider<VisualisationAssetConfig> configProvider;

    @SuppressWarnings("unused")
    @Inject
    VisualisationAssetResourceImpl(final Provider<VisualisationAssetService> serviceProvider,
                                   final Provider<VisualisationAssetConfig> configProvider,
                                   final Provider<DocumentEventLog> docEventLogProvider) {
        this.serviceProvider = serviceProvider;
        this.configProvider = configProvider;
    }

    @Override
    public VisualisationAssets fetchDraftAssets(final String ownerId) throws RuntimeException {
        try {
            return serviceProvider.get().fetchDraftAssets(ownerId);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in fetchDraftAssets: {}", t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public Boolean updateNewFolder(final String ownerDocId, final String path)
            throws RuntimeException {

        try {
            serviceProvider.get().updateNewFolder(ownerDocId, path);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in updateNewFolder: {}", t.getMessage(), t);
            throw t;
        }

        return Boolean.TRUE;
    }

    @Override
    public Boolean updateNewFile(final String ownerDocId, final VisualisationAssetUpdateNewFile update)
            throws RuntimeException {
        try {
            serviceProvider.get().updateNewFile(ownerDocId, update.getPath());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in updateNewFile: {}", t.getMessage(), t);
            throw t;
        }

        return Boolean.TRUE;
    }

    @Override
    public Boolean updateNewUploadedFile(final String ownerDocId,
                                         final VisualisationAssetUpdateNewFile update)
            throws RuntimeException {
        try {
            serviceProvider.get().updateNewUploadedFile(
                    ownerDocId,
                    update.getPath(),
                    update.getResourceKey());

        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in updateNewUploadedFile: {}", t.getMessage(), t);
            throw t;
        }

        return Boolean.TRUE;
    }

    @Override
    public Boolean updateDelete(final String ownerDocId, final VisualisationAssetUpdateDelete update)
            throws RuntimeException {
        try {
            serviceProvider.get().updateDelete(ownerDocId, update.getPath(), update.isFolder());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in updateDelete: {}", t.getMessage(), t);
            throw t;
        }

        return Boolean.TRUE;
    }

    @Override
    public Boolean updateRename(final String ownerDocId,
                                final VisualisationAssetUpdateRename update)
            throws RuntimeException {
        try {
            serviceProvider.get().updateRename(
                    ownerDocId,
                    update.getOldPath(),
                    update.getNewPath(),
                    update.isFolder());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in updateRename: {}", t.getMessage(), t);
            throw t;
        }

        return Boolean.TRUE;
    }

    @Override
    public Boolean updateContent(final String ownerDocId,
                                 final VisualisationAssetUpdateContent update)
            throws RuntimeException {
        try {
            serviceProvider.get().updateContent(ownerDocId, update.getPath(), update.getContent());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in updateContent: {}", t.getMessage(), t);
            throw t;
        }

        return Boolean.TRUE;
    }

    @Override
    public VisualisationAssetContent getDraftContent(final String ownerDocId, final String path) {
        try {
            final String content = serviceProvider.get().getDraftContent(ownerDocId, path);
            final String extension = Files.getFileExtension(path);
            final VisualisationAssetConfig config = configProvider.get();
            final String editorMode =
                    config.getAceEditorModes().getOrDefault(extension, config.getDefaultAceEditorMode());

            LOGGER.info("Content '{}' has extension '{}' -> editor mode '{}'", path, extension, editorMode);
            return new VisualisationAssetContent(content, editorMode);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in getDraftContent: {}", t.getMessage(), t);
            throw t;
        }
    }

    @Override
    public Boolean saveDraftToLive(final String ownerDocId) throws RuntimeException {
        try {
            serviceProvider.get().saveDraftToLive(ownerDocId);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in saveDraftToLive: {}", t.getMessage(), t);
            throw t;
        }
        return Boolean.TRUE;
    }

    @Override
    public Boolean revertDraftFromLive(final String ownerDocId) throws RuntimeException {
        try {
            serviceProvider.get().revertDraftFromLive(ownerDocId);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        } catch (final Throwable t) {
            LOGGER.error("Error in revertDraftFromLive: {}", t.getMessage(), t);
            throw t;
        }
        return Boolean.TRUE;
    }

}
