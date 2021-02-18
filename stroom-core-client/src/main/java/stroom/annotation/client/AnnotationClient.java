package stroom.annotation.client;

import stroom.annotation.shared.Annotation;

import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.RestService;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/annotation")
public interface AnnotationClient extends RestService {

    @GET
    @Path("/{id}")
    void get(@PathParam("id") String id, MethodCallback<Annotation> callback);
}
