package stroom.proxy.app.security;

import stroom.security.api.UserIdentity;

import java.util.ArrayDeque;
import java.util.Deque;

final class ProxyCurrentUserState {

    // Seems unlikely that we would need more than 4 identities on the stack at any time, and if we do,
    // the deque will grow anyway.
    private static final ThreadLocal<Deque<State>> THREAD_LOCAL = ThreadLocal.withInitial(() ->
            new ArrayDeque<>(4));

    private ProxyCurrentUserState() {
        // Utility.
    }

    static void push(final UserIdentity userIdentity) {
        final Deque<State> deque = THREAD_LOCAL.get();
        final State state = deque.peek();
        if (state != null) {
            deque.push(new State(userIdentity, state.elevatePermissions()));
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


    // --------------------------------------------------------------------------------


    private record State(UserIdentity userIdentity, boolean elevatePermissions) {

    }
}
