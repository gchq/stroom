package stroom.security;

import stroom.security.shared.UserRef;

import java.util.Deque;
import java.util.LinkedList;

public final class CurrentUserState {
    private static final ThreadLocal<Deque<State>> THREAD_LOCAL = InheritableThreadLocal.withInitial(LinkedList::new);

    private CurrentUserState() {
        // Utility.
    }

    static void pushUserRef(final UserRef userRef) {
        final Deque<State> deque = THREAD_LOCAL.get();

        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(userRef, state.isElevatePermissions()));
        } else {
            deque.push(new State(userRef, false));
        }
    }

    static UserRef popUserRef() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.pop();
        return state.getUserRef();
    }

    static UserRef currentUserRef() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            return state.getUserRef();
        }
        return null;
    }

    static void elevatePermissions() {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(state.getUserRef(), true));
        } else {
            deque.push(new State(null, true));
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
        private final UserRef userRef;
        private final boolean elevatePermissions;

        public State(final UserRef userRef, final boolean elevatePermissions) {
            this.userRef = userRef;
            this.elevatePermissions = elevatePermissions;
        }

        public UserRef getUserRef() {
            return userRef;
        }

        public boolean isElevatePermissions() {
            return elevatePermissions;
        }
    }
}
