package stroom.cluster.api;

import java.net.ConnectException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

public class RemoteRestUtil {
    public static RuntimeException handleExceptionsOnNodeCall(final String nodeName,
                                                       final String url,
                                                       final Throwable throwable) {
        if (throwable instanceof WebApplicationException) {
            throw (WebApplicationException) throwable;
        } else if (throwable instanceof ProcessingException) {
            if (throwable.getCause() != null && throwable.getCause() instanceof ConnectException) {
                return new NodeCallException(nodeName, url, throwable);
            } else {
                return new RuntimeException(throwable);
            }
        } else {
            return new RuntimeException(throwable);
        }
    }
}
