package stroom.kafka.impl;

import com.codahale.metrics.health.HealthCheck;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentResourceHelper;
import stroom.event.logging.api.DocumentEventLog;
import stroom.kafka.pipeline.KafkaConfigStore;
import stroom.kafkaConfig.shared.KafkaConfigDoc;
import stroom.kafkaConfig.shared.KafkaConfigResource;
import stroom.security.api.SecurityContext;
import stroom.resource.api.ResourceStore;
import stroom.util.HasHealthCheck;
import stroom.util.io.StreamUtil;
import stroom.util.shared.EntityServiceException;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;

import javax.inject.Inject;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

public class KafkaConfigResourceImpl implements KafkaConfigResource, HasHealthCheck {
    private final KafkaConfigStore kafkaConfigStore;
    private final DocumentResourceHelper documentResourceHelper;
    private final ResourceStore resourceStore;
    private final DocumentEventLog documentEventLog;
    private final SecurityContext securityContext;

    @Inject
    KafkaConfigResourceImpl(final KafkaConfigStore kafkaConfigStore,
                           final DocumentResourceHelper documentResourceHelper,
                           final ResourceStore resourceStore,
                           final DocumentEventLog documentEventLog,
                           final SecurityContext securityContext) {
        this.kafkaConfigStore = kafkaConfigStore;
        this.documentResourceHelper = documentResourceHelper;
        this.resourceStore = resourceStore;
        this.documentEventLog = documentEventLog;
        this.securityContext = securityContext;
    }

    @Override
    public KafkaConfigDoc read(final DocRef docRef) {
        return documentResourceHelper.read(kafkaConfigStore, docRef);
    }

    @Override
    public KafkaConfigDoc update(final KafkaConfigDoc doc) {
        return documentResourceHelper.update(kafkaConfigStore, doc);
    }

    @Override
    public ResourceGeneration download(final DocRef kafkaConfigDocRef) {
        return securityContext.secureResult(() -> {
            // Get dictionary.
            final KafkaConfigDoc kafkaConfigDoc = kafkaConfigStore.readDocument(kafkaConfigDocRef);
            if (kafkaConfigDoc == null) {
                throw new EntityServiceException("Unable to find kafka config");
            }

            try {
                final ResourceKey resourceKey = resourceStore.createTempFile("kafkaConfig.properties");
                final Path file = resourceStore.getTempFile(resourceKey);
                Files.writeString(file, kafkaConfigDoc.getData(), StreamUtil.DEFAULT_CHARSET);
                documentEventLog.download(kafkaConfigDoc, null);
                return new ResourceGeneration(resourceKey, new ArrayList<>());

            } catch (final IOException e) {
                documentEventLog.download(kafkaConfigDoc, null);
                throw new UncheckedIOException(e);
            }
        });
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}