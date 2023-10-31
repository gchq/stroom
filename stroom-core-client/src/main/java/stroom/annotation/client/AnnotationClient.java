package stroom.annotation.client;

import stroom.annotation.shared.Annotation;

import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.RestService;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;

@Path("/annotation")
public interface AnnotationClient extends RestService {

    @GET
    @Path("/{id}")
    void get(@PathParam("id") String id, MethodCallback<Annotation> callback);
}
