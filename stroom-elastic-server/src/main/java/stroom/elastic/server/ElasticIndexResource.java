package stroom.elastic.server;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.swagger.annotations.Api;
import stroom.elastic.shared.ElasticIndex;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

@Api(
        value = "elastic-index - /v1",
        description = "Elastic Index Document API")
@Path("/elastic-index/v1")
@Produces(MediaType.APPLICATION_JSON)
@Component
public class ElasticIndexResource implements HasHealthCheck {
    private static final Logger LOGGER = LoggerFactory.getLogger(ElasticIndexResource.class);

    private final ElasticIndexService service;

    @Inject
    public ElasticIndexResource(final ElasticIndexService service) {
        this.service = service;
    }

    @GET
    @Path("/{uuid}")
    public Response get(@PathParam("uuid") final String uuid) {

        LOGGER.debug(String.format("Getting an Index with UUID: %s", uuid));

        final ElasticIndex elasticIndex = this.service.loadByUuid(uuid);

        return Response.ok(elasticIndex).build();
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}
