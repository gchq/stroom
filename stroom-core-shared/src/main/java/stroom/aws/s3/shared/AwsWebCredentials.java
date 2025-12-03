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
public final class AwsWebCredentials implements AwsCredentials {

    /**
     * Define the role arn that should be used by this credentials provider.
     */
    @JsonProperty
    private final String roleArn;

    /**
     * Define the role session name that should be used by this credentials provider.
     */
    @JsonProperty
    private final String roleSessionName;

    /**
     * Define the absolute path to the web identity token file that should be used by this credentials provider.
     */
    @JsonProperty
    private final String webIdentityTokenFile;

    /**
     * Define whether the provider should fetch credentials asynchronously in the background.
     */
    @JsonProperty
    private final Boolean asyncCredentialUpdateEnabled;

    /**
     * Configure the amount of time, relative to STS token expiration, that the cached credentials are considered close
     * to stale and should be updated.
     *
     * <p>Prefetch updates will occur between the specified time and the stale time of the provider. Prefetch
     * updates may be asynchronous. See {@link #asyncCredentialUpdateEnabled}.
     *
     * <p>By default, this is 5 minutes.
     */
    @JsonProperty
    private final String prefetchTime;

    /**
     * Configure the amount of time, relative to STS token expiration, that the cached credentials are considered stale
     * and must be updated. All threads will block until the value is updated.
     *
     * <p>By default, this is 1 minute.
     */
    @JsonProperty
    private final String staleTime;

    /**
     * @param sessionDuration
     * @return
     */
    @JsonProperty
    private final String sessionDuration;

    @JsonCreator
    public AwsWebCredentials(@JsonProperty("roleArn") final String roleArn,
                             @JsonProperty("roleSessionName") final String roleSessionName,
                             @JsonProperty("webIdentityTokenFile") final String webIdentityTokenFile,
                             @JsonProperty("asyncCredentialUpdateEnabled") final Boolean asyncCredentialUpdateEnabled,
                             @JsonProperty("prefetchTime") final String prefetchTime,
                             @JsonProperty("staleTime") final String staleTime,
                             @JsonProperty("sessionDuration") final String sessionDuration) {
        this.roleArn = roleArn;
        this.roleSessionName = roleSessionName;
        this.webIdentityTokenFile = webIdentityTokenFile;
        this.asyncCredentialUpdateEnabled = asyncCredentialUpdateEnabled;
        this.prefetchTime = prefetchTime;
        this.staleTime = staleTime;
        this.sessionDuration = sessionDuration;
    }

    public String getRoleArn() {
        return roleArn;
    }

    public String getRoleSessionName() {
        return roleSessionName;
    }

    public String getWebIdentityTokenFile() {
        return webIdentityTokenFile;
    }

    public Boolean getAsyncCredentialUpdateEnabled() {
        return asyncCredentialUpdateEnabled;
    }

    public String getPrefetchTime() {
        return prefetchTime;
    }

    public String getStaleTime() {
        return staleTime;
    }

    public String getSessionDuration() {
        return sessionDuration;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AwsWebCredentials that = (AwsWebCredentials) o;
        return Objects.equals(roleArn, that.roleArn) && Objects.equals(roleSessionName,
                that.roleSessionName) && Objects.equals(webIdentityTokenFile,
                that.webIdentityTokenFile) && Objects.equals(asyncCredentialUpdateEnabled,
                that.asyncCredentialUpdateEnabled) && Objects.equals(prefetchTime,
                that.prefetchTime) && Objects.equals(staleTime, that.staleTime) && Objects.equals(
                sessionDuration,
                that.sessionDuration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleArn,
                roleSessionName,
                webIdentityTokenFile,
                asyncCredentialUpdateEnabled,
                prefetchTime,
                staleTime,
                sessionDuration);
    }

    @Override
    public String toString() {
        return "AwsWebCredentials{" +
                "roleArn='" + roleArn + '\'' +
                ", roleSessionName='" + roleSessionName + '\'' +
                ", webIdentityTokenFile='" + webIdentityTokenFile + '\'' +
                ", asyncCredentialUpdateEnabled=" + asyncCredentialUpdateEnabled +
                ", prefetchTime='" + prefetchTime + '\'' +
                ", staleTime='" + staleTime + '\'' +
                ", sessionDuration='" + sessionDuration + '\'' +
                '}';
    }
}
