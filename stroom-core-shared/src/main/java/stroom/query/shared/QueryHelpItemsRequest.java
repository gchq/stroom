package stroom.query.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Set;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class QueryHelpItemsRequest {

    @JsonProperty
    private final String query;
    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String filterInput;
    @JsonProperty
    private final Set<HelpItemType> requestedTypes;


    @JsonCreator
    public QueryHelpItemsRequest(@JsonProperty("query") final String query,
                                 @JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                                 @JsonProperty("filterInput") final String filterInput,
                                 @JsonProperty("requestedTypes") final Set<HelpItemType> requestedTypes) {
        this.query = query;
        this.dataSourceRef = dataSourceRef;
        this.filterInput = filterInput;
        this.requestedTypes = requestedTypes;
    }

    public static QueryHelpItemsRequest fromDataSource(final DocRef dataSourceRef,
                                                       final String filterInput,
                                                       final Set<HelpItemType> requestedTypes) {
        return new QueryHelpItemsRequest(
                null, dataSourceRef, filterInput, requestedTypes);
    }

    public static QueryHelpItemsRequest fromQuery(final String query,
                                                  final String filterInput,
                                                  final Set<HelpItemType> requestedTypes) {
        return new QueryHelpItemsRequest(
                query, null, filterInput, requestedTypes);
    }

    public String getFilterInput() {
        return filterInput;
    }

    public Set<HelpItemType> getRequestedTypes() {
        return requestedTypes;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getQuery() {
        return query;
    }

    @Override
    public String toString() {
        return "QueryHelpItemsRequest{" +
                "query='" + query + '\'' +
                ", dataSourceRef=" + dataSourceRef +
                ", filterInput='" + filterInput + '\'' +
                ", requestedTypes=" + requestedTypes +
                '}';
    }


    // --------------------------------------------------------------------------------


    public enum HelpItemType {
        DATA_SOURCE,
        STRUCTURE,
        FIELD,
        FUNCTION
    }
}
