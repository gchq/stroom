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

package stroom.security.shared;

import stroom.security.shared.AppPermissionSetImpl.EmptyAppPermissionSet;
import stroom.security.shared.AppPermissionSetImpl.SingletonAppPermissionSet;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * An immutable set of permissions that are required in order to perform an action.
 * Either all {@link AppPermission}s in the set must be held or at least one of them
 * depending on the state of {@link AppPermissionOperator}, {@link AppPermissionSet#isAllOf()} or
 * {@link AppPermissionSet#isAtLeastOneOf()}.
 */
// GWT/RestyGWT doesn't like @JsonDeserialize it seems
@JsonTypeInfo(
        defaultImpl = AppPermissionSetImpl.class,
        use = JsonTypeInfo.Id.NAME,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = AppPermissionSetImpl.class, name = "multiple"),
        @JsonSubTypes.Type(value = SingletonAppPermissionSet.class, name = "single"),
        @JsonSubTypes.Type(value = EmptyAppPermissionSet.class, name = "empty")})
public interface AppPermissionSet extends Iterable<AppPermission> {

    /**
     * Create a {@link AppPermissionSet} where ALL the app permissions must be held.
     */
    static AppPermissionSet allOf(final Collection<AppPermission> appPermissions) {
        if (NullSafe.isEmptyCollection(appPermissions)) {
            return EmptyAppPermissionSet.INSTANCE;
        } else if (appPermissions.size() == 1) {
            final AppPermission appPermission = appPermissions.iterator().next();
            return NullSafe.getOrElseGet(
                    appPermission,
                    SingletonAppPermissionSet::new,
                    AppPermissionSet::empty);
        } else {
            return new AppPermissionSetImpl(AppPermissionOperator.ALL_OF, appPermissions);
        }
    }

    /**
     * Create a {@link AppPermissionSet} where ALL the app permissions must be held.
     */
    static AppPermissionSet allOf(final AppPermission... appPermissions) {
        return allOf(NullSafe.asList(appPermissions));
    }

    /**
     * Create a {@link AppPermissionSet} where at least ONE of the app permissions
     * must be held.
     */
    static AppPermissionSet oneOf(final Collection<AppPermission> appPermissions) {
        if (NullSafe.isEmptyCollection(appPermissions)) {
            return EmptyAppPermissionSet.INSTANCE;
        } else if (appPermissions.size() == 1) {
            final AppPermission appPermission = appPermissions.iterator().next();
            return NullSafe.getOrElseGet(
                    appPermission,
                    SingletonAppPermissionSet::new,
                    AppPermissionSet::empty);
        } else {
            return new AppPermissionSetImpl(AppPermissionOperator.ONE_OF, appPermissions);
        }
    }

    /**
     * Create a {@link AppPermissionSet} where at least ONE of the app permissions
     * must be held.
     */
    static AppPermissionSet oneOf(final AppPermission... appPermissions) {
        return oneOf(NullSafe.asList(appPermissions));
    }

    /**
     * Create a {@link AppPermissionSet} for a single app permission.
     */
    static AppPermissionSet of(final AppPermission appPermission) {
        // Operator is irrelevant, so just use AND as that is easier to test
        if (appPermission != null) {
            return new SingletonAppPermissionSet(appPermission);
        } else {
            return EmptyAppPermissionSet.INSTANCE;
        }
    }

    /**
     * Create an empty {@link AppPermissionSet}.
     */
    static AppPermissionSet empty() {
        return EmptyAppPermissionSet.INSTANCE;
    }

    static boolean isEmpty(final AppPermissionSet appPermissions) {
        return appPermissions == null || appPermissions.isEmpty();
    }

    Set<AppPermission> asSet();

//    AppPermissionOperator getOperator();

    @JsonIgnore
    boolean isAllOf();

    @JsonIgnore
    boolean isAtLeastOneOf();

    int size();

    @JsonIgnore
    boolean isEmpty();

    boolean contains(Object o);

    boolean containsAll(Collection<?> c);

    Stream<AppPermission> stream();

    /**
     * This is only here to get GWT to play ball. We seem to need it
     * because of {@link Enum#name()} on {@link AppPermission}.
     */
    default String name() {
        return stream()
                .map(AppPermission::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    /**
     * @param permissionsHeld The permissions that are held and that need to be checked
     *                        against this set of required permissions.
     * @return True if the required permissions in this {@link AppPermissionSet} exist in
     * permissionsHeld, either all of them or at least one of them, depending on
     * {@link AppPermissionOperator}.
     */
    default boolean check(final Set<AppPermission> permissionsHeld) {
        if (isEmpty()) {
            return true;
        } else if (NullSafe.isEmptyCollection(permissionsHeld)) {
            // At least one required, none held
            return false;
        } else if (isAllOf()) {
            return permissionsHeld.containsAll(asSet());
        } else {
            //noinspection Convert2MethodRef // Makes it clearer
            return stream()
                    .anyMatch(requiredPerm ->
                            permissionsHeld.contains(requiredPerm));
        }
    }


    // --------------------------------------------------------------------------------


    enum AppPermissionOperator {
        /**
         * All required perms must be held.
         * I.e. permA AND permB
         */
        ALL_OF(true, false),
        /**
         * At least one of the required perms must be held.
         * I.e. permA OR permB
         */
        ONE_OF(false, true),
        /**
         * Only one required perm must be held, e.g. permA
         */
        SINGLE(true, true),
        /**
         * No perms need to be held.
         */
        EMPTY(false, false),
        ;

        private final boolean isAllOf;
        private final boolean isOneOf;

        AppPermissionOperator(final boolean isAllOf, final boolean isOneOf) {
            this.isAllOf = isAllOf;
            this.isOneOf = isOneOf;
        }

        @JsonIgnore
        boolean isAllOf() {
            return isAllOf;
        }

        @JsonIgnore
        boolean isAtLeastOneOf() {
            return isOneOf;
        }
    }
}
