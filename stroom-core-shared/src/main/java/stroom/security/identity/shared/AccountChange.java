/*
 * Copyright 2016-2026 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * What an administrator wants changed about an account, rather than what they believe the account now looks
 * like. Anything absent from a change is not written, so a change can only ever do what was asked of it.
 * <p>
 * This is why the account is not sent back as a whole object. Doing that makes every column an input, so a
 * screen opened before some other writer ran will quietly undo it on save - the login path locks accounts,
 * counts failures and stamps login times, none of which an administrator edits but all of which a
 * round-tripped account would carry stale values for. Sending only the change removes the possibility rather
 * than guarding against it, which is also why applying one does not test the account's version: there is no
 * longer anything for a version to protect it from.
 * </p>
 * <p>
 * A null field means "leave this alone"; an empty string is a real value and will clear the column. State is
 * carried separately as {@link AccountAction}s - see that type for why.
 * </p>
 */
@JsonInclude(Include.NON_NULL)
public class AccountChange {

    @JsonProperty
    private final String userId;
    @JsonProperty
    private final String email;
    @JsonProperty
    private final String firstName;
    @JsonProperty
    private final String lastName;
    @JsonProperty
    private final String comments;
    @JsonProperty
    private final Boolean neverExpires;
    @JsonProperty
    private final Boolean forcePasswordChange;
    @JsonProperty
    private final String password;
    @JsonProperty
    private final String confirmPassword;
    @JsonProperty
    private final Set<AccountAction> actions;

    @JsonCreator
    public AccountChange(@JsonProperty("userId") final String userId,
                         @JsonProperty("email") final String email,
                         @JsonProperty("firstName") final String firstName,
                         @JsonProperty("lastName") final String lastName,
                         @JsonProperty("comments") final String comments,
                         @JsonProperty("neverExpires") final Boolean neverExpires,
                         @JsonProperty("forcePasswordChange") final Boolean forcePasswordChange,
                         @JsonProperty("password") final String password,
                         @JsonProperty("confirmPassword") final String confirmPassword,
                         @JsonProperty("actions") final Set<AccountAction> actions) {
        this.userId = userId;
        this.email = email;
        this.firstName = firstName;
        this.lastName = lastName;
        this.comments = comments;
        this.neverExpires = neverExpires;
        this.forcePasswordChange = forcePasswordChange;
        this.password = password;
        this.confirmPassword = confirmPassword;
        this.actions = actions;
    }

    public String getUserId() {
        return userId;
    }

    public String getEmail() {
        return email;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getComments() {
        return comments;
    }

    public Boolean getNeverExpires() {
        return neverExpires;
    }

    public Boolean getForcePasswordChange() {
        return forcePasswordChange;
    }

    public String getPassword() {
        return password;
    }

    public String getConfirmPassword() {
        return confirmPassword;
    }

    public Set<AccountAction> getActions() {
        return actions == null
                ? Collections.emptySet()
                : actions;
    }

    public boolean hasAction(final AccountAction action) {
        return getActions().contains(action);
    }

    public static Builder builder() {
        return new Builder();
    }


    // --------------------------------------------------------------------------------


    public static final class Builder {

        private String userId;
        private String email;
        private String firstName;
        private String lastName;
        private String comments;
        private Boolean neverExpires;
        private Boolean forcePasswordChange;
        private String password;
        private String confirmPassword;
        private final Set<AccountAction> actions = new HashSet<>();

        private Builder() {
        }

        public Builder userId(final String userId) {
            this.userId = userId;
            return this;
        }

        public Builder email(final String email) {
            this.email = email;
            return this;
        }

        public Builder firstName(final String firstName) {
            this.firstName = firstName;
            return this;
        }

        public Builder lastName(final String lastName) {
            this.lastName = lastName;
            return this;
        }

        public Builder comments(final String comments) {
            this.comments = comments;
            return this;
        }

        public Builder neverExpires(final Boolean neverExpires) {
            this.neverExpires = neverExpires;
            return this;
        }

        public Builder forcePasswordChange(final Boolean forcePasswordChange) {
            this.forcePasswordChange = forcePasswordChange;
            return this;
        }

        public Builder password(final String password, final String confirmPassword) {
            this.password = password;
            this.confirmPassword = confirmPassword;
            return this;
        }

        public Builder action(final AccountAction action) {
            actions.add(action);
            return this;
        }

        /**
         * Adds the action for a state the administrator has changed, or nothing at all if they have not
         * touched it. This is what keeps an untouched control from becoming an instruction.
         */
        public Builder actionIfChanged(final boolean nowSet,
                                       final boolean wasSet,
                                       final AccountAction whenSet,
                                       final AccountAction whenCleared) {
            if (nowSet != wasSet) {
                actions.add(nowSet
                        ? whenSet
                        : whenCleared);
            }
            return this;
        }

        /**
         * Adds a value only if it differs from what the account already holds, so a change carries the edits
         * that were actually made.
         */
        public Builder valueIfChanged(final String now, final String was, final ValueSetter setter) {
            if (now == null
                    ? was != null
                    : !now.equals(was)) {
                setter.set(this, now);
            }
            return this;
        }

        public AccountChange build() {
            return new AccountChange(
                    userId,
                    email,
                    firstName,
                    lastName,
                    comments,
                    neverExpires,
                    forcePasswordChange,
                    password,
                    confirmPassword,
                    actions.isEmpty()
                            ? null
                            : actions);
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface ValueSetter {

        void set(Builder builder, String value);
    }
}
