/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.proxy.app;

import stroom.docref.DocRef;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.security.api.UserIdentityFactory;
import stroom.util.HasHealthCheck;
import stroom.util.authentication.DefaultOpenIdCredentials;
import stroom.util.jersey.JerseyClientFactory;
import stroom.util.jersey.JerseyClientName;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.codahale.metrics.health.HealthCheck;
import io.dropwizard.lifecycle.Managed;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.client.Invocation;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * No longer used. Leaving it here in case we need to implement something similar
 */
@Deprecated // No longer used. Leaving it here in case we need to implement something similar
@Singleton
public class ContentSyncService implements Managed, HasHealthCheck {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ContentSyncService.class);

    private final Provider<ProxyConfig> proxyConfigProvider;
    private final Provider<ContentSyncConfig> contentSyncConfigProvider;
    private final DefaultOpenIdCredentials defaultOpenIdCredentials;
    private final Set<ImportExportActionHandler> importExportActionHandlers;
    private final JerseyClientFactory jerseyClientFactory;
    private final UserIdentityFactory userIdentityFactory;
    private final Map<String, ImportExportActionHandler> typeToHandlerMap;

    private volatile ScheduledExecutorService scheduledExecutorService;

    //    @Inject
    public ContentSyncService(final Provider<ProxyConfig> proxyConfigProvider,
                              final Provider<ContentSyncConfig> contentSyncConfigProvider,
                              final DefaultOpenIdCredentials defaultOpenIdCredentials,
                              final Set<ImportExportActionHandler> importExportActionHandlers,
                              final JerseyClientFactory jerseyClientFactory,
                              final UserIdentityFactory userIdentityFactory) {
        this.contentSyncConfigProvider = contentSyncConfigProvider;
        this.importExportActionHandlers = importExportActionHandlers;
        this.jerseyClientFactory = jerseyClientFactory;
        this.proxyConfigProvider = proxyConfigProvider;
        this.userIdentityFactory = userIdentityFactory;
        contentSyncConfigProvider.get().validateConfiguration();
        this.defaultOpenIdCredentials = defaultOpenIdCredentials;
        this.typeToHandlerMap = importExportActionHandlers.stream()
                .collect(Collectors.toMap(ImportExportActionHandler::getType, Function.identity()));
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

    public void importDoc(final DocRef docRef) {
//        Objects.requireNonNull(docRef);
//        final String docType = docRef.getType();
//        final ImportExportActionHandler importHandler = typeToHandlerMap.get(docType);
//        if (importHandler == null) {
//            String knownHandlers = importExportActionHandlers.stream()
//                    .map(handler -> handler.getType() + "(" + handler.getClass().getSimpleName() + ")")
//                    .collect(Collectors.joining(", "));
//            LOGGER.error("No import handler found for type {}. Known handlers {}", docType, knownHandlers);
//        } else {
//            final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
//            final String url = NullSafe.map(contentSyncConfig.getReceiveDataRulesUrl())
//                    .get(docType);
//            Objects.requireNonNull(importHandler, () -> "No configured url for type " + docType);
//
//            importDocument(url, docRef, importHandler, contentSyncConfig);
//        }
    }

    private void sync() {
//        final Map<String, ImportExportActionHandler> typeToHandlerMap = importExportActionHandlers.stream()
//                .collect(Collectors.toMap(ImportExportActionHandler::getType, Function.identity()));
//
//        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
//
//        if (NullSafe.hasEntries(contentSyncConfig, ContentSyncConfig::getUpstreamUrl)) {
//            contentSyncConfig.getUpstreamUrl().forEach((type, url) -> {
//                final ImportExportActionHandler importHandler = typeToHandlerMap.get(type);
//                if (importHandler == null) {
//                    String knownHandlers = importExportActionHandlers.stream()
//                            .map(handler -> handler.getType() + "(" + handler.getClass().getSimpleName() + ")")
//                            .collect(Collectors.joining(", "));
//                    LOGGER.error("No import handler found for type {} with url {}. Known handlers {}",
//                            type, url, knownHandlers);
//                } else {
//                    try {
//                        if (url != null) {
//                            LOGGER.info("Syncing content from '" + url + "'");
//                            try (Response response = createClient(url, "/list", contentSyncConfig).get()) {
//                                if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
//                                    LOGGER.error(response.getStatusInfo().getReasonPhrase());
//                                } else {
//                                    final Set<DocRef> docRefs = response.readEntity(new GenericType<Set<DocRef>>() {
//                                    });
//                                    docRefs.forEach(docRef ->
//                                            importDocument(url, docRef, importHandler, contentSyncConfig));
//                                    LOGGER.info("Synced {} documents", docRefs.size());
//                                }
//                            }
//                        }
//                    } catch (Exception e) {
//                        LOGGER.error("Error syncing content of type {}", type, e);
//                    }
//                }
//            });
//        }
    }

    private void importDocument(final String url,
                                final DocRef docRef,
                                final ImportExportActionHandler importExportActionHandler,
                                final ContentSyncConfig contentSyncConfig) {
//        LOGGER.info("Fetching " + docRef.getType() + " " + docRef.getName() + " " + docRef.getUuid());
//        try (Response response = createClient(url, "/export", contentSyncConfig).post(Entity.json(docRef))) {
//            if (response.getStatusInfo().getStatusCode() != Status.OK.getStatusCode()) {
//                LOGGER.error(response.getStatusInfo().getReasonPhrase());
//            } else {
//                final DocumentData documentData = response.readEntity(DocumentData.class);
//                final ImportState importState = new ImportState(
//                        documentData.getDocRef(),
//                        documentData.getDocRef().getName());
//                importExportActionHandler.importDocument(
//                        documentData.getDocRef(),
//                        documentData.getDataMap(),
//                        importState,
//                        ImportSettings.auto());
//            }
//        }
    }

    private Invocation.Builder createClient(final String url,
                                            final String path,
                                            final ContentSyncConfig contentSyncConfig) {
        return jerseyClientFactory.createWebTarget(JerseyClientName.DOWNSTREAM, url)
                .path(path)
                .request(MediaType.APPLICATION_JSON)
                .headers(getHeaders(contentSyncConfig));
//        final Invocation.Builder invocationBuilder = webTarget.request(MediaType.APPLICATION_JSON);
//        ClientSecurityUtil.addAuthorisationHeader(invocationBuilder, getApiKey());
//
//        return invocationBuilder;
    }

    private MultivaluedMap<String, Object> getHeaders(final ContentSyncConfig contentSyncConfig) {
        final Map<String, String> headers;

        if (!NullSafe.isBlankString(contentSyncConfig.getApiKey())) {
            // Intended for when stroom is using its internal IDP. Create the API Key in stroom UI
            // and add it to config.
//            LOGGER.debug(() -> LogUtil.message("Using API key from config prop {}",
//                    contentSyncConfig.getFullPathStr(FeedStatusConfig.PROP_NAME_API_KEY)));

            headers = userIdentityFactory.getAuthHeaders(contentSyncConfig.getApiKey());
        } else {
            // Use a token from the external IDP
            headers = userIdentityFactory.getServiceUserAuthHeaders();
        }
        return new MultivaluedHashMap<>(headers);
    }

    @Override
    public HealthCheck.Result getHealth() {
//        HealthCheck.ResultBuilder resultBuilder = HealthCheck.Result.builder();
//
//        final AtomicBoolean allHealthy = new AtomicBoolean(true);
//        final Map<String, Object> postResults = new ConcurrentHashMap<>();
//        final String path = "/list";
//        final ContentSyncConfig contentSyncConfig = contentSyncConfigProvider.get();
//
//        // parallelStream so we can hit multiple URLs concurrently
//        if (contentSyncConfig.isContentSyncEnabled()
//            && NullSafe.hasEntries(contentSyncConfig.getUpstreamUrl())) {
//
//            contentSyncConfig.getUpstreamUrl()
//                    .entrySet()
//                    .parallelStream()
//                    .filter(entry ->
//                            entry.getValue() != null)
//                    .forEach(entry -> {
//                        final String url = entry.getValue();
//                        final String msg = validatePost(url, path, contentSyncConfig);
//
//                        if (!"200".equals(msg)) {
//                            allHealthy.set(false);
//                        }
//                        Map<String, String> detailMap = new HashMap<>();
//                        detailMap.put("type", entry.getKey());
//                        detailMap.put("url", entry.getValue() + path);
//                        detailMap.put("result", msg);
//                        postResults.put(url, detailMap);
//                    });
//        }
//        resultBuilder.withDetail("upstreamUrls", postResults);
//        if (allHealthy.get()) {
//            resultBuilder.healthy();
//        } else {
//            resultBuilder.unhealthy();
//        }
//        return resultBuilder.build();
        return null;
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
        } catch (final Exception e) {
            return LogUtil.message("Error: [{}]", e.getMessage());
        }
    }
}
