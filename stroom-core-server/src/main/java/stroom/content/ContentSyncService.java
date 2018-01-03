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
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class ContentSyncService implements Managed {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentSyncService.class);

    private final ContentSyncConfig contentSyncConfig;
    private final ImportExportActionHandler importExportActionHandler;

    private volatile ScheduledExecutorService scheduledExecutorService;

    public ContentSyncService(final ContentSyncConfig contentSyncConfig, final ImportExportActionHandler importExportActionHandler) {
        this.contentSyncConfig = contentSyncConfig;
        this.importExportActionHandler = importExportActionHandler;
    }

    @Override
    public synchronized void start() {
        if (scheduledExecutorService == null) {
            scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
            scheduledExecutorService.scheduleAtFixedRate(this::sync, 0, contentSyncConfig.getSyncFrequency(), TimeUnit.MILLISECONDS);
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
        LOGGER.info("Synching content from '" + contentSyncConfig.getUpstreamUrl() + "'");
        final Response response = createClient("/list").get();
        if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
            LOGGER.error(response.getStatusInfo().getReasonPhrase());
        } else {
            final DocRefs docRefs = response.readEntity(DocRefs.class);
            docRefs.getSet().forEach(this::fetchDocument);
        }
    }

    private void fetchDocument(final DocRef docRef) {
        LOGGER.info("Fetching " + docRef.getType() + " " + docRef.getUuid());
        final Response response = createClient("/export").post(Entity.json(docRef));
        if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
            LOGGER.error(response.getStatusInfo().getReasonPhrase());
        } else {
            final DocumentData documentData = response.readEntity(DocumentData.class);
            final ImportState importState = new ImportState(documentData.getDocRef(), documentData.getDocRef().getName());
            importExportActionHandler.importDocument(documentData.getDocRef(), documentData.getDataMap(), importState, ImportMode.IGNORE_CONFIRMATION);
        }
    }

    private Invocation.Builder createClient(final String path) {
        final Client client = ClientBuilder.newClient(new ClientConfig().register(LoggingFeature.class));
        final WebTarget webTarget = client.target(contentSyncConfig.getUpstreamUrl()).path(path);
        final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        invocationBuilder.header(HttpHeaders.AUTHORIZATION, "Bearer " + contentSyncConfig.getApiKey());
        return invocationBuilder;
    }
}
