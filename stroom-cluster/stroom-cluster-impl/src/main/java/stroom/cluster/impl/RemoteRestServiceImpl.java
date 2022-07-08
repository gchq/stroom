package stroom.cluster.impl;

import stroom.cluster.api.RemoteRestService;
import stroom.cluster.api.RemoteRestUtil;
import stroom.util.jersey.UriBuilderUtil;
import stroom.util.jersey.WebTargetFactory;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.rest.RestUtil;

import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class RemoteRestServiceImpl implements RemoteRestService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(RemoteRestServiceImpl.class);

    private final EndpointUrlServiceImpl endpointUrlService;
    private final WebTargetFactory webTargetFactory;

    @Inject
    public RemoteRestServiceImpl(final EndpointUrlServiceImpl endpointUrlService,
                                 final WebTargetFactory webTargetFactory) {
        this.endpointUrlService = endpointUrlService;
        this.webTargetFactory = webTargetFactory;
    }

    @Override
    public <T_RESP> T_RESP remoteRestResult(final String nodeName,
                                            final Supplier<String> fullPathSupplier,
                                            final Supplier<T_RESP> localSupplier,
                                            final Function<Invocation.Builder, Response> responseBuilderFunc,
                                            final Function<Response, T_RESP> responseMapper,
                                            final Map<String, Object> queryParams) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        final T_RESP resp;

        // If this is the node that was contacted then just resolve it locally
        if (endpointUrlService.shouldExecuteLocally(nodeName)) {
            LOGGER.debug(() -> LogUtil.message("Executing {} locally", fullPathSupplier.get()));
            resp = localSupplier.get();

        } else {
            // A different node to make a rest call to the required node
            final String url = endpointUrlService.getRemoteEndpointUrl(nodeName) + fullPathSupplier.get();
            LOGGER.debug("Fetching value from remote node at {}", url);
            try {
                final Builder builder = createBuilder(queryParams, url);

                final Response response = responseBuilderFunc.apply(builder);

                LOGGER.debug(() -> "Response status " + response.getStatus());
                if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }
                resp = responseMapper.apply(response);

                Objects.requireNonNull(resp, "Null response calling url " + url);
            } catch (final Throwable e) {
                throw RemoteRestUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
        return resp;
    }

    @Override
    public void remoteRestCall(final String nodeName,
                               final Supplier<String> fullPathSupplier,
                               final Runnable localRunnable,
                               final Function<Builder, Response> responseBuilderFunc,
                               final Map<String, Object> queryParams) {
        RestUtil.requireNonNull(nodeName, "nodeName not supplied");

        // If this is the node that was contacted then just resolve it locally
        if (endpointUrlService.shouldExecuteLocally(nodeName)) {

            LOGGER.debug(() -> LogUtil.message("Executing {} locally", fullPathSupplier.get()));
            localRunnable.run();
        } else {
            // A different node to make a rest call to the required node
            final String url = endpointUrlService.getRemoteEndpointUrl(nodeName) + fullPathSupplier.get();
            LOGGER.debug("Calling remote node at {}", url);
            try {
                final Builder builder = createBuilder(queryParams, url);

                final Response response = responseBuilderFunc.apply(builder);

                LOGGER.debug(() -> "Response status " + response.getStatus());
                if (response.getStatus() != Status.OK.getStatusCode()) {
                    throw new WebApplicationException(response);
                }
            } catch (final Throwable e) {
                throw RemoteRestUtil.handleExceptionsOnNodeCall(nodeName, url, e);
            }
        }
    }

    private Builder createBuilder(final Map<String, Object> queryParams, final String url) {
        WebTarget webTarget = webTargetFactory
                .create(url);

        if (queryParams != null) {
            for (final Entry<String, Object> entry : queryParams.entrySet()) {
                if (entry.getKey() != null) {
                    webTarget = UriBuilderUtil.addParam(webTarget, entry.getKey(), entry.getValue());
                }
            }
        }

        return webTarget
                .request(MediaType.APPLICATION_JSON);
    }
}
