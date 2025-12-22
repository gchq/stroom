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

package stroom.security.identity.token;

import stroom.util.shared.HasAuditInfo;
import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

// TODO make this immutable with a builder for the optional stuff
//   and a JsonCreator on the ctor, then get rid of as many boxed primitives as
//   possible
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
@Deprecated // Keeping it else it breaks the React code
public class ApiKey implements HasIntegerId, HasAuditInfo {

    @JsonProperty
    private Integer id;
    @JsonProperty
    private Integer version;
    @JsonProperty
    private Long createTimeMs;
    @JsonProperty
    private Long updateTimeMs;
    @JsonProperty
    private String createUser;
    @JsonProperty
    private String updateUser;

    @JsonProperty
    private String userId;
    @JsonProperty
    private String userEmail;
    @JsonProperty
    private String type;
    @JsonProperty
    private String data;
    @JsonProperty
    private Long expiresOnMs;
    @JsonProperty
    private String comments;
    @JsonProperty
    private boolean enabled;

    public ApiKey() {
    }

    @JsonCreator
    public ApiKey(@JsonProperty("id") final Integer id,
                  @JsonProperty("version") final Integer version,
                  @JsonProperty("createTimeMs") final Long createTimeMs,
                  @JsonProperty("updateTimeMs") final Long updateTimeMs,
                  @JsonProperty("createUser") final String createUser,
                  @JsonProperty("updateUser") final String updateUser,
                  @JsonProperty("userId") final String userId,
                  @JsonProperty("userEmail") final String userEmail,
                  @JsonProperty("type") final String type,
                  @JsonProperty("data") final String data,
                  @JsonProperty("expiresOnMs") final Long expiresOnMs,
                  @JsonProperty("comments") final String comments,
                  @JsonProperty("enabled") final boolean enabled) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.userId = userId;
        this.userEmail = userEmail;
        this.type = type;
        this.data = data;
        this.expiresOnMs = expiresOnMs;
        this.comments = comments;
        this.enabled = enabled;
    }

    @Override
    public Integer getId() {
        return id;
    }

    public void setId(final Integer id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(final Integer version) {
        this.version = version;
    }

    public Long getCreateTimeMs() {
        return createTimeMs;
    }

    public void setCreateTimeMs(final Long createTimeMs) {
        this.createTimeMs = createTimeMs;
    }

    public Long getUpdateTimeMs() {
        return updateTimeMs;
    }

    public void setUpdateTimeMs(final Long updateTimeMs) {
        this.updateTimeMs = updateTimeMs;
    }

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(final String createUser) {
        this.createUser = createUser;
    }

    public String getUpdateUser() {
        return updateUser;
    }

    public void setUpdateUser(final String updateUser) {
        this.updateUser = updateUser;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(final String userEmail) {
        this.userEmail = userEmail;
    }

    public String getType() {
        return type;
    }

    public void setType(final String type) {
        this.type = type;
    }

    public String getData() {
        return data;
    }

    public void setData(final String data) {
        this.data = data;
    }

    public Long getExpiresOnMs() {
        return expiresOnMs;
    }

    public void setExpiresOnMs(final Long expiresOnMs) {
        this.expiresOnMs = expiresOnMs;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}
