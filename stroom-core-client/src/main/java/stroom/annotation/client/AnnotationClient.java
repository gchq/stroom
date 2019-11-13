package stroom.annotation.client;

import org.fusesource.restygwt.client.MethodCallback;
import org.fusesource.restygwt.client.RestService;
import stroom.annotation.shared.Annotation;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;

@Path("/annotation")
public interface AnnotationClient extends RestService {
//    @GET
//    public void getThings(MethodCallback<List<Thing>> callback);

    @GET
    @Path("/{id}")
    public void get(@PathParam("id") String id, MethodCallback<Annotation> callback);
}