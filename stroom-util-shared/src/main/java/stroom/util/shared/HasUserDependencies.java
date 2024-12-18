package stroom.util.shared;

import java.util.List;

public interface HasUserDependencies {

    /**
     * Get all known things that have a dependency on the passed userRef.
     * This method is intended to be called by another service that will ensure that
     * it filters out any items that the current user does not have VIEW permission on
     * if sending them to the UI.
     */
    List<UserDependency> getUserDependencies(final UserRef userRef);

}
