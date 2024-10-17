package stroom.aws.s3.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class S3Config extends AbstractConfig implements IsStroomConfig {

    private final String skeletonConfigContent;
    private final CacheConfig s3ConfigDocCache;

    public S3Config() {
        skeletonConfigContent = DEFAULT_SKELETON_CONFIG_CONTENT;
        s3ConfigDocCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofSeconds(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public S3Config(@JsonProperty("skeletonConfigContent") final String skeletonConfigContent,
                    @JsonProperty("s3ConfigDocCache") final CacheConfig s3ConfigDocCache) {
        this.skeletonConfigContent = skeletonConfigContent;
        this.s3ConfigDocCache = s3ConfigDocCache;
    }

    @JsonProperty("skeletonConfigContent")
    @JsonPropertyDescription("The value of this property will be used to pre-populate a new S3 Configuration.")
    public String getSkeletonConfigContent() {
        return skeletonConfigContent;
    }

    @JsonProperty("s3ConfigDocCache")
    public CacheConfig getS3ConfigDocCache() {
        return s3ConfigDocCache;
    }

    @Override
    public String toString() {
        return "S3Config{" +
                "skeletonConfigContent='" + skeletonConfigContent + '\'' +
                ", s3ConfigDocCache=" + s3ConfigDocCache +
                '}';
    }

    // Put this at the bottom to keep it out of the way
    private static final String DEFAULT_SKELETON_CONFIG_CONTENT = """
            {
              "credentialsProviderType" : "DEFAULT",
              "region" : "eu-west-2",
              "bucketName" : "XXXX-eu-west-2",
              "keyPattern" : "${type}/${year}/${month}/${day}/${idPath}/${feed}/${idPadded}.zip"
            }
            """;
}
