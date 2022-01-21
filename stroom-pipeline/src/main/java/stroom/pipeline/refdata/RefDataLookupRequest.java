package stroom.pipeline.refdata;

import stroom.docref.DocRef;
import stroom.pipeline.shared.PipelineDoc;
import stroom.util.date.DateUtil;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.validation.ValidationMethod;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;

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
    private final String effectiveTime; // could be date str or epoch ms

    @JsonIgnore
    private final Long effectiveTimeEpochMs;

    @Valid
    @NotNull
    @NotEmpty
    @JsonProperty
    private final List<ReferenceLoader> referenceLoaders;

    @JsonCreator
    public RefDataLookupRequest(@JsonProperty("mapName") final String mapName,
                                @JsonProperty("key") final String key,
                                @JsonProperty("effectiveTime") final String effectiveTime,
                                @JsonProperty("referenceLoaders") final List<ReferenceLoader> referenceLoaders) {
        this.mapName = mapName;
        this.key = key;

        this.effectiveTime = effectiveTime;
        if (effectiveTime != null) {
            long epochMs;

            try {
                epochMs = Long.parseLong(effectiveTime);
            } catch (NumberFormatException e) {
                try {
                    epochMs = DateUtil.parseNormalDateTimeString(effectiveTime);
                } catch (Exception exception) {
                    throw new IllegalArgumentException("Invalid date " + effectiveTime);
                }
            }
            this.effectiveTimeEpochMs = epochMs;
        } else {
            this.effectiveTimeEpochMs = null;
        }
        this.referenceLoaders = referenceLoaders;
    }

    public String getMapName() {
        return mapName;
    }

    public String getKey() {
        return key;
    }

    /**
     * @return data string or epoch ms
     */
    public String getEffectiveTime() {
        return effectiveTime;
    }

    @JsonIgnore
    public Optional<Long> getOptEffectiveTimeAsEpochMs() {
        return Optional.ofNullable(effectiveTimeEpochMs);
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

        @Valid
        @JsonProperty
        private final String streamType;

        // TODO @AT Should we have strm type in here, defaulting to REFERENCE?

        @JsonCreator
        public ReferenceLoader(@JsonProperty("loaderPipeline") final DocRef loaderPipeline,
                               @JsonProperty("referenceFeed") final DocRef referenceFeed,
                               @JsonProperty("streamType") final String streamType) {
            this.loaderPipeline = loaderPipeline;
            this.referenceFeed = referenceFeed;
            this.streamType = streamType;
        }

        public DocRef getLoaderPipeline() {
            return loaderPipeline;
        }

        public DocRef getReferenceFeed() {
            return referenceFeed;
        }

        public String getStreamType() {
            return streamType;
        }

        @Override
        public String toString() {
            return "ReferenceLoader{" +
                    "loaderPipeline=" + loaderPipeline +
                    ", referenceFeed=" + referenceFeed +
                    ", streamType='" + streamType + '\'' +
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
