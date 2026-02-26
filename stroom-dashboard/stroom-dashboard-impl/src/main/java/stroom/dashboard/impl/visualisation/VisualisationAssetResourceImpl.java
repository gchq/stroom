package stroom.dashboard.impl.visualisation;

import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.visualisation.shared.VisualisationAssetContent;
import stroom.visualisation.shared.VisualisationAssetResource;
import stroom.visualisation.shared.VisualisationAssetSaveAsParameters;
import stroom.visualisation.shared.VisualisationAssetUpdateContent;
import stroom.visualisation.shared.VisualisationAssetUpdateDelete;
import stroom.visualisation.shared.VisualisationAssetUpdateNewFile;
import stroom.visualisation.shared.VisualisationAssetUpdateRename;
import stroom.visualisation.shared.VisualisationAssets;

import com.google.common.io.Files;
import event.logging.CreateEventAction;
import event.logging.DeleteEventAction;
import event.logging.Document;
import event.logging.File;
import event.logging.Folder;
import event.logging.MoveEventAction;
import event.logging.MultiObject;
import event.logging.MultiObject.Builder;
import event.logging.UpdateEventAction;
import event.logging.ViewEventAction;
import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.io.IOException;
import java.math.BigInteger;

import static stroom.event.logging.rs.api.AutoLogged.OperationType.MANUALLY_LOGGED;

/**
 * Serverside REST handling for Visualisation Assets.
 */
