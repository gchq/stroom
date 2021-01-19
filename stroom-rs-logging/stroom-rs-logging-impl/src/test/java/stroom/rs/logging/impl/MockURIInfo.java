package stroom.rs.logging.impl;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;

public class MockURIInfo implements UriInfo {
    @Override
    public String getPath() {
        return null;
    }

    @Override
    public String getPath(final boolean decode) {
        return null;
    }

    @Override
    public List<PathSegment> getPathSegments() {
        return null;
    }

    @Override
    public List<PathSegment> getPathSegments(final boolean decode) {
        return null;
    }

    @Override
    public URI getRequestUri() {
        return null;
    }

    @Override
    public UriBuilder getRequestUriBuilder() {
        return null;
    }

    @Override
    public URI getAbsolutePath() {
        return null;
    }

    @Override
    public UriBuilder getAbsolutePathBuilder() {
        return null;
    }

    @Override
    public URI getBaseUri() {
        return null;
    }

    @Override
    public UriBuilder getBaseUriBuilder() {
        return null;
    }


    private Integer id;

    public void setId (Integer id){
        this.id = id;
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters() {
        MultivaluedMap<String, String> pathParamterMap = new MultivaluedHashMap<>();

        if (id != null){
            pathParamterMap.putSingle("id", id.toString());
        }

        return pathParamterMap;
    }

    @Override
    public MultivaluedMap<String, String> getPathParameters(final boolean decode) {
        return getPathParameters();
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters() {
        return null;
    }

    @Override
    public MultivaluedMap<String, String> getQueryParameters(final boolean decode) {
        return null;
    }

    @Override
    public List<String> getMatchedURIs() {
        return null;
    }

    @Override
    public List<String> getMatchedURIs(final boolean decode) {
        return null;
    }

    @Override
    public List<Object> getMatchedResources() {
        return null;
    }

    @Override
    public URI resolve(final URI uri) {
        return null;
    }

    @Override
    public URI relativize(final URI uri) {
        return null;
    }
}
