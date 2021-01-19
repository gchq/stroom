package stroom.rs.logging.impl;

import stroom.rs.logging.api.StroomServerLoggingFilter;
import stroom.security.api.TokenException;
import stroom.util.shared.PermissionException;


import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.gwt.thirdparty.json.JSONException;
import com.google.gwt.thirdparty.json.JSONObject;
import org.glassfish.jersey.message.MessageUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.security.sasl.AuthenticationException;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ResourceInfo;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;

public class StroomServerLoggingFilterImpl implements StroomServerLoggingFilter {
    static final Logger LOGGER = LoggerFactory.getLogger(StroomServerLoggingFilterImpl.class);

    private static final String REQUEST_LOG_INFO_PROPERTY = "stroom.rs.logging.request";

    private final RequestEventLog requestEventLog;
    private final ObjectMapper objectMapper;
    private final RequestLoggingConfig config;

    @Context
    private HttpServletRequest request;

    @Context
    private ResourceInfo resourceInfo;


    @Inject
    StroomServerLoggingFilterImpl(RequestEventLog requestEventLog, RequestLoggingConfig config) {
        this.requestEventLog = requestEventLog;
        this.config = config;
        this.objectMapper = createObjectMapper();
    }

    StroomServerLoggingFilterImpl(RequestEventLog requestEventLog, RequestLoggingConfig config,
                                  ResourceInfo resourceInfo,
                                  HttpServletRequest request) {
        this.requestEventLog = requestEventLog;
        this.config = config;
        this.resourceInfo = resourceInfo;
        this.request = request;
        this.objectMapper = createObjectMapper();
    }

    @Override
    public Response toResponse(final Exception exception) {
        if (request != null) {
            final Object object = request.getAttribute(REQUEST_LOG_INFO_PROPERTY);
            if (object != null) {
                RequestInfo requestInfo = (RequestInfo) object;
                requestEventLog.log(requestInfo, null, exception);
            }
        } else {
            LOGGER.warn("Unable to create audit log for exception, request is null", exception);
        }

        //Could register these Exception types separately, but this seems easier to maintain at present
        if (exception instanceof WebApplicationException){
            WebApplicationException wae = (WebApplicationException) exception;
            return wae.getResponse();
        } else if (exception instanceof PermissionException) {
            return createExceptionResponse(Status.FORBIDDEN, exception);
        } else if (exception instanceof TokenException) {
            return createExceptionResponse(Status.FORBIDDEN, exception);
        } else if (exception instanceof AuthenticationException) {
            return createExceptionResponse(Status.FORBIDDEN, exception);
        } else if (exception instanceof javax.naming.AuthenticationException) {
            return createExceptionResponse(Status.FORBIDDEN, exception);
        }else {
            return createExceptionResponse(Status.INTERNAL_SERVER_ERROR, exception);
        }
    }

    private Response createExceptionResponse (Response.Status status, Exception ex) {
        try {
            String json = createExceptionJSON(status, ex);
            return Response.status(status).
                    entity(json).
                    type("application/json").
                    build();
        } catch (Exception internal) {
            LOGGER.error("Unable to create response for exception " + ex.getMessage(), internal);
            return Response.status(Status.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static String createExceptionJSON(Response.Status status, Exception ex) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("code", status.ordinal());
        json.put("message", ex.getMessage());
        json.put("details", status.getReasonPhrase() + " " + ex.getClass() + ex.getMessage()
                + ((ex.getCause() != null ) ? " cause: " + ex.getCause().getMessage() : ""));
        return json.toString();
    }

    @Override
    public void aroundWriteTo(final WriterInterceptorContext writerInterceptorContext)
            throws IOException, WebApplicationException {
        writerInterceptorContext.proceed();

        final Object object = request.getAttribute(REQUEST_LOG_INFO_PROPERTY);

        if (object != null) {
            RequestInfo requestInfo = (RequestInfo) object;
            requestEventLog.log (requestInfo, writerInterceptorContext.getEntity());
        }
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        ContainerResourceInfo containerResourceInfo = new ContainerResourceInfo(resourceInfo, context);

        if (containerResourceInfo.shouldLog(config.isGlobalLoggingEnabled())){
            if (context.hasEntity()) {
                final LoggingInputStream stream = new LoggingInputStream(resourceInfo, context.getEntityStream(),
                        objectMapper, MessageUtils.getCharset(context.getMediaType()));
                context.setEntityStream(stream);

                request.setAttribute(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(containerResourceInfo, stream.getRequestEntity()));
            } else {
                request.setAttribute(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(containerResourceInfo, context));
            }
        }
    }

    private static ObjectMapper createObjectMapper() {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, false);
        mapper.setSerializationInclusion(Include.NON_NULL);

        return mapper;
    }

}