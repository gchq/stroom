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
public final class AwsProfileCredentials implements AwsCredentials {

    @JsonProperty
    private final String profileName;
    @JsonProperty
    private final String profileFilePath;

    @JsonCreator
    public AwsProfileCredentials(@JsonProperty("profileName") final String profileName,
                                 @JsonProperty("profileFilePath") final String profileFilePath) {
        this.profileName = profileName;
        this.profileFilePath = profileFilePath;
    }

    public String getProfileName() {
        return profileName;
    }

    public String getProfileFilePath() {
        return profileFilePath;
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
        final AwsProfileCredentials that = (AwsProfileCredentials) o;
        return Objects.equals(profileName, that.profileName) && Objects.equals(profileFilePath,
                that.profileFilePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(profileName, profileFilePath);
    }

    @Override
    public String toString() {
        return "AwsProfileCredentials{" +
                "profileName='" + profileName + '\'' +
                ", profileFilePath='" + profileFilePath + '\'' +
                '}';
    }

    public static class Builder {

        private String profileName;
        private String profileFilePath;

        public Builder() {
        }

        public Builder(final AwsProfileCredentials awsProfileCredentials) {
            this.profileName = awsProfileCredentials.profileName;
            this.profileFilePath = awsProfileCredentials.profileFilePath;
        }

        public Builder profileName(final String profileName) {
            this.profileName = profileName;
            return this;
        }

        public Builder profileFilePath(final String profileFilePath) {
            this.profileFilePath = profileFilePath;
            return this;
        }

        public AwsProfileCredentials build() {
            return new AwsProfileCredentials(
                    profileName,
                    profileFilePath);
        }
    }
}
