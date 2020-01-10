package stroom.security.impl;

import stroom.security.api.UserIdentity;

import java.util.ArrayDeque;
import java.util.Deque;

final class CurrentUserState {
    private static final ThreadLocal<Deque<State>> THREAD_LOCAL = ThreadLocal.withInitial(ArrayDeque::new);

    private CurrentUserState() {
        // Utility.
    }

    static void push(final UserIdentity userIdentity) {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(userIdentity, state.isElevatePermissions()));
        } else {
            deque.push(new State(userIdentity, false));
        }
    }

    static void pop() {
        final Deque<State> deque = THREAD_LOCAL.get();
        deque.pop();
    }

    static UserIdentity current() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            return state.userIdentity;
        }
        return null;
    }

    static void elevatePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(state.getUserIdentity(), true));
        } else {
            throw new IllegalStateException("Attempt to elevate permissions without a current user");
        }
    }

    static void restorePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        deque.pop();
    }

    static boolean isElevatePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        return state != null && state.isElevatePermissions();
    }

    private static class State {
        private final UserIdentity userIdentity;
        private final boolean elevatePermissions;

        private State(final UserIdentity userIdentity, final boolean elevatePermissions) {
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
