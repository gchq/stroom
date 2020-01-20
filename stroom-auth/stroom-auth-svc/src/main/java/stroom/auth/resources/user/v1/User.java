/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.auth.resources.user.v1;

import com.google.common.base.Strings;
import org.apache.commons.lang3.tuple.Pair;
import org.mindrot.jbcrypt.BCrypt;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;

/**
 * This POJO binds to the response from the database, and to the JSON.
 * <p>
 * The names are database-style to reduce mapping code. This looks weird in Java but it's sensible for the database
 * and it's sensible for the json.
 */
public final class User {
    @Nullable
    private Integer id;
    @Nullable
    private String firstName;
    @Nullable
    private String lastName;
    @Nullable
    private String comments;
    @Nullable
    private String email;
    @Nullable
    private String state;
    @Nullable
    private String password;
    @Nullable
    private String passwordHash;
    @Nullable
    private Integer loginFailures;
    @Nullable
    private Integer loginCount;
    @Nullable
    private String lastLogin;
    @Nullable
    private String updatedOn;
    @Nullable
    private String updatedByUser;
    @Nullable
    private String createdOn;
    @Nullable
    private String createdByUser;
    @Nullable
    private boolean neverExpires;
    @Nullable
    private boolean forcePasswordChange;
    @Nullable
    private String reactivatedDate;

    public User() {
    }

    public User(@NotNull String email, @NotNull String password) {
        this.email = email;
        this.password = password;
    }

    @Nullable
    public final Integer getId() {
        return this.id;
    }

    public final void setId(@Nullable Integer id) {
        this.id = id;
    }

    @Nullable
    public final String getFirstName() {
        return this.firstName;
    }

    public final void setFirstName(@Nullable String firstName) {
        this.firstName = firstName;
    }

    @Nullable
    public final String getLastName() {
        return this.lastName;
    }

    public final void setLastName(@Nullable String lastName) {
        this.lastName = lastName;
    }

    @Nullable
    public final String getComments() {
        return this.comments;
    }

    public final void setComments(@Nullable String comments) {
        this.comments = comments;
    }

    @Nullable
    public final String getEmail() {
        return this.email;
    }

    public final void setEmail(@Nullable String email) {
        this.email = email;
    }

    @Nullable
    public final String getState() {
        return this.state;
    }

    public final void setState(@Nullable String state) {
        this.state = state;
    }

    @Nullable
    public final String getPassword() {
        return this.password;
    }

    public final void setPassword(@Nullable String password) {
        this.password = password;
    }

    @Nullable
    public final String getPasswordHash() {
        return this.passwordHash;
    }

    public final void setPasswordHash(@Nullable String passwordHash) {
        this.passwordHash = passwordHash;
    }

    @Nullable
    public final Integer getLoginFailures() {
        return this.loginFailures;
    }

    public final void setLoginFailures(@Nullable Integer loginFailures) {
        this.loginFailures = loginFailures;
    }

    @Nullable
    public final Integer getLoginCount() {
        return this.loginCount;
    }

    public final void setLoginCount(@Nullable Integer loginCount) {
        this.loginCount = loginCount;
    }

    @Nullable
    public final String getLastLogin() {
        return this.lastLogin;
    }

    public final void setLastLogin(@Nullable String lastLogin) {
        this.lastLogin = lastLogin;
    }

    @Nullable
    public final String getUpdatedOn() {
        return this.updatedOn;
    }

    public final void setUpdatedOn(@Nullable String updatedOn) {
        this.updatedOn = updatedOn;
    }

    @Nullable
    public final String getUpdatedByUser() {
        return this.updatedByUser;
    }

    public final void setUpdatedByUser(@Nullable String updatedByUser) {
        this.updatedByUser = updatedByUser;
    }

    @Nullable
    public final String getCreatedOn() {
        return this.createdOn;
    }

    public final void setCreatedOn(@Nullable String createdOn) {
        this.createdOn = createdOn;
    }

    @Nullable
    public final String getCreatedByUser() {
        return this.createdByUser;
    }

    public final void setCreatedByUser(@Nullable String createdByUser) {
        this.createdByUser = createdByUser;
    }

    @Nullable
    public boolean getNeverExpires() {
        return neverExpires;
    }

    public void setNeverExpires(@Nullable boolean neverExpires) {
        this.neverExpires = neverExpires;
    }

    public boolean isForcePasswordChange() {
        return forcePasswordChange;
    }

    public void setForcePasswordChange(boolean forcePasswordChange) {
        this.forcePasswordChange = forcePasswordChange;
    }

    public void setReactivatedDate(String reactivatedDate) {
        this.reactivatedDate = reactivatedDate;
    }

    public String getReactivatedDate() {
        return this.reactivatedDate;
    }

    /**
     * This is not the same as getPasswordHash(). That's a getting for the model property,
     * but this method actually creates a new hash.
     */
    public String generatePasswordHash() {
        return BCrypt.hashpw(this.password, BCrypt.gensalt());
    }

    public static Pair<Boolean, String> isValidForCreate(User user) {
        ArrayList<UserValidationError> validationErrors = new ArrayList<>();

        if (user == null) {
            validationErrors.add(UserValidationError.NO_USER);
        } else {
            if (Strings.isNullOrEmpty(user.getEmail())) {
                validationErrors.add(UserValidationError.NO_NAME);
            }

            if (Strings.isNullOrEmpty(user.getPassword())) {
                validationErrors.add(UserValidationError.NO_PASSWORD);
            }
        }

        String validationMessages = validationErrors.stream()
                .map(UserValidationError::getMessage)
                .reduce((validationMessage1, validationMessage2) -> validationMessage1 + validationMessage2).orElse("");
        boolean isValid = validationErrors.size() == 0;
        return Pair.of(isValid, validationMessages);
    }


    public enum UserState {
        ENABLED("enabled"),
        INACTIVE("inactive"),
        DISABLED("disabled"),
        LOCKED("locked");

        private String stateText;

        UserState(String stateText) {
            this.stateText = stateText;
        }

        public String getStateText() {
            return this.stateText;
        }
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", comments='" + comments + '\'' +
                ", email='" + email + '\'' +
                ", state='" + state + '\'' +
                ", password='" + password + '\'' +
                ", passwordHash='" + passwordHash + '\'' +
                ", loginFailures=" + loginFailures +
                ", loginCount=" + loginCount +
                ", lastLogin='" + lastLogin + '\'' +
                ", updatedOn='" + updatedOn + '\'' +
                ", updatedByUser='" + updatedByUser + '\'' +
                ", createdOn='" + createdOn + '\'' +
                ", createdByUser='" + createdByUser + '\'' +
                ", neverExpires='" + neverExpires + '\'' +
                ", forcePasswordChange='" + forcePasswordChange + '\'' +
                ", reactivated_date='" + reactivatedDate + '\'' +
                '}';
    }
}
