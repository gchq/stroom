package stroom.cluster.api;

import java.net.ConnectException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.WebApplicationException;

public class RemoteRestUtil {

    public static RuntimeException handleExceptions(final ClusterMember member,
                                                    final String url,
                                                    final Throwable throwable) {
        if (throwable instanceof WebApplicationException) {
            throw (WebApplicationException) throwable;
        } else if (throwable instanceof ProcessingException) {
            if (throwable.getCause() != null && throwable.getCause() instanceof ConnectException) {
                return new ClusterCallException(member, url, throwable);
            } else {
                return new RuntimeException(throwable);
            }
        } else {
            return new RuntimeException(throwable);
        }
    }
}
