/*
 * Copyright 2016 Crown Copyright
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

package stroom.security;

import stroom.entity.shared.HasUuid;
import stroom.entity.shared.NamedEntity;
import stroom.entity.shared.SQLNameConstants;

/**
 * <p>
 * Used to represent a user or machine in the Stroom server.
 * </p>
 */
public class User extends NamedEntity implements HasUuid {
    public static final String ENTITY_TYPE = "User";
    private static final long serialVersionUID = -2415531358356094596L;
    private String uuid;

    private String passwordHash;
    /**
     * When you change or set your password how long before it needs changing
     * again If you never set a password (say you just login with certs) then
     * this maybe null
     */
    private Long passwordExpiryMs;
    /**
     * The time you last logged in
     */
    private Long lastLoginMs;
    /**
     * The time from when this login is valid.
     * <p>
     * 1) When created it is the create time 2) When last logged in is the last
     * login time 3) When account re-enabled the current time
     */
    private Long loginValidMs;
    /**
     * If you don't login will this account get disabled?
     */
    private boolean loginExpiry = true;
    private byte pstatus = UserStatus.ENABLED.getPrimitiveValue();
    /**
     * Reset when you OK login
     */
    private int currentLoginFailures;
    /**
     * Total number of login failures
     */
    private int totalLoginFailures;
    /**
     * Is this user a user group or a regular user? TODO : At some point split
     * out logon and credential details into another entity.
     */
    private boolean group;

    //    @Id
//    @GeneratedValue(generator = "uuid2")
//    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @Override
    public String getUuid() {
        return uuid;
    }

    public void setUuid(final String uuid) {
        this.uuid = uuid;
    }

    /**
     * @return getter
     */
    public String getPasswordHash() {
        return passwordHash;
    }

    /**
     * @param passwordHash setter
     */
    public void setPasswordHash(final String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /**
     * @return getter
     */
    public Long getPasswordExpiryMs() {
        return passwordExpiryMs;
    }

    /**
     * @param passwordExpiryMs setter
     */
    public void setPasswordExpiryMs(final Long passwordExpiryMs) {
        this.passwordExpiryMs = passwordExpiryMs;
    }

    public boolean isLoginExpiry() {
        return loginExpiry;
    }

    public void setLoginExpiry(final boolean loginExpiry) {
        this.loginExpiry = loginExpiry;
    }

    /**
     * @return getter
     */
    public Long getLastLoginMs() {
        return lastLoginMs;
    }

    /**
     * @param lastLoginMs setter
     */
    public void setLastLoginMs(final Long lastLoginMs) {
        this.lastLoginMs = lastLoginMs;
    }

    /**
     * @return getter
     */
    public Long getLoginValidMs() {
        return loginValidMs;
    }

    public void setLoginValidMs(final Long loginValidMs) {
        this.loginValidMs = loginValidMs;
    }

    /**
     * @return getter
     */
    public byte getPstatus() {
        return pstatus;
    }

    /**
     * @param pstatus setter
     */
    public void setPstatus(final byte pstatus) {
        this.pstatus = pstatus;
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(final boolean group) {
        this.group = group;
    }

    /**
     * @return getter
     */
    public UserStatus getStatus() {
        return UserStatus.PRIMITIVE_VALUE_CONVERTER.fromPrimitiveValue(pstatus);
    }

    /**
     * @param status setter
     */
    private void setStatus(final UserStatus status) {
        this.pstatus = status.getPrimitiveValue();
    }

    /**
     * Utility method that ensures when updating the status back to enabled the
     * last login is reset.
     *
     * @param newStatus
     */
    public void updateStatus(final UserStatus newStatus) {
        // We going enabled?
        if (!UserStatus.ENABLED.equals(getStatus()) && newStatus.equals(UserStatus.ENABLED)) {
            final long nowMs = System.currentTimeMillis();
            setCurrentLoginFailures(0);
            setLoginValidMs(nowMs);
        }

        setStatus(newStatus);
    }

    public void updateValidLogin() {
        updateValidLogin(System.currentTimeMillis());
    }

    public void updateValidLogin(final long nowMs) {
        setLastLoginMs(nowMs);
        setLoginValidMs(nowMs);
        setCurrentLoginFailures(0);
    }

    /**
     * Derived Field
     *
     * @return
     */
    public boolean isStatusEnabled() {
        return UserStatus.ENABLED.equals(getStatus());
    }

    public void setStatusEnabled(final boolean enabled) {
        if (enabled) {
            if (!isStatusEnabled()) {
                updateStatus(UserStatus.ENABLED);
            }
        } else {
            if (isStatusEnabled()) {
                updateStatus(UserStatus.DISABLED);
            }
        }
    }

    /**
     * @return getter
     */
    public int getCurrentLoginFailures() {
        return currentLoginFailures;
    }

    /**
     * @param currentLoginFailures setter
     */
    public void setCurrentLoginFailures(final int currentLoginFailures) {
        this.currentLoginFailures = currentLoginFailures;
    }

    /**
     * @return getter
     */
    public int getTotalLoginFailures() {
        return totalLoginFailures;
    }

    /**
     * @param totalLoginFailures setter
     */
    public void setTotalLoginFailures(final int totalLoginFailures) {
        this.totalLoginFailures = totalLoginFailures;
    }

    @Override
    public final String getType() {
        return ENTITY_TYPE;
    }
}
