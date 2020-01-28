package stroom.util.jersey;

import javax.ws.rs.client.WebTarget;

public interface WebTargetFactory {
    WebTarget create(String url);
}
