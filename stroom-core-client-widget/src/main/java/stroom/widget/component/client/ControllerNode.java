/*
 * Copyright 2026 Crown Copyright
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

package stroom.widget.component.client;

import com.google.web.bindery.event.shared.HandlerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// A place in the ownership tree: what a controller owns and what it provides to the controllers
/// below it. Disposing a node releases everything it owns, so nothing a controller registers or
/// creates outlives it.
///
/// A node owns:
///
/// - child nodes ([#adopt(ControllerNode)]), the controllers it shows;
/// - registrations ([#add(HandlerRegistration)]), e.g. event bus handlers;
/// - clean-ups ([#onDispose(Runnable)]), e.g. stopping a timer.
///
/// Context values provided on a node ([#provide(ContextKey, ContextValue)]) are seen by every node
/// below it, through [#lookup(ContextKey)] and [#watch(ContextKey, Consumer)]. A node is
/// *connected* when it is a root ([#root(String)]) or its owner is connected; watches report a
/// value only once their node is connected, so a controller can watch in its constructor, before
/// it is owned.
///
/// The UI runs on one thread, so nodes are not thread safe.
public final class ControllerNode {

    private final String name;
    private final boolean root;
    private final List<ControllerNode> children = new ArrayList<>();
    private final List<HandlerRegistration> registrations = new ArrayList<>();
    private final List<Runnable> cleanUps = new ArrayList<>();
    private final List<Watch<?>> watches = new ArrayList<>();
    private final Map<ContextKey<?>, ContextValue<?>> provided = new HashMap<>();
    private ControllerNode parent;
    private boolean shared;
    private boolean disposing;
    private boolean disposed;
    // The ControllerChecks generation it is counted as unowned in, if it is
    int countedUnowned = ControllerChecks.NOT_COUNTED;

    /// Makes a node that something must own (see [#adopt(ControllerNode)]). Until it is owned, it
    /// is counted as unowned by [ControllerChecks].
    ///
    /// @param name The controller's name, for diagnostics.
    public ControllerNode(final String name) {
        this(name, false);
        ControllerChecks.unowned(this);
    }

    private ControllerNode(final String name, final boolean root) {
        this.name = Objects.requireNonNull(name, "name");
        this.root = root;
    }

    /// Makes a root: a node that nothing owns, which is disposed by whatever made it, e.g. the
    /// content area for an open document's tab, the popup manager for a dialog or a workbench
    /// story.
    ///
    /// @param name The root's name, for diagnostics.
    /// @return The root.
    public static ControllerNode root(final String name) {
        return new ControllerNode(name, true);
    }

    /// @return The controller's name.
    public String getName() {
        return name;
    }

    /// @return The node that owns this one, or null if none does.
    public ControllerNode parent() {
        return parent;
    }

    /// @return Whether this is a root.
    public boolean isRoot() {
        return root;
    }

    /// @return Whether this node is a root or its owner is connected, so that context resolves.
    public boolean isConnected() {
        ControllerNode node = this;
        while (node.parent != null) {
            node = node.parent;
        }
        return node.root;
    }

    /// Marks this node as shared, e.g. a GIN singleton's, so that nothing can own it (closing one
    /// owner would break it for every other). A shared node isn't counted as unowned.
    public void markShared() {
        shared = true;
        ControllerChecks.owned(this);
    }

    /// @return Whether this node is shared.
    public boolean isShared() {
        return shared;
    }

    /// Owns a child, which is then disposed with this node, and connects it to this node's context.
    ///
    /// Misuse fails (see [ControllerChecks]): owning a root, a shared node, a disposed node, a node
    /// that is already owned, this node or one of its owners, or owning anything once this node is
    /// disposed. With the checks disabled, the misuse is ignored, except that a node owned by
    /// another is moved here, and a node given to a disposed owner is disposed, as nothing else
    /// would release it.
    ///
    /// @param child The child.
    public void adopt(final ControllerNode child) {
        Objects.requireNonNull(child, "child");
        if (disposed || disposing) {
            if (!ControllerChecks.fail("Can't own " + child.name + ": " + name + " is disposed")) {
                child.dispose();
            }
            return;
        }
        if (child.root) {
            ControllerChecks.fail("Can't own " + child.name + ": it is a root");
            return;
        }
        if (child.shared) {
            ControllerChecks.fail("Can't own " + child.name + ": it is shared");
            return;
        }
        if (child.disposed) {
            ControllerChecks.fail("Can't own " + child.name + ": it is disposed");
            return;
        }
        if (child.parent == this) {
            ControllerChecks.fail(child.name + " is already owned by " + name);
            return;
        }
        if (isOrIsOwnedBy(child)) {
            ControllerChecks.fail("Can't own " + child.name + ": it owns " + name);
            return;
        }
        if (child.parent != null) {
            if (ControllerChecks.fail(child.name + " is already owned by " + child.parent.name)) {
                return;
            }
            child.parent.children.remove(child);
        }
        child.parent = this;
        children.add(child);
        ControllerChecks.owned(child);
        if (isConnected()) {
            child.refreshWatches();
        }
    }

    /// Disposes one child early, e.g. a replaced tab's, rather than waiting for this node.
    ///
    /// @param child The child.
    public void release(final ControllerNode child) {
        Objects.requireNonNull(child, "child");
        if (child.parent != this) {
            ControllerChecks.fail("Can't release " + child.name + ": it isn't owned by " + name);
            return;
        }
        child.dispose();
    }

    /// Owns a registration, removed when this node is disposed. Once this node is disposed, a
    /// registration given to it is removed at once.
    ///
    /// @param registration The registration, e.g. of an event bus handler.
    public void add(final HandlerRegistration registration) {
        Objects.requireNonNull(registration, "registration");
        if (disposed || disposing) {
            registration.removeHandler();
        } else {
            registrations.add(registration);
        }
    }

    /// Adds a clean-up, run when this node is disposed. Once this node is disposed, a clean-up
    /// given to it runs at once.
    ///
    /// @param cleanUp What to run, e.g. stopping a timer.
    public void onDispose(final Runnable cleanUp) {
        Objects.requireNonNull(cleanUp, "cleanUp");
        if (disposed || disposing) {
            cleanUp.run();
        } else {
            cleanUps.add(cleanUp);
        }
    }

    /// Provides a value to every node below this one, however deep, including this one.
    ///
    /// @param key   The key.
    /// @param value The value, which may change.
    /// @param <T>   The type of the value.
    /// @throws IllegalStateException If this node already provides the key.
    public <T> void provide(final ContextKey<T> key, final ContextValue<T> value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (provided.containsKey(key)) {
            throw new IllegalStateException(name + " already provides " + key);
        }
        if (disposed || disposing) {
            return;
        }
        provided.put(key, value);
        if (isConnected()) {
            refreshWatches();
        }
    }

    /// @param key The key.
    /// @param <T> The type of the value.
    /// @return The value this node itself provides, e.g. to change it, or null if it provides none.
    @SuppressWarnings("unchecked")
    public <T> ContextValue<T> provided(final ContextKey<T> key) {
        return (ContextValue<T>) provided.get(key);
    }

    /// Stops providing a value, so that this node and those below it see the one provided above it
    /// again, e.g. a dialog's task monitor once the dialog is hidden. Nothing happens if this node
    /// doesn't provide it.
    ///
    /// @param key The key.
    public void unprovide(final ContextKey<?> key) {
        Objects.requireNonNull(key, "key");
        if (provided.remove(key) != null && isConnected() && !disposed && !disposing) {
            refreshWatches();
        }
    }

    /// Provides a value, or changes the one this node provides, e.g. a dialog's task monitor each
    /// time the dialog is shown.
    ///
    /// @param key   The key.
    /// @param value The value.
    /// @param <T>   The type of the value.
    public <T> void setProvided(final ContextKey<T> key, final T value) {
        final ContextValue<T> current = provided(key);
        if (current == null) {
            provide(key, ContextValue.of(value));
        } else {
            current.set(value);
        }
    }

    /// Looks up a context value from this node's owner, so that a node that provides a key itself
    /// (e.g. a document part that is a document) finds the one above it.
    ///
    /// @param key The key.
    /// @param <T> The type of the value.
    /// @return The value, or the key's default if this node has no owner or nothing above provides it.
    public <T> T lookupAbove(final ContextKey<T> key) {
        return parent == null
                ? key.getDefaultValue()
                : parent.lookup(key);
    }

    /// Looks up a value from the nearest node, this one or above, that provides it. For a
    /// narrowing key (see [ContextKey#narrowing]) every provider on the way up counts.
    ///
    /// @param key The key.
    /// @param <T> The type of the value.
    /// @return The value, or the key's default if nothing provides it.
    public <T> T lookup(final ContextKey<T> key) {
        return effectiveValue(key, providers(key));
    }

    /// Watches a value: runs `onChange` with it now (or, if this node isn't connected yet, once
    /// it is), and again whenever it changes, until this node is disposed. A change that leaves
    /// the value as it was isn't reported.
    ///
    /// @param key      The key.
    /// @param onChange What to run with the value.
    /// @param <T>      The type of the value.
    /// @return A registration that stops watching early.
    public <T> HandlerRegistration watch(final ContextKey<T> key, final Consumer<T> onChange) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(onChange, "onChange");
        if (disposed || disposing) {
            return () -> {
            };
        }
        final Watch<T> watch = new Watch<>(key, onChange);
        watches.add(watch);
        if (isConnected()) {
            watch.refresh();
        }
        return () -> {
            watch.stop();
            watches.remove(watch);
        };
    }

    /// Wraps a callback, e.g. a REST result's, so that it does nothing once this node is disposed.
    ///
    /// @param callback The callback.
    /// @param <T>      What it is given.
    /// @return The guarded callback.
    public <T> Consumer<T> guard(final Consumer<T> callback) {
        Objects.requireNonNull(callback, "callback");
        return value -> {
            if (!disposed && !disposing) {
                callback.accept(value);
            }
        };
    }

    /// As [#guard(Consumer)], for a callback that is given nothing.
    ///
    /// @param callback The callback.
    /// @return The guarded callback.
    public Runnable guard(final Runnable callback) {
        Objects.requireNonNull(callback, "callback");
        return () -> {
            if (!disposed && !disposing) {
                callback.run();
            }
        };
    }

    /// Disposes this node: its children, newest first, then its registrations and watches, then
    /// its clean-ups, each newest first. Each runs once, and this node leaves its owner. A
    /// disposed node is never used again.
    ///
    /// @throws RuntimeException The first failure, once everything else has been released (one
    ///                          failing to let go mustn't stop the rest).
    public void dispose() {
        if (disposed || disposing) {
            return;
        }
        disposing = true;
        RuntimeException failure = null;

        final List<ControllerNode> ownedChildren = new ArrayList<>(children);
        children.clear();
        for (int i = ownedChildren.size() - 1; i >= 0; i--) {
            failure = run(ownedChildren.get(i)::dispose, failure);
        }

        for (final Watch<?> watch : watches) {
            watch.stop();
        }
        watches.clear();
        for (int i = registrations.size() - 1; i >= 0; i--) {
            failure = run(registrations.get(i)::removeHandler, failure);
        }
        registrations.clear();

        for (int i = cleanUps.size() - 1; i >= 0; i--) {
            failure = run(cleanUps.get(i), failure);
        }
        cleanUps.clear();

        provided.clear();
        if (parent != null) {
            parent.children.remove(this);
        }
        ControllerChecks.owned(this);
        disposed = true;
        disposing = false;
        if (failure != null) {
            throw failure;
        }
    }

    /// @return Whether this node is disposed (or being disposed).
    public boolean isDisposed() {
        return disposed || disposing;
    }

    /// @return The number of child nodes.
    public int childCount() {
        return children.size();
    }

    /// @return The number of registrations and watches.
    public int registrationCount() {
        return registrations.size() + watches.size();
    }

    @Override
    public String toString() {
        return name;
    }

    private boolean isOrIsOwnedBy(final ControllerNode node) {
        for (ControllerNode current = this; current != null; current = current.parent) {
            if (current == node) {
                return true;
            }
        }
        return false;
    }

    // Re-resolves every watch in this subtree, e.g. once it is connected or a provider is added
    private void refreshWatches() {
        for (final Watch<?> watch : new ArrayList<>(watches)) {
            watch.refresh();
        }
        for (final ControllerNode child : new ArrayList<>(children)) {
            child.refreshWatches();
        }
    }

    // The providers of a key, nearest first: only the nearest, unless the key narrows
    @SuppressWarnings("unchecked")
    private <T> List<ContextValue<T>> providers(final ContextKey<T> key) {
        final List<ContextValue<T>> providers = new ArrayList<>();
        for (ControllerNode node = this; node != null; node = node.parent) {
            final ContextValue<T> value = (ContextValue<T>) node.provided.get(key);
            if (value != null) {
                providers.add(value);
                if (!key.isNarrowing()) {
                    break;
                }
            }
        }
        return providers;
    }

    private static <T> T effectiveValue(final ContextKey<T> key, final List<ContextValue<T>> providers) {
        if (providers.isEmpty()) {
            return key.getDefaultValue();
        }
        // The outermost provider first, each nearer one narrowing it
        T value = providers.get(providers.size() - 1).get();
        for (int i = providers.size() - 2; i >= 0; i--) {
            value = key.narrow(value, providers.get(i).get());
        }
        return value;
    }

    private static RuntimeException run(final Runnable runnable, final RuntimeException failure) {
        try {
            runnable.run();
            return failure;
        } catch (final RuntimeException e) {
            return failure == null
                    ? e
                    : failure;
        }
    }

    // A watch of one key: subscribed to the providers it currently resolves to
    private final class Watch<T> {

        private final ContextKey<T> key;
        private final Consumer<T> onChange;
        private final List<HandlerRegistration> subscriptions = new ArrayList<>();
        private List<ContextValue<T>> sources = new ArrayList<>();
        private boolean reported;
        private boolean stopped;
        private T lastValue;

        private Watch(final ContextKey<T> key, final Consumer<T> onChange) {
            this.key = key;
            this.onChange = onChange;
        }

        // Resolves the providers again, as they may have changed, and reports any new value
        private void refresh() {
            if (stopped) {
                return;
            }
            final List<ContextValue<T>> providers = providers(key);
            if (!sameSources(providers)) {
                unsubscribe();
                sources = providers;
                for (final ContextValue<T> source : sources) {
                    subscriptions.add(source.subscribe(this::report));
                }
            }
            report();
        }

        private void report() {
            if (stopped || disposed || disposing) {
                return;
            }
            final T value = effectiveValue(key, sources);
            if (!reported || !Objects.equals(lastValue, value)) {
                reported = true;
                lastValue = value;
                onChange.accept(value);
            }
        }

        private boolean sameSources(final List<ContextValue<T>> providers) {
            if (providers.size() != sources.size()) {
                return false;
            }
            for (int i = 0; i < providers.size(); i++) {
                if (providers.get(i) != sources.get(i)) {
                    return false;
                }
            }
            return true;
        }

        private void stop() {
            stopped = true;
            unsubscribe();
        }

        private void unsubscribe() {
            for (final HandlerRegistration subscription : subscriptions) {
                subscription.removeHandler();
            }
            subscriptions.clear();
        }
    }
}
