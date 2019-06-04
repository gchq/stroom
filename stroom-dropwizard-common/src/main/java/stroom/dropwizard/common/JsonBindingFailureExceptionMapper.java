package stroom.dropwizard.common;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;
import javax.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Provide more information about
 */
@Provider
public class JsonBindingFailureExceptionMapper implements ExceptionMapper<JsonProcessingException> {

    @Override

    public Response toResponse(JsonProcessingException exception) {
        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("code", 400);

        if(exception.getOriginalMessage() != null) {
            entity.put("message", exception.getOriginalMessage());
        }

        if (exception.getLocation() != null) {
            entity.put("location", String.format("line %d, column %d",
                    exception.getLocation().getLineNr(),
                    exception.getLocation().getColumnNr()));
        }

        return Response.status(Status.BAD_REQUEST)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(entity)
                .build();
    }
}

