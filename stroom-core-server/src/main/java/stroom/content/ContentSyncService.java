package stroom.content;

import io.dropwizard.lifecycle.Managed;
import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.logging.LoggingFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.importexport.server.DocRefs;
import stroom.importexport.server.DocumentData;
import stroom.importexport.server.ImportExportActionHandler;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.query.api.v2.DocRef;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentSyncService implements Managed {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentSyncService.class);

    private final ContentSyncConfig contentSyncConfig;
    private final Map<String, ImportExportActionHandler> importExportActionHandlers;

    private volatile ScheduledExecutorService scheduledExecutorService;

    public ContentSyncService(final ContentSyncConfig contentSyncConfig, final Map<String, ImportExportActionHandler> importExportActionHandlers) {
        this.contentSyncConfig = contentSyncConfig;
        this.importExportActionHandlers = importExportActionHandlers;
    }

    @Override
    public synchronized void start() {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutorService.scheduleWithFixedDelay(this::sync, 0, contentSyncConfig.getSyncFrequency(), TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public synchronized void stop() {
        if (scheduledExecutorService != null) {
            scheduledExecutorService.shutdown();
            scheduledExecutorService = null;
        }
    }

    public void sync() {
        importExportActionHandlers.forEach((type, importExportActionHandler) -> {
            try {
                final String url = contentSyncConfig.getUpstreamUrl().get(type);
                if (url != null) {
                    LOGGER.info("Synching content from '" + url + "'");
                    final Response response = createClient(url, "/list").get();
                    if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
                        LOGGER.error(response.getStatusInfo().getReasonPhrase());
                    } else {
                        final DocRefs docRefs = response.readEntity(DocRefs.class);
                        docRefs.getSet().forEach(docRef -> importDocument(url, docRef, importExportActionHandler));
                    }
                }
            } catch (final Exception e) {
                LOGGER.error(e.getMessage());
            }
        });
    }

    private void importDocument(final String url, final DocRef docRef, final ImportExportActionHandler importExportActionHandler) {
        LOGGER.info("Fetching " + docRef.getType() + " " + docRef.getUuid());
        final Response response = createClient(url, "/export").post(Entity.json(docRef));
        if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
            LOGGER.error(response.getStatusInfo().getReasonPhrase());
        } else {
            final DocumentData documentData = response.readEntity(DocumentData.class);
            final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
            importExportActionHandler.importDocument(documentData.getDocRef(), documentData.getDataMap(), importState, ImportMode.IGNORE_CONFIRMATION);
        }
    }

    private Invocation.Builder createClient(final String url, final String path) {
        final Client client = ClientBuilder.newClient(new ClientConfig().register(LoggingFeature.class));
        final WebTarget webTarget = client.target(url).path(path);
        final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        invocationBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + contentSyncConfig.getApiKey());
        return invocationBuilder;
    }
}
