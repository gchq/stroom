/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.aws.s3.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class S3ClientConfig {

    public static final String DEFAULT_BUCKET_NAME = "stroom.${feed}.${type}";
    public static final String DEFAULT_KEY_PATTERN =
            "${type}/${year}/${month}/${day}/${idPath}/${feed}/${idPadded}.zip";

    @JsonProperty
    private final AwsCredentials credentials;
    @JsonProperty
    private final AwsAssumeRole assumeRole;

    @JsonProperty
    private final Long readBufferSizeInBytes;
    @JsonProperty
    private final String region;
    @JsonProperty
    private final Long minimalPartSizeInBytes;
    @JsonProperty
    private final Double targetThroughputInGbps;
    @JsonProperty
    private final Integer maxConcurrency;
    @JsonProperty
    private final String endpointOverride;
    @JsonProperty
    private final Boolean checksumValidationEnabled;
    @JsonProperty
    private final AwsHttpConfig httpConfiguration;
    @JsonProperty
    private final Boolean accelerate;
    @JsonProperty
    private final Boolean forcePathStyle;

    @JsonProperty
    private final Integer numRetries;
    @JsonProperty
    private final boolean crossRegionAccessEnabled;
    @JsonProperty
    private final Long thresholdInBytes;

    @JsonProperty
    private final boolean async;
    @JsonProperty
    private final boolean multipart;
    @JsonProperty
    private final boolean createBuckets;
    @JsonProperty
    private final String bucketName;
    @JsonProperty
    private final String keyPattern;

    @JsonCreator
    public S3ClientConfig(@JsonProperty("credentials") final AwsCredentials credentials,
                          @JsonProperty("assumeRole") final AwsAssumeRole assumeRole,
                          @JsonProperty("readBufferSizeInBytes") final Long readBufferSizeInBytes,
                          @JsonProperty("region") final String region,
                          @JsonProperty("minimalPartSizeInBytes") final Long minimalPartSizeInBytes,
                          @JsonProperty("targetThroughputInGbps") final Double targetThroughputInGbps,
                          @JsonProperty("maxConcurrency") final Integer maxConcurrency,
                          @JsonProperty("endpointOverride") final String endpointOverride,
                          @JsonProperty("checksumValidationEnabled") final Boolean checksumValidationEnabled,
                          @JsonProperty("httpConfiguration") final AwsHttpConfig httpConfiguration,
                          @JsonProperty("accelerate") final Boolean accelerate,
                          @JsonProperty("forcePathStyle") final Boolean forcePathStyle,
                          @JsonProperty("numRetries") final Integer numRetries,
                          @JsonProperty("crossRegionAccessEnabled") final boolean crossRegionAccessEnabled,
                          @JsonProperty("thresholdInBytes") final Long thresholdInBytes,
                          @JsonProperty("async") final boolean async,
                          @JsonProperty("multipart") final boolean multipart,
                          @JsonProperty("createBuckets") final boolean createBuckets,
                          @JsonProperty("bucketName") final String bucketName,
                          @JsonProperty("keyPattern") final String keyPattern) {
        this.credentials = credentials;
        this.assumeRole = assumeRole;
        this.readBufferSizeInBytes = readBufferSizeInBytes;
        this.region = region;
        this.minimalPartSizeInBytes = minimalPartSizeInBytes;
        this.targetThroughputInGbps = targetThroughputInGbps;
        this.maxConcurrency = maxConcurrency;
        this.endpointOverride = endpointOverride;
        this.checksumValidationEnabled = checksumValidationEnabled;
        this.httpConfiguration = httpConfiguration;
        this.accelerate = accelerate;
        this.forcePathStyle = forcePathStyle;
        this.numRetries = numRetries;
        this.crossRegionAccessEnabled = crossRegionAccessEnabled;
        this.thresholdInBytes = thresholdInBytes;
        this.async = async;
        this.multipart = multipart;
        this.createBuckets = createBuckets;
        this.bucketName = bucketName;
        this.keyPattern = keyPattern;
    }

    public AwsCredentials getCredentials() {
        return credentials;
    }

    public AwsAssumeRole getAssumeRole() {
        return assumeRole;
    }

    public Long getReadBufferSizeInBytes() {
        return readBufferSizeInBytes;
    }

    public String getRegion() {
        return region;
    }

    public Long getMinimalPartSizeInBytes() {
        return minimalPartSizeInBytes;
    }

    public Double getTargetThroughputInGbps() {
        return targetThroughputInGbps;
    }

    public Integer getMaxConcurrency() {
        return maxConcurrency;
    }

    public String getEndpointOverride() {
        return endpointOverride;
    }

    public Boolean getChecksumValidationEnabled() {
        return checksumValidationEnabled;
    }

    public AwsHttpConfig getHttpConfiguration() {
        return httpConfiguration;
    }

    public Boolean getAccelerate() {
        return accelerate;
    }

    public Boolean getForcePathStyle() {
        return forcePathStyle;
    }

    public Integer getNumRetries() {
        return numRetries;
    }

    public boolean isCrossRegionAccessEnabled() {
        return crossRegionAccessEnabled;
    }

    public Long getThresholdInBytes() {
        return thresholdInBytes;
    }

    public boolean isAsync() {
        return async;
    }

    public boolean isMultipart() {
        return multipart;
    }

    public boolean isCreateBuckets() {
        return createBuckets;
    }

    public String getBucketName() {
        return bucketName;
    }

    public String getKeyPattern() {
        return keyPattern;
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }


    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final S3ClientConfig that = (S3ClientConfig) o;
        return crossRegionAccessEnabled == that.crossRegionAccessEnabled &&
                async == that.async &&
                multipart == that.multipart &&
                createBuckets == that.createBuckets &&
                Objects.equals(credentials, that.credentials) &&
                Objects.equals(readBufferSizeInBytes, that.readBufferSizeInBytes) &&
                Objects.equals(region, that.region) &&
                Objects.equals(minimalPartSizeInBytes, that.minimalPartSizeInBytes) &&
                Objects.equals(targetThroughputInGbps, that.targetThroughputInGbps) &&
                Objects.equals(maxConcurrency, that.maxConcurrency) &&
                Objects.equals(endpointOverride, that.endpointOverride) &&
                Objects.equals(checksumValidationEnabled, that.checksumValidationEnabled) &&
                Objects.equals(httpConfiguration, that.httpConfiguration) &&
                Objects.equals(accelerate, that.accelerate) &&
                Objects.equals(forcePathStyle, that.forcePathStyle) &&
                Objects.equals(numRetries, that.numRetries) &&
                Objects.equals(thresholdInBytes, that.thresholdInBytes) &&
                Objects.equals(bucketName, that.bucketName) &&
                Objects.equals(keyPattern, that.keyPattern);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                credentials,
                readBufferSizeInBytes,
                region,
                minimalPartSizeInBytes,
                targetThroughputInGbps,
                maxConcurrency,
                endpointOverride,
                checksumValidationEnabled,
                httpConfiguration,
                accelerate,
                forcePathStyle,
                numRetries,
                crossRegionAccessEnabled,
                thresholdInBytes,
                async,
                multipart,
                createBuckets,
                bucketName,
                keyPattern);
    }

    @Override
    public String toString() {
        return "S3ClientConfig{" +
                "credentials=" + credentials +
                ", readBufferSizeInBytes=" + readBufferSizeInBytes +
                ", region='" + region + '\'' +
                ", minimalPartSizeInBytes=" + minimalPartSizeInBytes +
                ", targetThroughputInGbps=" + targetThroughputInGbps +
                ", maxConcurrency=" + maxConcurrency +
                ", endpointOverride='" + endpointOverride + '\'' +
                ", checksumValidationEnabled=" + checksumValidationEnabled +
                ", httpConfiguration=" + httpConfiguration +
                ", accelerate=" + accelerate +
                ", forcePathStyle=" + forcePathStyle +
                ", numRetries=" + numRetries +
                ", crossRegionAccessEnabled=" + crossRegionAccessEnabled +
                ", thresholdInBytes=" + thresholdInBytes +
                ", async=" + async +
                ", multipart=" + multipart +
                ", createBuckets=" + createBuckets +
                ", bucketName='" + bucketName + '\'' +
                ", keyPattern='" + keyPattern + '\'' +
                '}';
    }

    public static class Builder {

        private AwsCredentials credentials;
        private AwsAssumeRole assumeRole;

        private Long readBufferSizeInBytes;
        private String region;
        private Long minimalPartSizeInBytes;
        private Double targetThroughputInGbps;
        private Integer maxConcurrency;
        private String endpointOverride;
        private Boolean checksumValidationEnabled;
        private AwsHttpConfig httpConfiguration;
        private Boolean accelerate;
        private Boolean forcePathStyle;

        private Integer numRetries;
        private boolean crossRegionAccessEnabled;
        private Long thresholdInBytes;
        private boolean async;
        private boolean multipart;
        private boolean createBuckets;
        private String bucketName = DEFAULT_BUCKET_NAME;
        private String keyPattern = DEFAULT_KEY_PATTERN;

        public Builder() {
        }

        public Builder(final S3ClientConfig s3ClientConfig) {
            this.credentials = s3ClientConfig.credentials;

            this.readBufferSizeInBytes = s3ClientConfig.readBufferSizeInBytes;
            this.region = s3ClientConfig.region;
            this.minimalPartSizeInBytes = s3ClientConfig.minimalPartSizeInBytes;
            this.targetThroughputInGbps = s3ClientConfig.targetThroughputInGbps;
            this.maxConcurrency = s3ClientConfig.maxConcurrency;
            this.endpointOverride = s3ClientConfig.endpointOverride;
            this.checksumValidationEnabled = s3ClientConfig.checksumValidationEnabled;
            this.httpConfiguration = s3ClientConfig.httpConfiguration;
            this.accelerate = s3ClientConfig.accelerate;
            this.forcePathStyle = s3ClientConfig.forcePathStyle;

            this.numRetries = s3ClientConfig.numRetries;
            this.crossRegionAccessEnabled = s3ClientConfig.crossRegionAccessEnabled;
            this.thresholdInBytes = s3ClientConfig.thresholdInBytes;

            this.async = s3ClientConfig.async;
            this.multipart = s3ClientConfig.multipart;
            this.createBuckets = s3ClientConfig.createBuckets;
            this.bucketName = s3ClientConfig.bucketName;
            this.keyPattern = s3ClientConfig.keyPattern;
        }

        public Builder credentials(final AwsCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder assumeRole(final AwsAssumeRole assumeRole) {
            this.assumeRole = assumeRole;
            return this;
        }

        public Builder readBufferSizeInBytes(final Long readBufferSizeInBytes) {
            this.readBufferSizeInBytes = readBufferSizeInBytes;
            return this;
        }

        public Builder region(final String region) {
            this.region = region;
            return this;
        }

        public Builder minimalPartSizeInBytes(final Long minimalPartSizeInBytes) {
            this.minimalPartSizeInBytes = minimalPartSizeInBytes;
            return this;
        }

        public Builder targetThroughputInGbps(final Double targetThroughputInGbps) {
            this.targetThroughputInGbps = targetThroughputInGbps;
            return this;
        }

        public Builder maxConcurrency(final Integer maxConcurrency) {
            this.maxConcurrency = maxConcurrency;
            return this;
        }

        public Builder endpointOverride(final String endpointOverride) {
            this.endpointOverride = endpointOverride;
            return this;
        }

        public Builder checksumValidationEnabled(final Boolean checksumValidationEnabled) {
            this.checksumValidationEnabled = checksumValidationEnabled;
            return this;
        }

        public Builder httpConfiguration(final AwsHttpConfig httpConfiguration) {
            this.httpConfiguration = httpConfiguration;
            return this;
        }

        public Builder accelerate(final Boolean accelerate) {
            this.accelerate = accelerate;
            return this;
        }

        public Builder forcePathStyle(final Boolean forcePathStyle) {
            this.forcePathStyle = forcePathStyle;
            return this;
        }

        public Builder numRetries(final Integer numRetries) {
            this.numRetries = numRetries;
            return this;
        }

        public Builder crossRegionAccessEnabled(final boolean crossRegionAccessEnabled) {
            this.crossRegionAccessEnabled = crossRegionAccessEnabled;
            return this;
        }

        public Builder thresholdInBytes(final Long thresholdInBytes) {
            this.thresholdInBytes = thresholdInBytes;
            return this;
        }

        public Builder async(final boolean async) {
            this.async = async;
            return this;
        }

        public Builder multipart(final boolean multipart) {
            this.multipart = multipart;
            return this;
        }

        public Builder createBuckets(final boolean createBuckets) {
            this.createBuckets = createBuckets;
            return this;
        }

        public Builder bucketName(final String bucketName) {
            this.bucketName = bucketName;
            return this;
        }

        public Builder keyPattern(final String keyPattern) {
            this.keyPattern = keyPattern;
            return this;
        }

        public S3ClientConfig build() {
            return new S3ClientConfig(
                    credentials,
                    assumeRole,
                    readBufferSizeInBytes,
                    region,
                    minimalPartSizeInBytes,
                    targetThroughputInGbps,
                    maxConcurrency,
                    endpointOverride,
                    checksumValidationEnabled,
                    httpConfiguration,
                    accelerate,
                    forcePathStyle,
                    numRetries,
                    crossRegionAccessEnabled,
                    thresholdInBytes,
                    async,
                    multipart,
                    createBuckets,
                    bucketName,
                    keyPattern);
        }
    }
}
