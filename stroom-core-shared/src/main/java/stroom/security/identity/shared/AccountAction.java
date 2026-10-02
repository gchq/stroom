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

package stroom.security.identity.shared;

import java.util.Optional;

/**
 * A change an administrator makes to an account's state, as an intent rather than a value.
 * <p>
 * These are deliberately not expressed as fields on {@link AccountChange}. A field carries the state the
 * caller believes the account is in, so a stale one silently reverts whatever happened since the caller read
 * it - an account locked by failed logins while an edit screen was open would be unlocked by saving that
 * screen. An action carries only what the administrator asked for, so an account is only ever unlocked
 * because somebody asked to unlock it.
 * </p>
 */
public enum AccountAction {

    /**
     * Release a lock applied by repeated wrong passwords, clearing its expiry and resetting the count. The
     * count has to be reset: left at or above the lock threshold, the next failed attempt would immediately
     * re-lock the account.
     */
    UNLOCK,

    ENABLE,
    DISABLE,

    /**
     * Make an inactive account active again. Inactivity is applied by the account maintenance job when an
     * account goes unused, and cleared by a successful sign in where that is configured; this is for when
     * neither has happened and somebody needs the account back.
     */
    REACTIVATE;

    /**
     * The action that contradicts this one, so that a change cannot ask for both at once, or empty where an
     * action has no opposite.
     * <p>
     * Only enabling and disabling pair up. Locking and deactivating are not things an administrator does -
     * a lock exists to blunt repeated wrong passwords, and inactivity marks an account as unused - so
     * {@link #UNLOCK} and {@link #REACTIVATE} can only ever undo something the system did, and there is no
     * action to contradict them with.
     * </p>
     */
    public Optional<AccountAction> getOpposite() {
        switch (this) {
            case ENABLE:
                return Optional.of(DISABLE);
            case DISABLE:
                return Optional.of(ENABLE);
            default:
                return Optional.empty();
        }
    }
}
