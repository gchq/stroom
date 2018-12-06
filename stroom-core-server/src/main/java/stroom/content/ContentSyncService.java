package stroom.content;

import com.codahale.metrics.health.HealthCheck;
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
import stroom.util.HasHealthCheck;
import stroom.util.logging.LambdaLogger;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class ContentSyncService implements Managed , HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentSyncService.class);

    private final ContentSyncConfig contentSyncConfig;
    private final Map<String, ImportExportActionHandler> importExportActionHandlers;

    private volatile ScheduledExecutorService scheduledExecutorService;

    public ContentSyncService(final ContentSyncConfig contentSyncConfig,
                              final Map<String, ImportExportActionHandler> importExportActionHandlers) {
        this.contentSyncConfig = contentSyncConfig;
        this.importExportActionHandlers = importExportActionHandlers;
        contentSyncConfig.validateConfiguration();
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
                    LOGGER.info("Syncing content from '" + url + "'");
                    final Response response = createClient(url, "/list").get();
                    if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
                        LOGGER.error(response.getStatusInfo().getReasonPhrase());
                    } else {
                        final DocRefs docRefs = response.readEntity(DocRefs.class);
                        docRefs.getSet().forEach(docRef -> importDocument(url, docRef, importExportActionHandler));
                    }
                }
            } catch (final Exception e) {
                LOGGER.error("Error syncing content of type {}", type, e);
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

    @Override
    public HealthCheck.Result getHealth() {
        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);
        final Map<String, Object> postResults = new ConcurrentHashMap<>();
        final String path = "/list";

        // parallelStream so we can hit multiple URLs concurrently
        contentSyncConfig.getUpstreamUrl().entrySet().parallelStream()
                .filter(entry ->
                        entry.getValue() != null)
                .forEach(entry -> {
                    final String url = entry.getValue();
                    final String msg = validatePost(url, path);

                    if (!"200".equals(msg)) {
                        allHealthy.set(false);
                    }
                    Map<String, String> detailMap = new HashMap<>();
                    detailMap.put("type", entry.getKey());
                    detailMap.put("url", entry.getValue() + path);
                    detailMap.put("result", msg);
                    postResults.put(url, detailMap);
                });

        resultBuilder.withDetail("upstreamUrls", postResults);
        if (allHealthy.get()) {
            resultBuilder.healthy();
        } else {
            resultBuilder.unhealthy();
        }
        return resultBuilder.build();
    }

    private String validatePost(final String url, final String path) {
        final Response response;
        try {
            response = createClient(url, path).get();
            if (response.getStatusInfo().getStatusCode() == Status.OK.getStatusCode()) {
                return String.valueOf(Status.OK.getStatusCode());
            } else {
                LOGGER.error(response.getStatusInfo().getReasonPhrase());
                return LambdaLogger.buildMessage("Error: [{}] [{}]",
                        response.getStatusInfo().getStatusCode(),
                        response.getStatusInfo().getReasonPhrase());
            }
        } catch (Exception e) {
            return LambdaLogger.buildMessage("Error: [{}]", e.getMessage());
        }
    }
}
