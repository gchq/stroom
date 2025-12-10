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

package stroom.util.pipeline.scope;

import com.google.common.collect.Maps;
import com.google.inject.Key;
import com.google.inject.OutOfScopeException;
import com.google.inject.Provider;
import com.google.inject.Scope;
import com.google.inject.Scopes;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

import static com.google.common.base.Preconditions.checkState;

/**
 * Scopes a single execution of a block of code. Apply this scope with a
 * try/finally block: <pre><code>
 * <p>
 *   scope.enter();
 *   try {
 *     // explicitly seed some seed objects...
 *     scope.seed(Key.get(SomeObject.class), someObject);
 *     // create and access scoped objects
 *   } finally {
 *     scope.exit();
 *   }
 * </code></pre>
 * <p>
 * The scope can be initialized with one or more seed threadLocal by calling
 * <code>seed(key, value)</code> before the injector will be called upon to
 * provide for this key. A typical use is for a servlet filter to enter/exit the
 * scope, representing a Request Scope, and seed HttpServletRequest and
 * HttpServletResponse.  For each key inserted with seed(), you must include a
 * corresponding binding:
 * <pre><code>
 *   bind(key)
 *       .toProvider(SimpleScope.&lt;KeyClass&gt;seededKeyProvider())
 *       .in(ScopeAnnotation.class);
 * </code></pre>
 *
 * @author Jesse Wilson
 * @author Fedor Karpelevitch
 */
public class PipelineScope implements Scope {

    private static final Provider<Object> SEEDED_KEY_PROVIDER =
            () -> {
                throw new IllegalStateException("If you got here then it means that" +
                        " your code asked for scoped object which should have been" +
                        " explicitly seeded in this scope by calling" +
                        " SimpleScope.seed(), but was not.");
            };
    private final ThreadLocal<Deque<Map<Key<?>, Object>>> threadLocal = ThreadLocal.withInitial(ArrayDeque::new);

    public void enter() {
        final Deque<Map<Key<?>, Object>> deque = threadLocal.get();
        deque.offerLast(Maps.newHashMap());
    }

    public void exit() {
        final Deque<Map<Key<?>, Object>> deque = threadLocal.get();
        checkState(deque != null, "No scoping block in progress");
        final Map<Key<?>, Object> map = deque.pollLast();
        checkState(map != null, "No scoping block in progress");

//        if (queue.peek() == null) {
//            threadLocal.remove();
//        }
    }

    public <T> void seed(final Key<T> key, final T value) {
        final Map<Key<?>, Object> scopedObjects = getScopedObjectMap(key);
        checkState(!scopedObjects.containsKey(key), "A value for the key %s was " +
                        "already seeded in this scope. Old value: %s New value: %s", key,
                scopedObjects.get(key), value);
        scopedObjects.put(key, value);
    }

    public <T> void seed(final Class<T> clazz, final T value) {
        seed(Key.get(clazz), value);
    }

    public <T> Provider<T> scope(final Key<T> key, final Provider<T> unscoped) {
        return () -> {
            final Map<Key<?>, Object> scopedObjects = getScopedObjectMap(key);

            @SuppressWarnings("unchecked")
            T current = (T) scopedObjects.get(key);
            if (current == null && !scopedObjects.containsKey(key)) {
                current = unscoped.get();

                // don't remember proxies; these exist only to serve circular dependencies
                if (Scopes.isCircularProxy(current)) {
                    return current;
                }

                scopedObjects.put(key, current);
            }
            return current;
        };
    }

    private <T> Map<Key<?>, Object> getScopedObjectMap(final Key<T> key) {
        final Deque<Map<Key<?>, Object>> deque = threadLocal.get();
        if (deque == null) {
            throw new OutOfScopeException("Cannot access " + key
                    + " outside of a scoping block");
        }
        final Map<Key<?>, Object> scopedObjects = deque.peekLast();
        if (scopedObjects == null) {
            throw new OutOfScopeException("Cannot access " + key
                    + " outside of a scoping block");
        }
        return scopedObjects;
    }

    /**
     * Returns a provider that always throws exception complaining that the object
     * in question must be seeded before it can be injected.
     *
     * @return typed provider
     */
    @SuppressWarnings({"unchecked"})
    public static <T> Provider<T> seededKeyProvider() {
        return (Provider<T>) SEEDED_KEY_PROVIDER;
    }
}
