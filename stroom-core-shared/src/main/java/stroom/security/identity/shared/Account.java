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

package stroom.security.identity.shared;

import stroom.util.shared.HasIntegerId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Optional;

@JsonInclude(Include.NON_NULL)
public class Account implements HasIntegerId {

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
    private String email;
    @JsonProperty
    private String firstName;
    @JsonProperty
    private String lastName;
    @JsonProperty
    private String comments;
    @JsonProperty
    private int loginCount;
    @JsonProperty
    private int loginFailures;
    @JsonProperty
    private Long lastLoginMs;
    @JsonProperty
    private Long reactivatedMs;
    @JsonProperty
    private boolean forcePasswordChange;
    @JsonProperty
    private boolean neverExpires;
    @JsonProperty
    private boolean enabled;
    @JsonProperty
    private boolean inactive;
    @JsonProperty
    private boolean locked;
    @JsonProperty
    private boolean processingAccount;

    public Account() {
    }

    @JsonCreator
    public Account(@JsonProperty("id") final Integer id,
                   @JsonProperty("version") final Integer version,
                   @JsonProperty("createTimeMs") final Long createTimeMs,
                   @JsonProperty("updateTimeMs") final Long updateTimeMs,
                   @JsonProperty("createUser") final String createUser,
                   @JsonProperty("updateUser") final String updateUser,
                   @JsonProperty("userId") final String userId,
                   @JsonProperty("email") final String email,
                   @JsonProperty("firstName") final String firstName,
                   @JsonProperty("lastName") final String lastName,
                   @JsonProperty("comments") final String comments,
                   @JsonProperty("loginCount") final int loginCount,
                   @JsonProperty("loginFailures") final int loginFailures,
                   @JsonProperty("lastLoginMs") final Long lastLoginMs,
                   @JsonProperty("reactivatedMs") final Long reactivatedMs,
                   @JsonProperty("forcePasswordChange") final boolean forcePasswordChange,
                   @JsonProperty("neverExpires") final boolean neverExpires,
                   @JsonProperty("enabled") final boolean enabled,
                   @JsonProperty("inactive") final boolean inactive,
                   @JsonProperty("locked") final boolean locked,
                   @JsonProperty("processingAccount") final boolean processingAccount) {
        this.id = id;
        this.version = version;
        this.createTimeMs = createTimeMs;
        this.updateTimeMs = updateTimeMs;
        this.createUser = createUser;
        this.updateUser = updateUser;
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.comments = comments;
        this.loginCount = loginCount;
        this.loginFailures = loginFailures;
        this.lastLoginMs = lastLoginMs;
        this.reactivatedMs = reactivatedMs;
        this.forcePasswordChange = forcePasswordChange;
        this.neverExpires = neverExpires;
        this.enabled = enabled;
        this.inactive = inactive;
        this.locked = locked;
        this.processingAccount = processingAccount;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(final String email) {
        this.email = email;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(final String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(final String lastName) {
        this.lastName = lastName;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(final String comments) {
        this.comments = comments;
    }

    public int getLoginCount() {
        return loginCount;
    }

    public void setLoginCount(final int loginCount) {
        this.loginCount = loginCount;
    }

    public int getLoginFailures() {
        return loginFailures;
    }

    public void setLoginFailures(final int loginFailures) {
        this.loginFailures = loginFailures;
    }

    public Long getLastLoginMs() {
        return lastLoginMs;
    }

    public void setLastLoginMs(final Long lastLoginMs) {
        this.lastLoginMs = lastLoginMs;
    }

    public Long getReactivatedMs() {
        return reactivatedMs;
    }

    public void setReactivatedMs(final Long reactivatedMs) {
        this.reactivatedMs = reactivatedMs;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public void setForcePasswordChange(final boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
    }

    public boolean isNeverExpires() {
        return neverExpires;
    }

    public void setNeverExpires(final boolean neverExpires) {
        this.neverExpires = neverExpires;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInactive() {
        return inactive;
    }

    public void setInactive(final boolean inactive) {
        this.inactive = inactive;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(final boolean locked) {
        this.locked = locked;
    }

    public boolean isProcessingAccount() {
        return processingAccount;
    }

    public void setProcessingAccount(final boolean processingAccount) {
        this.processingAccount = processingAccount;
    }

    /**
     * @return The users full name, e.g. "Joe Bloggs".
     */
    @JsonIgnore
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        } else {
            return String.join(
                    " ",
                    Optional.ofNullable(firstName).orElse(""),
                    Optional.ofNullable(lastName).orElse(""));
        }
    }

    @JsonIgnore
    public String getStatus() {
        if (locked) {
            return "Locked";
        } else if (inactive) {
            return "Inactive";
        } else if (enabled) {
            return "Enabled";
        } else {
            return "Disabled";
        }
    }

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", version=" + version +
                ", createTimeMs=" + createTimeMs +
                ", updateTimeMs=" + updateTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateUser='" + updateUser + '\'' +
                ", subjectId='" + userId + '\'' +
                ", email='" + email + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", comments='" + comments + '\'' +
                ", loginCount=" + loginCount +
                ", loginFailures=" + loginFailures +
                ", lastLoginMs=" + lastLoginMs +
                ", reactivatedMs=" + reactivatedMs +
                ", forcePasswordChange=" + forcePasswordChange +
                ", neverExpires=" + neverExpires +
                ", enabled=" + enabled +
                ", inactive=" + inactive +
                ", locked=" + locked +
                ", processingAccount=" + processingAccount +
                '}';
    }
}
