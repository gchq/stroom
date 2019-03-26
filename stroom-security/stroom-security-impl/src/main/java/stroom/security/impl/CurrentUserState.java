package stroom.security.impl;

import stroom.security.shared.UserRef;
import stroom.security.shared.UserToken;

import java.util.ArrayDeque;
import java.util.Deque;

final class CurrentUserState {
    private static final ThreadLocal<Deque<State>> THREAD_LOCAL = ThreadLocal.withInitial(ArrayDeque::new);

    private CurrentUserState() {
        // Utility.
    }

    static void push(final UserToken userToken, final UserRef userRef) {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(userToken, userRef, state.isElevatePermissions()));
        } else {
            deque.push(new State(userToken, userRef, false));
        }
    }

    static void pop() {
        final Deque<State> deque = THREAD_LOCAL.get();
        deque.pop();
    }

    static void elevatePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(state.getUserToken(), state.getUserRef(), true));
        } else {
            throw new IllegalStateException("Attempt to elevate permissions without a current user");
        }
    }

    static void restorePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        deque.pop();
    }


    static UserToken currentUserToken() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            return state.getUserToken();
        }
        return null;
    }

    static UserRef currentUserRef() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            return state.getUserRef();
        }
        return null;
    }

    static boolean isElevatePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        return state != null && state.isElevatePermissions();
    }

    private static class State {
        private final UserToken userToken;
        private final UserRef userRef;
        private final boolean elevatePermissions;

        private State(final UserToken userToken, final UserRef userRef, final boolean elevatePermissions) {
            this.userToken = userToken;
            this.userRef = userRef;
            this.elevatePermissions = elevatePermissions;
        }

        UserToken getUserToken() {
            return userToken;
        }

        UserRef getUserRef() {
            return userRef;
        }

        boolean isElevatePermissions() {
            return elevatePermissions;
        }
    }
}
