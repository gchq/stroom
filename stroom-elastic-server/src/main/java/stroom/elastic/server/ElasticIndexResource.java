package stroom.elastic.server;

import com.codahale.metrics.health.HealthCheck;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import io.swagger.annotations.Api;
import stroom.util.HasHealthCheck;

import javax.inject.Inject;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.UUID;

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

    @POST
    @Path("/")
    public Response createIndex(){
        LOGGER.info("Creating a Random Index");

        final ElasticIndex elasticIndex = this.service.create("Some Name " + UUID.randomUUID().toString());
        elasticIndex.setIndexName("shakespeare");
        elasticIndex.setIndexedType("line");
        elasticIndex.setUuid(UUID.randomUUID().toString());
        this.service.save(elasticIndex);

        return Response.ok(elasticIndex).build();
    }

    @GET
    @Path("/{id}")
    public Response get(@PathParam("id") final long id) {

        LOGGER.info(String.format("Getting an Index with Id: %d", id));

        final ElasticIndex elasticIndex = this.service.loadById(id);

        return Response.ok(elasticIndex).build();
    }

    @Override
    public HealthCheck.Result getHealth() {
        return HealthCheck.Result.healthy();
    }
}
