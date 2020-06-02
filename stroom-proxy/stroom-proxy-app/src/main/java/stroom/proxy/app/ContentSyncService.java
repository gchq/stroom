package stroom.proxy.app;

import stroom.docref.DocRef;
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.security.api.ClientSecurityUtil;
import stroom.util.HasHealthCheck;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.base.Strings;
import io.dropwizard.lifecycle.Managed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ContentSyncService implements Managed, HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContentSyncService.class);

    private final ProxyConfig proxyConfig;
    private final ContentSyncConfig contentSyncConfig;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final Set<ImportExportActionHandler> importExportActionHandlers;
    private final Provider<Client> clientProvider;

    private volatile ScheduledExecutorService scheduledExecutorService;

    @Inject
    public ContentSyncService(final ProxyConfig proxyConfig,
                              final ContentSyncConfig contentSyncConfig,
                              final DefaultOpenIdCredentials defaultOpenIdCredentials,
                              final Set<ImportExportActionHandler> importExportActionHandlers,
                              final Provider<Client> clientProvider) {
        this.contentSyncConfig = contentSyncConfig;
        this.importExportActionHandlers = importExportActionHandlers;
        this.clientProvider = clientProvider;
        this.proxyConfig = proxyConfig;
        contentSyncConfig.validateConfiguration();
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public synchronized void start() {
        if (contentSyncConfig.isContentSyncEnabled()) {
            if (scheduledExecutorService == null) {
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                scheduledExecutorService.scheduleWithFixedDelay(this::sync, 0, contentSyncConfig.getSyncFrequency(), TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (contentSyncConfig.isContentSyncEnabled()) {
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdown();
                scheduledExecutorService = null;
            }
        }
    }

    private void sync() {
        final Map<String, ImportExportActionHandler> typeToHandlerMap = importExportActionHandlers.stream()
                .collect(Collectors.toMap(ImportExportActionHandler::getType, Function.identity()));

        if (contentSyncConfig.getUpstreamUrl() != null) {
            contentSyncConfig.getUpstreamUrl().forEach((type, url) -> {
                final ImportExportActionHandler importHandler = typeToHandlerMap.get(type);
                if (importHandler == null) {
                    String knownHandlers = importExportActionHandlers.stream()
                            .map(handler -> handler.getType() + "(" + handler.getClass().getSimpleName() + ")")
                            .collect(Collectors.joining(", "));
                    LOGGER.error("No import handler found for type {} with url {}. Known handlers {}",
                            type, url, knownHandlers);
                } else {
                    try {
                        if (url != null) {
                            LOGGER.info("Syncing content from '" + url + "'");
                            final Response response = createClient(url, "/list").get();
                            if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
                                LOGGER.error(response.getStatusInfo().getReasonPhrase());
                            } else {
                                final Set<DocRef> docRefs = response.readEntity(Set.class);
                                docRefs.forEach(docRef -> importDocument(url, docRef, importHandler));
                                LOGGER.info("Synced {} documents", docRefs.size());
                            }
                        }
                    } catch (Exception e) {
                        LOGGER.error("Error syncing content of type {}", type, e);
                    }
                }
            });
        }
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
        final Client client = clientProvider.get();
        final WebTarget webTarget = client.target(url).path(path);
        final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
        ClientSecurityUtil.addAuthorisationHeader(invocationBuilder, getApiKey());
        return invocationBuilder;
    }

    private String getApiKey() {

        // Allows us to use hard-coded open id creds / token to authenticate with stroom
        // out of the box. ONLY for use in test/demo environments.
        if (proxyConfig.isUseDefaultOpenIdCredentials() && Strings.isNullOrEmpty(contentSyncConfig.getApiKey())) {
            LOGGER.info("Using default authentication token, should only be used in test/demo environments.");
            return Objects.requireNonNull(defaultOpenIdCredentials.getApiKey());
        } else {
            return contentSyncConfig.getApiKey();
        }
    }

    @Override
    public HealthCheck.Result getHealth() {
        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);
        final Map<String, Object> postResults = new ConcurrentHashMap<>();
        final String path = "/list";

        // parallelStream so we can hit multiple URLs concurrently
        if (contentSyncConfig.isContentSyncEnabled()) {
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
        }
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
                return LogUtil.message("Error: [{}] [{}]",
                        response.getStatusInfo().getStatusCode(),
                        response.getStatusInfo().getReasonPhrase());
            }
        } catch (Exception e) {
            return LogUtil.message("Error: [{}]", e.getMessage());
        }
    }
}
