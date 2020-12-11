package stroom.rs.logging.api;

import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.WriterInterceptor;

public interface StroomServerLoggingFilter extends ContainerRequestFilter, WriterInterceptor, ExceptionMapper<Exception>
{
}
