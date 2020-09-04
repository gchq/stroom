package stroom.pipeline.refdata;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

@JsonInclude(Include.NON_NULL)
public class RefDataLookupRequest {

    @NotNull
    @NotEmpty
    @JsonProperty
    private final String mapName;

    @NotNull
    @NotEmpty
    @JsonProperty
    private final String key;

    @JsonProperty
    private final long effectiveTimeEpochMs;

    @NotNull
    @NotEmpty
    @JsonProperty
    private final List<ReferenceLoader> referenceLoaders;

    @JsonCreator
    public RefDataLookupRequest(@JsonProperty("mapName") final String mapName,
                                @JsonProperty("key") final String key,
                                @JsonProperty("effectiveTimeEpochMs") final Long effectiveTimeEpochMs,
                                @JsonProperty("pipelines") final List<ReferenceLoader> referenceLoaders) {
        this.mapName = mapName;
        this.key = key;
        this.effectiveTimeEpochMs = effectiveTimeEpochMs != null
                ? effectiveTimeEpochMs
                : Instant.now().toEpochMilli();
        this.referenceLoaders = referenceLoaders;
    }

    public String getMapName() {
        return mapName;
    }

    public String getKey() {
        return key;
    }

    public long getEffectiveTimeEpochMs() {
        return effectiveTimeEpochMs;
    }

    public List<ReferenceLoader> getReferenceLoaders() {
        return referenceLoaders;
    }

    @Override
    public String toString() {
        return "RefDataLookupRequest{" +
                "mapName='" + mapName + '\'' +
                ", key='" + key + '\'' +
                ", effectiveTimeEpochMs=" + Instant.ofEpochMilli(effectiveTimeEpochMs) +
                ", referenceLoaders=" + referenceLoaders +
                '}';
    }

    @JsonInclude(Include.NON_NULL)
    public static class ReferenceLoader {

        @NotNull
        @JsonProperty
        private final DocRef loaderPipeline;

        @NotNull
        @NotEmpty
        @JsonProperty
        private final String feedName;

        // TODO @AT Should we have strm type in here, defaulting to REFERENCE?

        @JsonCreator
        public ReferenceLoader(@JsonProperty("loaderPipeline") final DocRef loaderPipeline,
                               @JsonProperty("feedName") final String feedName) {
            this.loaderPipeline = loaderPipeline;
            this.feedName = feedName;
        }

        public DocRef getLoaderPipeline() {
            return loaderPipeline;
        }

        public String getFeedName() {
            return feedName;
        }

        @Override
        public String toString() {
            return "ReferenceLoader{" +
                    "loaderPipeline=" + loaderPipeline +
                    ", feedName='" + feedName + '\'' +
                    '}';
        }
    }
}
