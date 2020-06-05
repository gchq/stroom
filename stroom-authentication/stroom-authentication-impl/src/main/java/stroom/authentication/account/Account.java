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

package stroom.authentication.account;

/**
 * This POJO binds to the response from the database, and to the JSON.
 * <p>
 * The names are database-style to reduce mapping code. This looks weird in Java but it's sensible for the database
 * and it's sensible for the json.
 */
public class Account {
    private Integer id;
    private Integer version;
    private Long createTimeMs;
    private Long updateTimeMs;
    private String createUser;
    private String updateUser;
    private String email;
    private String firstName;
    private String lastName;
    private String comments;
    private int loginCount;
    private int loginFailures;
    private Long lastLoginMs;
    private Long reactivatedMs;
    private boolean forcePasswordChange;
    private boolean neverExpires;
    private boolean enabled;
    private boolean inactive;
    private boolean locked;
    private boolean processingAccount;

//    public Account() {
//    }
//
//    public Account(@NotNull String email, @NotNull String password) {
//        this.email = email;
//        this.password = password;
//    }
//
//    public static Pair<Boolean, String> isValidForCreate(Account account) {
//        ArrayList<AccountValidationError> validationErrors = new ArrayList<>();
//
//        if (account == null) {
//            validationErrors.add(AccountValidationError.NO_USER);
//        } else {
//            if (Strings.isNullOrEmpty(account.getEmail())) {
//                validationErrors.add(AccountValidationError.NO_NAME);
//            }
//
//            if (Strings.isNullOrEmpty(account.getPassword())) {
//                validationErrors.add(AccountValidationError.NO_PASSWORD);
//            }
//        }
//
//        String validationMessages = validationErrors.stream()
//                .map(AccountValidationError::getMessage)
//                .reduce((validationMessage1, validationMessage2) -> validationMessage1 + validationMessage2).orElse("");
//        boolean isValid = validationErrors.size() == 0;
//        return Pair.of(isValid, validationMessages);
//    }


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

    @Override
    public String toString() {
        return "Account{" +
                "id=" + id +
                ", version=" + version +
                ", createTimeMs=" + createTimeMs +
                ", updateTimeMs=" + updateTimeMs +
                ", createUser='" + createUser + '\'' +
                ", updateUser='" + updateUser + '\'' +
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
