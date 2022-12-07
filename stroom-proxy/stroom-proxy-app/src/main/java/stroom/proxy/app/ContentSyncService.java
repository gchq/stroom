package stroom.proxy.app;

import stroom.docref.DocRef;
import stroom.importexport.api.DocumentData;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.importexport.shared.ImportState;
import stroom.importexport.shared.ImportState.ImportMode;
import stroom.proxy.app.handler.FeedStatusConfig;
import stroom.security.common.impl.UserIdentityFactory;
import stroom.util.HasHealthCheck;
import stroom.util.NullSafe;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.lifecycle.Managed;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

@Singleton
public class ContentSyncService implements Managed, HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentSyncService.class);

    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<ContentSyncConfig> contentSyncConfigProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final Set<ImportExportActionHandler> importExportActionHandlers;
    private final Provider<Client> jerseyClientProvider;
    private final UserIdentityFactory userIdentityFactory;

    private volatile ScheduledExecutorService scheduledExecutorService;

    @Inject
    public ContentSyncService(final Provider<ProxyConfig> proxyConfigProvider,
                              final Provider<ContentSyncConfig> contentSyncConfigProvider,
                              final DefaultOpenIdCredentials defaultOpenIdCredentials,
                              final Set<ImportExportActionHandler> importExportActionHandlers,
                              final Provider<Client> jerseyClientProvider,
                              final UserIdentityFactory userIdentityFactory) {
        this.contentSyncConfigProvider = contentSyncConfigProvider;
        this.importExportActionHandlers = importExportActionHandlers;
        this.jerseyClientProvider = jerseyClientProvider;
        this.proxyConfigProvider = proxyConfigProvider;
        this.userIdentityFactory = userIdentityFactory;
        contentSyncConfigProvider.get().validateConfiguration();
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
    }

    @Override
    public synchronized void start() {
        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
        if (contentSyncConfig.isContentSyncEnabled()) {
            if (scheduledExecutorService == null) {
                scheduledExecutorService = Executors.newSingleThreadScheduledExecutor();
                scheduledExecutorService.scheduleWithFixedDelay(
                        this::sync,
                        0,
                        contentSyncConfig.getSyncFrequency().toMillis(),
                        TimeUnit.MILLISECONDS);
            }
        }
    }

    @Override
    public synchronized void stop() {
        if (contentSyncConfigProvider.get().isContentSyncEnabled()) {
            if (scheduledExecutorService != null) {
                scheduledExecutorService.shutdown();
                scheduledExecutorService = null;
            }
        }
    }

    private void sync() {
        final Map<String, ImportExportActionHandler> typeToHandlerMap = importExportActionHandlers.stream()
                .collect(Collectors.toMap(ImportExportActionHandler::getType, Function.identity()));

        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();

        if (NullSafe.hasEntries(contentSyncConfig, ContentSyncConfig::getUpstreamUrl)) {
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
                            final Response response = createClient(url, "/list", contentSyncConfig).get();
                            if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
                                LOGGER.error(response.getStatusInfo().getReasonPhrase());
                            } else {
                                final Set<DocRef> docRefs = response.readEntity(new GenericType<Set<DocRef>>() {
                                });
                                docRefs.forEach(docRef ->
                                        importDocument(url, docRef, importHandler, contentSyncConfig));
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

    private void importDocument(final String url,
                                final DocRef docRef,
                                final ImportExportActionHandler importExportActionHandler,
                                final ContentSyncConfig contentSyncConfig) {
        LOGGER.info("Fetching " + docRef.getType() + " " + docRef.getName() + " " + docRef.getUuid());
        final Response response = createClient(url, "/export", contentSyncConfig).post(Entity.json(docRef));
        if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
            LOGGER.error(response.getStatusInfo().getReasonPhrase());
        } else {
            final DocumentData documentData = response.readEntity(DocumentData.class);
            final ImportState importState = new ImportState(
                    documentData.getDocRef(),
                    documentData.getDocRef().getName());
            importExportActionHandler.importDocument(
                    documentData.getDocRef(),
                    documentData.getDataMap(),
                    importState,
                    ImportMode.IGNORE_CONFIRMATION);
        }
    }

    private Invocation.Builder createClient(final String url,
                                            final String path,
                                            final ContentSyncConfig contentSyncConfig) {
        return jerseyClientProvider.get()
                .target(url)
                .path(path)
                .request(MediaType.APPLICATION_JSON)
                .headers(getHeaders(contentSyncConfig));
    }

    private MultivaluedMap<String, Object> getHeaders(final ContentSyncConfig contentSyncConfig) {
        final Map<String, String> headers;

        if (!NullSafe.isBlankString(contentSyncConfig.getApiKey())) {
            // Intended for when stroom is using its internal IDP. Create the API Key in stroom UI
            // and add it to config.
            LOGGER.debug(() -> LogUtil.message("Using API key from config prop {}",
                    contentSyncConfig.getFullPathStr(FeedStatusConfig.PROP_NAME_API_KEY)));

            headers = userIdentityFactory.getAuthHeaders(contentSyncConfig.getApiKey());
        } else {
            // Use a token from the external IDP
            headers = userIdentityFactory.getAuthHeaders(userIdentityFactory.getServiceUserIdentity());
        }
        return new MultivaluedHashMap<>(headers);
    }

    @Override
    public HealthCheck.Result getHealth() {
        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();

        final AtomicBoolean allHealthy = new AtomicBoolean(true);
        final Map<String, Object> postResults = new ConcurrentHashMap<>();
        final String path = "/list";
        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();

        // parallelStream so we can hit multiple URLs concurrently
        if (contentSyncConfig.isContentSyncEnabled()
                && NullSafe.hasEntries(contentSyncConfig.getUpstreamUrl())) {

            contentSyncConfig.getUpstreamUrl()
                    .entrySet()
                    .parallelStream()
                    .filter(entry ->
                            entry.getValue() != null)
                    .forEach(entry -> {
                        final String url = entry.getValue();
                        final String msg = validatePost(url, path, contentSyncConfig);

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

    private String validatePost(final String url,
                                final String path,
                                final ContentSyncConfig contentSyncConfig) {
        final Response response;
        try {
            response = createClient(url, path, contentSyncConfig).get();
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
