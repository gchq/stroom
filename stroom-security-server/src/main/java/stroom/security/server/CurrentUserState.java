package stroom.security.server;

import stroom.security.shared.UserIdentity;
import stroom.util.thread.Link;

final class CurrentUserState {
    private static final ThreadLocal<Link<State>> THREAD_LOCAL = new InheritableThreadLocal<>();

    private CurrentUserState() {
        // Utility.
    }

    static void push(final UserIdentity userIdentity) {
        final Link<State> link = THREAD_LOCAL.get();

        boolean elevatePermissions = false;
        if (link != null) {
            elevatePermissions = link.getObject().isElevatePermissions();
        }

        THREAD_LOCAL.set(new Link<>(new State(userIdentity, elevatePermissions), link));
    }

    static UserIdentity pop() {
        final Link<State> link = THREAD_LOCAL.get();
        THREAD_LOCAL.set(link.getParent());
        return link.getObject().getUserIdentity();
    }

    static UserIdentity current() {
        final Link<State> link = THREAD_LOCAL.get();
        if (link != null) {
            return link.getObject().getUserIdentity();
        }
        return null;
    }

    static void elevatePermissions() {
        final Link<State> link = THREAD_LOCAL.get();

        UserIdentity token = null;
        if (link != null) {
            token = link.getObject().getUserIdentity();
        }

        THREAD_LOCAL.set(new Link<>(new State(token, true), link));
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
        private final UserIdentity userIdentity;
        private final boolean elevatePermissions;

        public State(final UserIdentity userIdentity, final boolean elevatePermissions) {
            this.userIdentity = userIdentity;
            this.elevatePermissions = elevatePermissions;
        }

        UserIdentity getUserIdentity() {
            return userIdentity;
        }

        boolean isElevatePermissions() {
            return elevatePermissions;
        }
    }
}