@AutoLogged(MANUALLY_LOGGED)
public class VisualisationAssetResourceImpl implements VisualisationAssetResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(VisualisationAssetResourceImpl.class);

    /** Service that backs the resource */
    private final Provider<VisualisationAssetService> serviceProvider;

    /** Configuration */
    private final Provider<VisualisationAssetConfig> configProvider;

    /** Event logging */
    private final Provider<StroomEventLoggingService> eventLoggingServiceProvider;

    @SuppressWarnings("unused")
    @Inject
    VisualisationAssetResourceImpl(final Provider<VisualisationAssetService> serviceProvider,
                                   final Provider<VisualisationAssetConfig> configProvider,
                                   final Provider<StroomEventLoggingService> eventLoggingServiceProvider) {
        this.serviceProvider = serviceProvider;
        this.configProvider = configProvider;
        this.eventLoggingServiceProvider = eventLoggingServiceProvider;
    }

    @Override
    public VisualisationAssets fetchDraftAssets(final String ownerId) throws RuntimeException {

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();
        final ViewEventAction viewEventAction = ViewEventAction.builder()
                .addDocument(Document.builder().withId(ownerId).build())
                .build();

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "fetchDraftAssets"))
                .withDescription("View all assets for a Visualisation Document (saved or unsaved)")
                .withDefaultEventAction(viewEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        return serviceProvider.get().fetchDraftAssets(ownerId);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in fetchDraftAssets: {}", t.getMessage(), t);
                        throw t;
                    }
                })
                .getResultAndLog();
    }

    @Override
    public Boolean updateNewFolder(final String ownerDocId, final String path)
            throws RuntimeException {

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();
        final Folder newFolder = Folder.builder().withPath(path).build();
        final CreateEventAction createEventAction = CreateEventAction.builder()
                .addFolder(newFolder)
                .addDocument(Document.builder().withId(ownerDocId).build())
                .build();

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateNewFolder"))
                .withDescription("Create new folder for assets (unsaved)")
                .withDefaultEventAction(createEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().updateNewFolder(ownerDocId, path);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in updateNewFolder: {}", t.getMessage(), t);
                        throw t;
                    }
                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

    @Override
    public Boolean updateNewFile(final String ownerDocId, final VisualisationAssetUpdateNewFile update)
            throws RuntimeException {

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();
        final File newFile = File.builder()
                .withPath(update.getPath())
                .withSize(BigInteger.ZERO)
                .build();
        final CreateEventAction createEventAction = CreateEventAction.builder()
                .addFile(newFile)
                .addDocument(Document.builder().withId(ownerDocId).build())
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateNewFile"))
                .withDescription("Create new file asset with zero size (unsaved)")
                .withDefaultEventAction(createEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().updateNewFile(ownerDocId, update.getPath());
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in updateNewFile: {}", t.getMessage(), t);
                        throw t;
                    }
                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

    @Override
    public Boolean updateNewUploadedFile(final String ownerDocId,
                                         final VisualisationAssetUpdateNewFile update)
            throws RuntimeException {

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final File uploadedFile = File.builder()
                .withPath(update.getPath())
                .build();
        final CreateEventAction createEventAction = CreateEventAction.builder()
                .addFile(uploadedFile)
                .addDocument(Document.builder().withId(ownerDocId).build())
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateNewUploadedFile"))
                .withDescription("Create new file asset from HTTP file upload (unsaved)")
                .withDefaultEventAction(createEventAction)
                .withSimpleLoggedResult(() -> {
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
                })
                .getResultAndLog();
    }

    @Override
    public Boolean updateDelete(final String ownerDocId, final VisualisationAssetUpdateDelete update)
            throws RuntimeException {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final DeleteEventAction deleteEventAction;
        final String description;

        if (update.isFolder()) {
            description = "Delete a folder and all assets under it (unsaved)";
            final Folder deletedFolder = Folder.builder()
                    .withPath(update.getPath())
                    .build();
            deleteEventAction = DeleteEventAction.builder()
                    .addFolder(deletedFolder)
                    .addDocument(Document.builder().withId(ownerDocId).build())
                    .build();
        } else {
            description = "Delete a file asset (unsaved)";
            final File deletedFile = File.builder()
                    .withPath(update.getPath())
                    .build();
            deleteEventAction = DeleteEventAction.builder()
                    .addFile(deletedFile)
                    .addDocument(Document.builder().withId(ownerDocId).build())
                    .build();
        }

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateDelete"))
                .withDescription(description)
                .withDefaultEventAction(deleteEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().updateDelete(ownerDocId, update.getPath(), update.isFolder());
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in updateDelete: {}", t.getMessage(), t);
                        throw t;
                    }

                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

    @Override
    public Boolean updateRename(final String ownerDocId,
                                final VisualisationAssetUpdateRename update)
            throws RuntimeException {

        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final MoveEventAction moveEventAction;
        final String description;
        if (update.isFolder()) {
            description = "Rename a folder and all assets under it (unsaved)";

            final Folder sourceFolder = Folder.builder()
                    .withPath(update.getOldPath())
                    .build();
            final Folder destFolder = Folder.builder()
                    .withPath(update.getNewPath())
                    .build();
            moveEventAction = MoveEventAction.builder()
                    .withSource()
                    .addFolder(sourceFolder)
                    .addDocument(Document.builder().withId(ownerDocId).build())
                    .end()
                    .withDestination().addFolder(destFolder).end()
                    .build();
        } else {
            description = "Rename a file asset (unsaved)";

            final File sourceFile = File.builder()
                    .withPath(update.getOldPath())
                    .build();
            final File destFile = File.builder()
                    .withPath(update.getNewPath())
                    .build();
            moveEventAction = MoveEventAction.builder()
                    .withSource()
                    .addFile(sourceFile)
                    .addDocument(Document.builder().withId(ownerDocId).build())
                    .end()
                    .withDestination().addFile(destFile).end()
                    .build();
        }

        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateRename"))
                .withDescription(description)
                .withDefaultEventAction(moveEventAction)
                .withSimpleLoggedResult(() -> {
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
                })
                .getResultAndLog();
    }

    @Override
    public Boolean updateContent(final String ownerDocId,
                                 final VisualisationAssetUpdateContent update)
            throws RuntimeException {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final File updatedFile = File.builder()
                .withPath(update.getPath())
                .withSize(BigInteger.valueOf(update.getContent().length))
                .build();
        final UpdateEventAction updateEventAction = UpdateEventAction.builder()
                .withAfter()
                .addFile(updatedFile)
                .addDocument(Document.builder().withId(ownerDocId).build())
                .end()
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "updateContent"))
                .withDescription("Update the content of a file asset (unsaved)")
                .withDefaultEventAction(updateEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().updateContent(ownerDocId, update.getPath(), update.getContent());
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in updateContent: {}", t.getMessage(), t);
                        throw t;
                    }
                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

    @Override
    public VisualisationAssetContent getDraftContent(final String ownerDocId, final String path) {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final File viewedFile = File.builder()
                .withPath(path)
                .build();
        final ViewEventAction viewEventAction = ViewEventAction.builder()
                .addFile(viewedFile)
                .addDocument(Document.builder().withId(ownerDocId).build())
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "getDraftContent"))
                .withDescription("View the unsaved or saved content of a file asset")
                .withDefaultEventAction(viewEventAction)
                .withSimpleLoggedResult(() -> {
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
                })
                .getResultAndLog();
    }

    @Override
    public Boolean saveDraftToLive(final String ownerDocId) throws RuntimeException {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final UpdateEventAction updateEventAction = UpdateEventAction.builder()
                .withAfter().addDocument(Document.builder().withId(ownerDocId).build()).end()
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "saveDraftToLive"))
                .withDescription("Make all unsaved changes permanent")
                .withDefaultEventAction(updateEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().saveDraftToLive(ownerDocId);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in saveDraftToLive: {}", t.getMessage(), t);
                        throw t;
                    }
                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

    @Override
    public Boolean revertDraftFromLive(final String ownerDocId) throws RuntimeException {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        final UpdateEventAction updateEventAction = UpdateEventAction.builder()
                .withAfter().addDocument(Document.builder().withId(ownerDocId).build()).end()
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "revertDraftFromLive"))
                .withDescription("Delete all unsaved changes and revert to the previous saved state")
                .withDefaultEventAction(updateEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().revertDraftFromLive(ownerDocId);
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in revertDraftFromLive: {}", t.getMessage(), t);
                        throw t;
                    }
                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

    @Override
    public Boolean saveAs(final String fromOwnerDocId, final VisualisationAssetSaveAsParameters updateParameters) {
        final StroomEventLoggingService eventLoggingService = eventLoggingServiceProvider.get();

        // Log any content changes
        final Builder<Void> beforeBuilder = MultiObject.builder();
        beforeBuilder.addDocument(Document.builder().withId(fromOwnerDocId).build());
        if (updateParameters.getUpdatedContentPath() != null && updateParameters.getUpdatedContent() != null) {
            beforeBuilder.addFile(File.builder()
                    .withPath(updateParameters.getUpdatedContentPath())
                    .withSize(BigInteger.valueOf(updateParameters.getUpdatedContent().length))
                    .build()
            );
        }

        final UpdateEventAction updateEventAction = UpdateEventAction.builder()
                .withBefore(beforeBuilder.build())
                .withAfter().addDocument(Document.builder().withId(updateParameters.getToOwnerDocId()).build()).end()
                .build();
        return eventLoggingService.loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "saveAs"))
                .withDescription("Copy all assets (saved and unsaved) from the existing document into a new document")
                .withDefaultEventAction(updateEventAction)
                .withSimpleLoggedResult(() -> {
                    try {
                        serviceProvider.get().saveAs(fromOwnerDocId,
                                updateParameters.getToOwnerDocId(),
                                updateParameters.getUpdatedContentPath(),
                                updateParameters.getUpdatedContent());
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    } catch (final Throwable t) {
                        LOGGER.error("Error in saveAs: {}", t.getMessage(), t);
                        throw t;
                    }

                    return Boolean.TRUE;
                })
                .getResultAndLog();
    }

}
