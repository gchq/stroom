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

import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private int failureCount;
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
    private Long failureLockedMs;
    @JsonProperty
    private Long failureLockedUntilMs;

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
                   @JsonProperty("failureCount") final int failureCount,
                   @JsonProperty("lastLoginMs") final Long lastLoginMs,
                   @JsonProperty("reactivatedMs") final Long reactivatedMs,
                   @JsonProperty("forcePasswordChange") final boolean forcePasswordChange,
                   @JsonProperty("neverExpires") final boolean neverExpires,
                   @JsonProperty("enabled") final boolean enabled,
                   @JsonProperty("inactive") final boolean inactive,
                   @JsonProperty("failureLockedMs") final Long failureLockedMs,
                   @JsonProperty("failureLockedUntilMs") final Long failureLockedUntilMs) {
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
        this.failureCount = failureCount;
        this.lastLoginMs = lastLoginMs;
        this.reactivatedMs = reactivatedMs;
        this.forcePasswordChange = forcePasswordChange;
        this.neverExpires = neverExpires;
        this.enabled = enabled;
        this.inactive = inactive;
        this.failureLockedMs = failureLockedMs;
        this.failureLockedUntilMs = failureLockedUntilMs;
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

    public int getFailureCount() {
        return failureCount;
    }

    public void setFailureCount(final int failureCount) {
        this.failureCount = failureCount;
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

    /**
     * When the lockout was applied, or null if the account is not locked. This is the single stored value:
     * an account is locked exactly when there is a time at which it was locked.
     */
    public Long getFailureLockedMs() {
        return failureLockedMs;
    }

    public void setFailureLockedMs(final Long failureLockedMs) {
        this.failureLockedMs = failureLockedMs;
    }

    public Long getFailureLockedUntilMs() {
        return failureLockedUntilMs;
    }

    public void setFailureLockedUntilMs(final Long failureLockedUntilMs) {
        this.failureLockedUntilMs = failureLockedUntilMs;
    }

    /**
     * Whether repeated wrong passwords are currently barring this account, as distinct from the stored flag.
     * <p>
     * A lock is released lazily, on the next sign in attempt, so the flag outlives the lock itself. Reading
     * the flag alone reports an account as locked when it would in fact be admitted straight away. Every
     * caller wants this rather than the flag, which is why this keeps the plain name.
     * </p>
     * <p>
     * Note that {@link #getFailureLockedUntilMs()} is not a stored value. The account table records only
     * when a lock was applied, and the data access layer adds the configured lock duration when it builds
     * this object, so that changing that duration governs locks already in force. A null end time means the
     * lock does not lapse, which is how a duration of zero arrives here - so it has to be read together
     * with {@link #getFailureLockedMs()}, which is what says whether there is a lock at all.
     * </p>
     */
    @JsonIgnore
    public boolean isLocked() {
        return failureLockedMs != null
               && (failureLockedUntilMs == null || failureLockedUntilMs > System.currentTimeMillis());
    }

    /**
     * @return The users full name, e.g. "Joe Bloggs".
     */
    @JsonIgnore
    public String getFullName() {
        if (firstName == null && lastName == null) {
            return null;
        }
        // Only the parts we have. Joining unconditionally produced " Smith" or "John ", which then became
        // the stroom user's stored full name and stopped later comparisons matching.
        return Stream.of(firstName, lastName)
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(" "));
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
                ", failureCount=" + failureCount +
                ", lastLoginMs=" + lastLoginMs +
                ", reactivatedMs=" + reactivatedMs +
                ", forcePasswordChange=" + forcePasswordChange +
                ", neverExpires=" + neverExpires +
                ", enabled=" + enabled +
                ", inactive=" + inactive +
                ", failureLockedMs=" + failureLockedMs +
                ", failureLockedUntilMs=" + failureLockedUntilMs +
                '}';
    }
}
