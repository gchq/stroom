package stroom.pathways.shared;

import stroom.docref.DocRef;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class GetTraceRequest {

    @JsonProperty
    private final DocRef dataSourceRef;
    @JsonProperty
    private final String traceId;
    @JsonProperty
    private final SimpleDuration temporalOrderingTolerance;

    @JsonCreator
    public GetTraceRequest(@JsonProperty("dataSourceRef") final DocRef dataSourceRef,
                           @JsonProperty("traceId") final String traceId,
                           @JsonProperty("temporalOrderingTolerance") final SimpleDuration temporalOrderingTolerance) {
        this.dataSourceRef = dataSourceRef;
        this.traceId = traceId;
        this.temporalOrderingTolerance = temporalOrderingTolerance;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    public String getTraceId() {
        return traceId;
    }

    public SimpleDuration getTemporalOrderingTolerance() {
        return temporalOrderingTolerance;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final GetTraceRequest that = (GetTraceRequest) o;
        return Objects.equals(dataSourceRef, that.dataSourceRef) &&
               Objects.equals(traceId, that.traceId) &&
               Objects.equals(temporalOrderingTolerance, that.temporalOrderingTolerance);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), dataSourceRef, traceId, temporalOrderingTolerance);
    }

    @Override
    public String toString() {
        return "FindTraceCriteria{" +
               "dataSourceRef=" + dataSourceRef +
               ", traceId='" + traceId + '\'' +
               ", temporalOrderingTolerance='" + temporalOrderingTolerance + '\'' +
               '}';
    }
}
