package stroom.util.jersey;

import javax.ws.rs.client.WebTarget;

/**
 * A factory for creating {@link WebTarget} instances that add the user token to the Authorization
 * header. So only use this for inter-node communication or communication between proxy and stroom.
 */
public interface WebTargetFactory {

    WebTarget create(String url);
}
