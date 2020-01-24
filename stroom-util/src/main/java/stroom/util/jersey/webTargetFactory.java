package stroom.util.jersey;

import javax.ws.rs.client.WebTarget;

public interface webTargetFactory {
    WebTarget create(String url);
}
