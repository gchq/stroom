package stroom.proxy.app.security;

import stroom.security.shared.ApiKeyCheckResource;
import stroom.util.shared.ResourcePaths;
import stroom.util.shared.RestResource;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Tag(name = "API Key")
@Path(ProxyApiKeyResource.BASE_PATH)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public interface ProxyApiKeyResource extends ApiKeyCheckResource, RestResource {

    String BASE_PATH = "/apikey" + ResourcePaths.V2;
}
