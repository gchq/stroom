/*
 * Copyright 2026 Crown Copyright
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

package stroom.dataconnection.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// The settings a [DataConnectionDoc] uses to query Amazon Athena through the AWS SDK.
///
/// Static AWS keys are held in the Credentials store and referenced here by name only. With no
/// credential name, the AWS default credentials chain is used (environment, web identity, instance
/// profile and so on), so this object is safe to send to the browser and to include in exports.
@JsonPropertyOrder({
        "region",
        "workGroup",
        "catalog",
        "database",
        "outputLocation",
        "credentialName",
        "resultReuseMinutes",
        "minRefreshMinutes",
        "maxDataScannedMb"})
@JsonInclude(Include.NON_NULL)
public class AthenaConnectionSettings {

    /// The Athena workgroup used when none is set.
    public static final String DEFAULT_WORK_GROUP = "primary";
    /// The Athena data catalog used when none is set.
    public static final String DEFAULT_CATALOG = "AwsDataCatalog";
    /// The Athena database used when none is set.
    public static final String DEFAULT_DATABASE = "default";
    /// The shortest interval at which a dashboard reruns an Athena query automatically, when none
    /// is set. Every Athena run is billed by the data it scans.
    public static final int DEFAULT_MIN_REFRESH_MINUTES = 5;

    @JsonProperty
    private final String region;
    @JsonProperty
    private final String workGroup;
    @JsonProperty
    private final String catalog;
    @JsonProperty
    private final String database;
    @JsonProperty
    private final String outputLocation;
    @JsonProperty
    private final String credentialName;
    @JsonProperty
    private final Integer resultReuseMinutes;
    @JsonProperty
    private final Integer minRefreshMinutes;
    @JsonProperty
    private final Integer maxDataScannedMb;

    /// Creates Athena connection settings.
    ///
    /// @param region             The AWS region, e.g. `eu-west-2`.
    /// @param workGroup          The Athena workgroup, or `null` for [#DEFAULT_WORK_GROUP].
    /// @param catalog            The data catalog, or `null` for [#DEFAULT_CATALOG].
    /// @param database           The database, or `null` for [#DEFAULT_DATABASE].
    /// @param outputLocation     The S3 URI for query results, or `null` to use the workgroup's.
    /// @param credentialName     The name of an AWS key credential, or `null` for the default chain.
    /// @param resultReuseMinutes How long Athena may reuse a previous result, or `null` to never reuse.
    /// @param minRefreshMinutes  The shortest automatic rerun interval of a dashboard in minutes, or
    ///                           `null` for [#DEFAULT_MIN_REFRESH_MINUTES].
    /// @param maxDataScannedMb   The most data, in MB, a query may scan before Stroom stops it, or
    ///                           `null` or 0 for no limit beyond the workgroup's.
    @JsonCreator
    public AthenaConnectionSettings(@JsonProperty("region") final String region,
                                    @JsonProperty("workGroup") final String workGroup,
                                    @JsonProperty("catalog") final String catalog,
                                    @JsonProperty("database") final String database,
                                    @JsonProperty("outputLocation") final String outputLocation,
                                    @JsonProperty("credentialName") final String credentialName,
                                    @JsonProperty("resultReuseMinutes") final Integer resultReuseMinutes,
                                    @JsonProperty("minRefreshMinutes") final Integer minRefreshMinutes,
                                    @JsonProperty("maxDataScannedMb") final Integer maxDataScannedMb) {
        this.region = region;
        this.workGroup = workGroup;
        this.catalog = catalog;
        this.database = database;
        this.outputLocation = outputLocation;
        this.credentialName = credentialName;
        this.resultReuseMinutes = resultReuseMinutes;
        this.minRefreshMinutes = minRefreshMinutes;
        this.maxDataScannedMb = maxDataScannedMb;
    }

    /// @return The AWS region, or `null` if not set.
    public String getRegion() {
        return region;
    }

    /// @return The Athena workgroup, or `null` to use [#DEFAULT_WORK_GROUP].
    public String getWorkGroup() {
        return workGroup;
    }

    /// @return The data catalog, or `null` to use [#DEFAULT_CATALOG].
    public String getCatalog() {
        return catalog;
    }

    /// @return The database, or `null` to use [#DEFAULT_DATABASE].
    public String getDatabase() {
        return database;
    }

    /// @return The S3 URI for query results, or `null` to use the workgroup's.
    public String getOutputLocation() {
        return outputLocation;
    }

    /// @return The name of the AWS key credential, or `null` to use the AWS default credentials chain.
    public String getCredentialName() {
        return credentialName;
    }

    /// @return How long, in minutes, Athena may reuse a previous result, or `null` to never reuse one.
    public Integer getResultReuseMinutes() {
        return resultReuseMinutes;
    }

    /// @return The shortest interval, in minutes, at which a dashboard may rerun queries automatically, or `null` for
    /// [#DEFAULT_MIN_REFRESH_MINUTES].
    public Integer getMinRefreshMinutes() {
        return minRefreshMinutes;
    }

    /// @return The most data, in MB, a query may scan before Stroom stops it, or `null` or 0 for no
    ///         limit beyond the workgroup's.
    public Integer getMaxDataScannedMb() {
        return maxDataScannedMb;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AthenaConnectionSettings that = (AthenaConnectionSettings) o;
        return Objects.equals(region, that.region) &&
               Objects.equals(workGroup, that.workGroup) &&
               Objects.equals(catalog, that.catalog) &&
               Objects.equals(database, that.database) &&
               Objects.equals(outputLocation, that.outputLocation) &&
               Objects.equals(credentialName, that.credentialName) &&
               Objects.equals(resultReuseMinutes, that.resultReuseMinutes) &&
               Objects.equals(minRefreshMinutes, that.minRefreshMinutes) &&
               Objects.equals(maxDataScannedMb, that.maxDataScannedMb);
    }

    @Override
    public int hashCode() {
        return Objects.hash(region,
                workGroup,
                catalog,
                database,
                outputLocation,
                credentialName,
                resultReuseMinutes,
                minRefreshMinutes,
                maxDataScannedMb);
    }

    @Override
    public String toString() {
        return "AthenaConnectionSettings{" +
               "region='" + region + '\'' +
               ", workGroup='" + workGroup + '\'' +
               ", catalog='" + catalog + '\'' +
               ", database='" + database + '\'' +
               ", outputLocation='" + outputLocation + '\'' +
               ", credentialName='" + credentialName + '\'' +
               ", resultReuseMinutes=" + resultReuseMinutes +
               ", minRefreshMinutes=" + minRefreshMinutes +
               ", maxDataScannedMb=" + maxDataScannedMb +
               '}';
    }

    /// @return A builder initialised with the values of these settings.
    public Builder copy() {
        return new Builder(this);
    }

    /// @return A new, empty builder.
    public static Builder builder() {
        return new Builder();
    }

    /// Builds [AthenaConnectionSettings] instances.
    public static final class Builder {

        private String region;
        private String workGroup;
        private String catalog;
        private String database;
        private String outputLocation;
        private String credentialName;
        private Integer resultReuseMinutes;
        private Integer minRefreshMinutes;
        private Integer maxDataScannedMb;

        private Builder() {
        }

        private Builder(final AthenaConnectionSettings settings) {
            this.region = settings.region;
            this.workGroup = settings.workGroup;
            this.catalog = settings.catalog;
            this.database = settings.database;
            this.outputLocation = settings.outputLocation;
            this.credentialName = settings.credentialName;
            this.resultReuseMinutes = settings.resultReuseMinutes;
            this.minRefreshMinutes = settings.minRefreshMinutes;
            this.maxDataScannedMb = settings.maxDataScannedMb;
        }

        /// @param region The AWS region, e.g. `eu-west-2`.
        /// @return This builder.
        public Builder region(final String region) {
            this.region = region;
            return this;
        }

        /// @param workGroup The Athena workgroup, or `null` for the default.
        /// @return This builder.
        public Builder workGroup(final String workGroup) {
            this.workGroup = workGroup;
            return this;
        }

        /// @param catalog The data catalog, or `null` for the default.
        /// @return This builder.
        public Builder catalog(final String catalog) {
            this.catalog = catalog;
            return this;
        }

        /// @param database The database, or `null` for the default.
        /// @return This builder.
        public Builder database(final String database) {
            this.database = database;
            return this;
        }

        /// @param outputLocation The S3 URI for query results, or `null` to use the workgroup's.
        /// @return This builder.
        public Builder outputLocation(final String outputLocation) {
            this.outputLocation = outputLocation;
            return this;
        }

        /// @param credentialName The name of an AWS key credential, or `null` for the default chain.
        /// @return This builder.
        public Builder credentialName(final String credentialName) {
            this.credentialName = credentialName;
            return this;
        }

        /// @param resultReuseMinutes How long Athena may reuse a result, or `null` to never reuse.
        /// @return This builder.
        public Builder resultReuseMinutes(final Integer resultReuseMinutes) {
            this.resultReuseMinutes = resultReuseMinutes;
            return this;
        }

        /// @param minRefreshMinutes The shortest automatic rerun interval of a dashboard in minutes,
        ///                          or `null` for the default.
        /// @return This builder.
        public Builder minRefreshMinutes(final Integer minRefreshMinutes) {
            this.minRefreshMinutes = minRefreshMinutes;
            return this;
        }

        /// @param maxDataScannedMb The most data, in MB, a query may scan before Stroom stops it, or
        ///                         `null` or 0 for no limit.
        /// @return This builder.
        public Builder maxDataScannedMb(final Integer maxDataScannedMb) {
            this.maxDataScannedMb = maxDataScannedMb;
            return this;
        }

        /// @return New [AthenaConnectionSettings] holding the values set on this builder.
        public AthenaConnectionSettings build() {
            return new AthenaConnectionSettings(
                    region,
                    workGroup,
                    catalog,
                    database,
                    outputLocation,
                    credentialName,
                    resultReuseMinutes,
                    minRefreshMinutes,
                    maxDataScannedMb);
        }
    }
}
