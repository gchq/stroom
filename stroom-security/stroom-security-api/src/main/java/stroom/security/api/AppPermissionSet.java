package stroom.security.api;

import stroom.security.api.AppPermissionSetImpl.EmptyAppPermissionSet;
import stroom.security.api.AppPermissionSetImpl.Operator;
import stroom.security.api.AppPermissionSetImpl.SingletonAppPermissionSet;
import stroom.security.shared.AppPermission;
import stroom.util.shared.NullSafe;

import java.util.Collection;
import java.util.Set;
import java.util.function.Predicate;

// Ideally I would have made AppPermission implement AppPermissionSet to save having to wrap
// a singleton permission, but GWT didn't like the extension of the Set iface.

/**
 * A set of permissions that are required in order to perform an action.
 * Either all {@link AppPermission}s in the set must be held or at least one of them
 * depending on the state of {@link Operator}, {@link AppPermissionSet#isAllOf()} or
 * {@link AppPermissionSet#isAtLeastOneOf()}.
 */
public interface AppPermissionSet extends Set<AppPermission> {

    /**
     * Create a {@link AppPermissionSet} where ALL the app permissions must be held.
     */
    static AppPermissionSet allOf(Collection<AppPermission> appPermissions) {
        if (NullSafe.isEmptyCollection(appPermissions)) {
            return EmptyAppPermissionSet.INSTANCE;
        } else {
            return new AppPermissionSetImpl(
                    Operator.ALL_OF,
                    NullSafe.unmodifialbeEnumSet(AppPermission.class, appPermissions));
        }
    }

    /**
     * Create a {@link AppPermissionSet} where ALL the app permissions must be held.
     */
    static AppPermissionSet allOf(AppPermission... appPermissions) {
        return allOf(NullSafe.asList(appPermissions));
    }

    /**
     * Create a {@link AppPermissionSet} where at least ONE of the app permissions
     * must be held.
     */
    static AppPermissionSet oneOf(Collection<AppPermission> appPermissions) {
        if (NullSafe.isEmptyCollection(appPermissions)) {
            return EmptyAppPermissionSet.INSTANCE;
        } else {
            return new AppPermissionSetImpl(
                    Operator.ONE_OF,
                    NullSafe.unmodifialbeEnumSet(AppPermission.class, appPermissions));
        }
    }

    /**
     * Create a {@link AppPermissionSet} where at least ONE of the app permissions
     * must be held.
     */
    static AppPermissionSet oneOf(AppPermission... appPermissions) {
        return oneOf(NullSafe.asList(appPermissions));
    }

    /**
     * Create a {@link AppPermissionSet} for a single app permission.
     */
    static AppPermissionSet of(AppPermission appPermission) {
        // Operator is irrelevant, so just use AND as that is easier to test
        if (appPermission != null) {
            return new SingletonAppPermissionSet(appPermission);
        } else {
            return EmptyAppPermissionSet.INSTANCE;
        }
    }

    /**
     * Create a {@link AppPermissionSet} for a single app permission.
     */
    static AppPermissionSet empty() {
        return EmptyAppPermissionSet.INSTANCE;
    }

    static boolean isEmpty(final AppPermissionSet appPermissions) {
        return appPermissions == null || appPermissions.isEmpty();
    }

    Operator getOperator();

    boolean isAllOf();

    boolean isAtLeastOneOf();

    @Override
    default void clear() {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }

    @Override
    default boolean remove(Object o) {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }

    @Override
    default boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }

    @Override
    default boolean removeIf(Predicate<? super AppPermission> filter) {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }

    @Override
    default boolean add(AppPermission permission) {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }

    @Override
    default boolean addAll(Collection<? extends AppPermission> c) {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }

    @Override
    default boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException("AppPermissionSet is not mutable");
    }
}
