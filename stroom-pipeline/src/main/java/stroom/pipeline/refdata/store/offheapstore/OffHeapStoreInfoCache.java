package stroom.pipeline.refdata.store.offheapstore;

import stroom.cache.api.CacheManager;
import stroom.cache.api.LoadingStroomCache;
import stroom.node.api.NodeCallUtil;
import stroom.node.api.NodeInfo;
import stroom.node.api.NodeService;
import stroom.pipeline.refdata.ReferenceDataResource;
import stroom.pipeline.refdata.store.RefDataStoreFactory;
import stroom.util.cache.CacheConfig;
import stroom.util.exception.ThrowingFunction;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResourcePaths;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.GenericType;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Singleton
public class OffHeapStoreInfoCache {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(OffHeapStoreInfoCache.class);

    private static final String CACHE_NAME = "Reference Data Store Info Cache";

    private final LoadingStroomCache<CacheKey, OffHeapStoreInfo> nodeToInfoCache;
    private final Provider<NodeInfo> nodeInfoProvider;
    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<WebTargetFactory> webTargetFactoryProvider;
    private final DelegatingRefDataOffHeapStore delegatingRefDataOffHeapStore;

    @Inject
    public OffHeapStoreInfoCache(final CacheManager cacheManager,
                                 final Provider<NodeInfo> nodeInfoProvider,
                                 final Provider<NodeService> nodeServiceProvider,
                                 final Provider<WebTargetFactory> webTargetFactoryProvider,
                                 final RefDataStoreFactory refDataStoreFactory) {
        this.nodeInfoProvider = nodeInfoProvider;
        this.nodeServiceProvider = nodeServiceProvider;
        this.webTargetFactoryProvider = webTargetFactoryProvider;
        this.delegatingRefDataOffHeapStore = refDataStoreFactory.getOffHeapStore();

        // TODO make config prop for cache
        nodeToInfoCache = cacheManager.createLoadingCache(
                CACHE_NAME,
                () -> CacheConfig.builder()
                        .refreshAfterWrite(StroomDuration.ofMinutes(1))
                        .build(),
                this::getStoreLocalStoreInfo);
    }

    public Optional<OffHeapStoreInfo> getLocalStoreInfo(final String feedName) {
        LOGGER.debug("getStoreInfo - feedName: {}", feedName);
        return nodeToInfoCache.getOptional(new CacheKey(feedName));
    }

    public List<OffHeapStoreInfo> getStoreInfo(final String nodeName) {
        LOGGER.debug("getStoreInfo - nodeName: {}", nodeName);
        final NodeInfo nodeInfo = nodeInfoProvider.get();
        if (nodeInfo.isThisNode(nodeName)) {
            return delegatingRefDataOffHeapStore.getFeedNameToStoreMap()
                    .keySet()
                    .stream()
                    .map(feedName ->
                            nodeToInfoCache.getOptional(new CacheKey(feedName))
                                    .orElse(null))
                    .filter(Objects::nonNull)
                    .toList();
        } else {
            return getStoreInfoForRemoteNode(nodeInfo, nodeName);
        }
    }

    private OffHeapStoreInfo getStoreLocalStoreInfo(final CacheKey cacheKey) {
        final RefDataOffHeapStore store = delegatingRefDataOffHeapStore.getEffectiveStore(cacheKey.feedName);
        if (store != null) {
            final Path localDir = store.getLocalDir();
            final long osFreeSpaceBytes;
            final long osBytesTotal;
            try {
                osFreeSpaceBytes = NullSafe.get(
                        localDir,
                        ThrowingFunction.unchecked(Files::getFileStore),
                        ThrowingFunction.unchecked(FileStore::getUnallocatedSpace));
                osBytesTotal = NullSafe.get(
                        localDir,
                        ThrowingFunction.unchecked(Files::getFileStore),
                        ThrowingFunction.unchecked(FileStore::getTotalSpace));
            } catch (Exception e) {
                throw new RuntimeException("Error getting free space for path " + localDir, e);
            }

            return new OffHeapStoreInfo(
                    store.getName(),
                    cacheKey.feedName,
                    nodeInfoProvider.get().getThisNodeName(),
                    store.getLocalDir(),
                    store.getSizeOnDisk(),
                    store.getSizeInUse(),
                    osFreeSpaceBytes,
                    osBytesTotal,
                    store.getKeyValueEntryCount(),
                    store.getRangeValueEntryCount(),
                    store.getValueStoreCount(),
                    store.getValueStoreCount(),
                    System.currentTimeMillis());
        } else {
            return null;
        }
    }

    private List<OffHeapStoreInfo> getStoreInfoForRemoteNode(final NodeInfo nodeInfo,
                                                             final String nodeName) {
        final String baseUrl = NodeCallUtil.getBaseEndpointUrl(
                nodeInfo,
                nodeServiceProvider.get(),
                nodeName);
        final String url = baseUrl + ResourcePaths.buildAuthenticatedApiPath(
                ReferenceDataResource.BASE_PATH,
                ReferenceDataResource.STORE_INFO_SUB_PATH,
                nodeName);
        try {
            // A different node to make a rest call to the required node
            WebTarget webTarget = webTargetFactoryProvider.get()
                    .create(url);
            final Response response = webTarget
                    .request(MediaType.APPLICATION_JSON)
                    .get();
            if (response.getStatus() == Status.NOT_FOUND.getStatusCode()) {
                throw new NotFoundException(response);
            } else if (response.getStatus() != Status.OK.getStatusCode()) {
                throw new WebApplicationException(response);
            }

            //noinspection Convert2Diamond
            final List<OffHeapStoreInfo> list = response.readEntity(new GenericType<List<OffHeapStoreInfo>>() {
            });
            LOGGER.debug(() -> LogUtil.message("getStoreInfoForRemoteNode - nodeName: {}, count: {}",
                    nodeName, list.size()));

            return list;
        } catch (final Throwable e) {
            throw NodeCallUtil.handleExceptionsOnNodeCall(nodeName, url, e);
        }
    }

    // --------------------------------------------------------------------------------


    // Make the caches key much clearer than just having a string
    private record CacheKey(String feedName) {

        @Override
        public int hashCode() {
            return feedName.hashCode();
        }
    }
}
