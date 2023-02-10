package stroom.docstore.fav.impl;

import stroom.docref.DocRef;
import stroom.docstore.fav.api.DocFavService;
import stroom.docstore.shared.DocFavResource;
import stroom.event.logging.api.StroomEventLoggingService;
import stroom.event.logging.api.StroomEventLoggingUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import event.logging.MultiObject;
import event.logging.UpdateEventAction;

import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

public class DocFavResourceImpl implements DocFavResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DocFavResourceImpl.class);

    private final Provider<DocFavService> docFavService;
    private final Provider<StroomEventLoggingService> eventLoggingService;

    @Inject
    DocFavResourceImpl(final Provider<DocFavService> docFavService,
                       final Provider<StroomEventLoggingService> eventLoggingService) {
        this.docFavService = docFavService;
        this.eventLoggingService = eventLoggingService;
    }

    @Override
    public void create(final DocRef docRef) {
        LOGGER.debug("Setting document {} as favourite", docRef);
        eventLoggingService.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                .withDescription("Set document as favourite")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .withObjects(eventLoggingService.get().convert(docRef))
                                .build())
                        .build())
                .withSimpleLoggedAction(() -> {
                    docFavService.get().create(docRef);
                })
                .runActionAndLog();
    }

    @Override
    public void delete(final DocRef docRef) {
        LOGGER.debug("Unsetting document {} as favourite", docRef);
        eventLoggingService.get().loggedWorkBuilder()
                .withTypeId(StroomEventLoggingUtil.buildTypeId(this, "update"))
                .withDescription("Unset document as favourite")
                .withDefaultEventAction(UpdateEventAction.builder()
                        .withAfter(MultiObject.builder()
                                .withObjects(eventLoggingService.get().convert(docRef))
                                .build())
                        .build())
                .withSimpleLoggedAction(() -> {
                    docFavService.get().delete(docRef);
                })
                .runActionAndLog();
    }

    @Override
    public List<DocRef> fetchDocFavs() {
        return docFavService.get().fetchDocFavs();
    }
}
