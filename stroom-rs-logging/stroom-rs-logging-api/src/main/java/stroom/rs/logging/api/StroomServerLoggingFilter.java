package stroom.rs.logging.api;

import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.ext.WriterInterceptor;

public interface StroomServerLoggingFilter extends ContainerRequestFilter, ContainerResponseFilter, WriterInterceptor {
}
