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
