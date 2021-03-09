package stroom.kafka.impl;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.rs.api.AutoLogged;
import stroom.kafka.shared.KafkaConfigDoc;
import stroom.kafka.shared.KafkaConfigResource;
import stroom.resource.api.ResourceStore;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.FetchWithUuid;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
public class KafkaConfigResourceImpl implements KafkaConfigResource, FetchWithUuid<KafkaConfigDoc> {
    private static final Logger LOGGER = LoggerFactory.getLogger(KafkaConfigResourceImpl.class);

    private final Provider<KafkaConfigStore> kafkaConfigStoreProvider;
    private final Provider<DocumentResourceHelper> documentResourceHelperProvider;
    private final Provider<ResourceStore> resourceStoreProvider;

    @Inject
    KafkaConfigResourceImpl(final Provider<KafkaConfigStore> kafkaConfigStoreProvider,
                            final Provider<DocumentResourceHelper> documentResourceHelperProvider,
                            final Provider<ResourceStore> resourceStoreProvider) {
        this.kafkaConfigStoreProvider = kafkaConfigStoreProvider;
        this.documentResourceHelperProvider = documentResourceHelperProvider;
        this.resourceStoreProvider = resourceStoreProvider;
    }

    @Override
    public KafkaConfigDoc fetch(final String uuid) {
        return documentResourceHelperProvider.get().read(kafkaConfigStoreProvider.get(), getDocRef(uuid));
    }

    @Override
    public KafkaConfigDoc update(final String uuid, final KafkaConfigDoc doc) {
        if (doc.getUuid() == null || !doc.getUuid().equals(uuid)) {
            throw new EntityServiceException("The document UUID must match the update UUID");
        }
        return documentResourceHelperProvider.get().update(kafkaConfigStoreProvider.get(), doc);
    }

    private DocRef getDocRef(final String uuid) {
        return DocRef.builder()
                .uuid(uuid)
                .type(KafkaConfigDoc.DOCUMENT_TYPE)
                .build();
    }

    @Override
    public ResourceGeneration download(final DocRef kafkaConfigDocRef) {
        final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStoreProvider.get().readDocument(kafkaConfigDocRef);
        if (kafkaConfigDoc == null) {
            throw new EntityServiceException("Unable to find kafka config");
        }

        final ResourceKey resourceKey = resourceStoreProvider.get().createTempFile("kafkaConfig.properties");
        final Path file = resourceStoreProvider.get().getTempFile(resourceKey);
        try {
            Files.writeString(file, kafkaConfigDoc.getData(), StreamUtil.DEFAULT_CHARSET);
        } catch (IOException e) {
            LOGGER.error("Unable to download KafkaConfig", e);
            throw new UncheckedIOException(e);
        }

        return new ResourceGeneration(resourceKey, new ArrayList<>());
    }
}
