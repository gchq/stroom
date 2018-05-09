package stroom.security.server;

import stroom.security.shared.UserRef;
import stroom.util.thread.Link;

final class CurrentUserState {
    private static final ThreadLocal<Link<State>> THREAD_LOCAL = new InheritableThreadLocal<>();

    private CurrentUserState() {
        // Utility.
    }

    static void pushUserRef(final UserRef userRef) {
        final Link<State> link = THREAD_LOCAL.get();

        boolean elevatePermissions = false;
        if (link != null) {
            elevatePermissions = link.getObject().isElevatePermissions();
        }

        THREAD_LOCAL.set(new Link<>(new State(userRef, elevatePermissions), link));
    }

    static UserRef popUserRef() {
        final Link<State> link = THREAD_LOCAL.get();
        THREAD_LOCAL.set(link.getParent());
        return link.getObject().getUserRef();
    }

    static UserRef currentUserRef() {
        final Link<State> link = THREAD_LOCAL.get();
        if (link != null) {
            return link.getObject().getUserRef();
        }
        return null;
    }

    static void elevatePermissions() {
        final Link<State> link = THREAD_LOCAL.get();

        UserRef userRef = null;
        if (link != null) {
            userRef = link.getObject().getUserRef();
        }

        THREAD_LOCAL.set(new Link<>(new State(userRef, true), link));
    }

    static void restorePermissions() {
        final Link<State> link = THREAD_LOCAL.get();
        THREAD_LOCAL.set(link.getParent());
    }

    static boolean isElevatePermissions() {
        final Link<State> link = THREAD_LOCAL.get();
        return link != null && link.getObject().isElevatePermissions();
    }

    private static class State {
        private final UserRef userRef;
        private final boolean elevatePermissions;

        public State(final UserRef userRef, final boolean elevatePermissions) {
            this.userRef = userRef;
            this.elevatePermissions = elevatePermissions;
        }

        public UserRef getUserRef() {
            return userRef;
        }

        boolean isElevatePermissions() {
            return elevatePermissions;
        }
    }
}
