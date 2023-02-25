package stroom.query.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.node.api.NodeService;
import stroom.query.api.v2.FindResultStoreCriteria;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultStoreInfo;
import stroom.query.common.v2.ResultStoreManager;
import stroom.query.shared.DestroyStoreRequest;
import stroom.query.shared.ResultStoreResource;
import stroom.query.shared.ResultStoreResponse;
import stroom.query.shared.UpdateStoreRequest;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.ws.rs.client.Entity;

@AutoLogged(OperationType.UNLOGGED)
public class ResultStoreResourceImpl implements ResultStoreResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(ResultStoreResourceImpl.class);

    private final Provider<NodeService> nodeServiceProvider;
    private final Provider<ResultStoreManager> resultStoreManagerProvider;

    @Inject
    public ResultStoreResourceImpl(final Provider<ResultStoreManager> resultStoreManagerProvider,
                                   final Provider<NodeService> nodeServiceProvider) {
        this.resultStoreManagerProvider = resultStoreManagerProvider;
        this.nodeServiceProvider = nodeServiceProvider;
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public ResultStoreResponse list(final String nodeName) {
        return find(nodeName, new FindResultStoreCriteria());
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public ResultStoreResponse find(final String nodeName, final FindResultStoreCriteria criteria) {
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            ResultStoreResponse.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.FIND_PATH_PART,
                                    nodeName),
                            () -> {
                                final ResultPage<ResultStoreInfo> resultPage = resultStoreManagerProvider.get()
                                        .find(criteria);
                                return new ResultStoreResponse(
                                        resultPage.getValues(),
                                        Collections.emptyList(),
                                        resultPage.getPageResponse());
                            },
                            builder ->
                                    builder.post(Entity.json(criteria)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return new ResultStoreResponse(Collections.emptyList(), List.of(e.getMessage()), null);
        }
    }

    @Override
    public Boolean update(final String nodeName, final UpdateStoreRequest updateStoreRequest) {
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.UPDATE_PATH_PART,
                                    nodeName),
                            () -> {
                                resultStoreManagerProvider.get().update(updateStoreRequest.getQueryKey(),
                                        updateStoreRequest.getSearchProcessLifespan(),
                                        updateStoreRequest.getStoreLifespan());
                                return true;
                            },
                            builder ->
                                    builder.post(Entity.json(updateStoreRequest)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public Boolean exists(final String nodeName, final QueryKey queryKey) {
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.TERMINATE_PATH_PART,
                                    nodeName),
                            () -> resultStoreManagerProvider.get()
                                    .terminate(queryKey),
                            builder ->
                                    builder.post(Entity.json(queryKey)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }

    //    @AutoLogged(value = OperationType.PROCESS, verb = "Terminating",
//            decorator = TerminateDecorator.class)
    @Override
    public Boolean terminate(final String nodeName, final QueryKey queryKey) {
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.TERMINATE_PATH_PART,
                                    nodeName),
                            () -> resultStoreManagerProvider.get()
                                    .terminate(queryKey),
                            builder ->
                                    builder.post(Entity.json(queryKey)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }

    @Override
    public Boolean destroy(final String nodeName, final DestroyStoreRequest request) {
        try {
            return nodeServiceProvider.get()
                    .remoteRestResult(
                            nodeName,
                            Boolean.class,
                            () -> ResourcePaths.buildAuthenticatedApiPath(
                                    ResultStoreResource.BASE_PATH,
                                    ResultStoreResource.DESTROY_PATH_PART,
                                    nodeName),
                            () -> resultStoreManagerProvider.get()
                                    .destroy(request.getQueryKey(), request.getDestroyReason()),
                            builder ->
                                    builder.post(Entity.json(request)));
        } catch (final RuntimeException e) {
            LOGGER.debug(e.getMessage(), e);
            return false;
        }
    }
}
