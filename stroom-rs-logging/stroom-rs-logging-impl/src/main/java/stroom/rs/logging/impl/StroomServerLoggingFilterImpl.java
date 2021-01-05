package stroom.rs.logging.impl;

import stroom.rs.logging.api.StroomServerLoggingFilter;
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
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.container.ContainerRequestContext;
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
    private final Provider<ResourcePathMap> resourcePathMapProvider;

    @Context
    private HttpServletRequest request;



    @Inject
    StroomServerLoggingFilterImpl(RequestEventLog requestEventLog,
                                  Provider<ResourcePathMap> resourcePathMapProvider
    ) {
        this.requestEventLog = requestEventLog;
        this.resourcePathMapProvider = resourcePathMapProvider;
        this.objectMapper = createObjectMapper();
    }

    @Override
    public Response toResponse(final Exception exception) {
        //todo create separate providers for
        //todo TokenException
        //todo AuthenticationException

        if (request != null) {
            final Object entity = request.getAttribute(REQUEST_LOG_INFO_PROPERTY);
            if (entity != null) {
                RequestInfo requestInfo = (RequestInfo) entity;
                requestEventLog.log(requestInfo.getLoggingInfo(), requestInfo.getRequestEntity(),
                        requestInfo.getRequestEntity(), exception);
            } else {
                LOGGER.warn("Unable to create audit log for exception, request info is null", exception);
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
        } else {
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

        final Object entity = request.getAttribute(REQUEST_LOG_INFO_PROPERTY);
//        final Object entity = writerInterceptorContext.getProperty(REQUEST_LOG_INFO_PROPERTY);

        if (entity != null) {
            RequestInfo requestInfo = (RequestInfo) entity;
            requestEventLog.log (requestInfo.getLoggingInfo(), requestInfo.getRequestEntity(),
                    writerInterceptorContext.getEntity());
        }
    }

    @Override
    public void filter(final ContainerRequestContext context) throws IOException {
        LoggingInfo loggingInfo = resourcePathMapProvider.get().lookup(context);

        if (context.hasEntity()) {
            final LoggingInputStream stream = new LoggingInputStream(loggingInfo, context.getEntityStream(),
                    objectMapper, MessageUtils.getCharset(context.getMediaType()));
            context.setEntityStream(stream);

//            context.setProperty(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(stream.getLoggingInfo(), stream.getRequestEntity()));
            request.setAttribute(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(stream.getLoggingInfo(), stream.getRequestEntity()));
        } else {
            request.setAttribute(REQUEST_LOG_INFO_PROPERTY, new RequestInfo(request.getRequestURI(), loggingInfo));
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