package stroom.core.snippet;

import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;

import javax.ws.rs.GET;
import java.util.List;

public interface SnippetResource {

    @GET
    @ApiOperation(
            value = "Gets snippets for a docType",
            response = Snippet.class)
    List<Snippet> getSnippets(@ApiParam("docType") String docType);
}
