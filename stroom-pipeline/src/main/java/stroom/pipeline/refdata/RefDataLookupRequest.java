package stroom.pipeline.refdata;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.validation.ValidationMethod;
import org.hibernate.validator.constraints.NotEmpty;

import javax.validation.Valid;
import javax.validation.constraints.Min;
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

    @Min(0)
    @JsonProperty
    private final long effectiveTimeEpochMs;

    @Valid
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

        @Valid
        @NotNull
        @JsonProperty
        private final DocRef loaderPipeline;

        @Valid
        @NotNull
        @JsonProperty
        private final DocRef referenceFeed;

        // TODO @AT Should we have strm type in here, defaulting to REFERENCE?

        @JsonCreator
        public ReferenceLoader(@JsonProperty("loaderPipeline") final DocRef loaderPipeline,
                               @JsonProperty("referenceFeed") final DocRef referenceFeed) {
            this.loaderPipeline = loaderPipeline;
            this.referenceFeed = referenceFeed;
        }

        public DocRef getLoaderPipeline() {
            return loaderPipeline;
        }

        public DocRef getReferenceFeed() {
            return referenceFeed;
        }

        @Override
        public String toString() {
            return "ReferenceLoader{" +
                    "loaderPipeline=" + loaderPipeline +
                    ", referenceFeed='" + referenceFeed + '\'' +
                    '}';
        }

        @ValidationMethod(message = "loaderPipeline docRef type must be '" + PipelineDoc.DOCUMENT_TYPE + "'")
        @JsonIgnore
        public boolean isCorrectLoaderType() {
            return loaderPipeline != null && PipelineDoc.DOCUMENT_TYPE.equals(loaderPipeline.getType());
        }

        @ValidationMethod(message = "loaderPipeline docRef must have a UUID")
        @JsonIgnore
        public boolean isValidLoaderDocRef() {
            return loaderPipeline != null && loaderPipeline.getUuid() != null && !loaderPipeline.getUuid().isEmpty();
        }

        @ValidationMethod(message = "referenceFeed docRef must have a UUID or a name. The lookup will " +
                "be faster if both are supplied")
        @JsonIgnore
        public boolean isValidFeedDocRef() {
            return referenceFeed != null && (
                    (referenceFeed.getUuid() != null && !referenceFeed.getUuid().isEmpty()) ||
                            (referenceFeed.getName() != null && !referenceFeed.getName().isEmpty()));
        }
    }
}
