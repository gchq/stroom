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

package stroom.widget.util.client;

import com.gwtplatform.mvp.client.MyPresenterWidget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/// The presenters that belong to something that is opened and later closed, such as an open
/// document, so that closing it can release them all.
///
/// GWTP binds a presenter as soon as it is made, and unbinding a presenter doesn't unbind the
/// presenters it holds, so a closed document's presenters keep their event bus handlers, and the
/// event bus keeps them, for the life of the page. A scope records every [MyPresenterWidget] made
/// while it is current (see [#capture(Supplier)]), e.g. a document's editor and all the presenters
/// GIN makes for it, and [#dispose()] releases them all when the document is closed.
///
/// A presenter that is shared (e.g. a GIN singleton) must not first be made while a scope is
/// current, or closing that scope would dispose it for everyone; make such presenters eager
/// singletons.
public final class PresenterScope {

    // The scope that presenters made now belong to (the UI runs on one thread)
    private static PresenterScope current;

    private final List<MyPresenterWidget<?>> presenters = new ArrayList<>();
    private final List<Runnable> cleanUps = new ArrayList<>();
    private boolean disposed;

    /// Runs something with this scope current, so that every presenter made meanwhile belongs to
    /// it.
    ///
    /// @param supplier What to run, e.g. making a document's editor.
    /// @param <T>      What it returns.
    /// @return What it returned.
    public <T> T capture(final Supplier<T> supplier) {
        final PresenterScope previous = current;
        current = this;
        try {
            return supplier.get();
        } finally {
            current = previous;
        }
    }

    /// As [#capture(Supplier)], for something that returns nothing.
    ///
    /// @param runnable What to run.
    public void run(final Runnable runnable) {
        capture(() -> {
            runnable.run();
            return null;
        });
    }

    /// Records a presenter in the current scope, if there is one. Called by [MyPresenterWidget]
    /// when it is made.
    ///
    /// @param presenter The presenter.
    /// @return The scope it belongs to, or null if none is current.
    public static PresenterScope register(final MyPresenterWidget<?> presenter) {
        if (current == null || current.disposed) {
            return null;
        }
        current.presenters.add(presenter);
        return current;
    }

    /// @return The current scope, or null if none is current.
    public static PresenterScope current() {
        return current;
    }

    /// Runs something in a scope if there is one, otherwise just runs it.
    ///
    /// @param scope    The scope, or null.
    /// @param supplier What to run.
    /// @param <T>      What it returns.
    /// @return What it returned.
    public static <T> T captureIn(final PresenterScope scope, final Supplier<T> supplier) {
        return scope == null
                ? supplier.get()
                : scope.capture(supplier);
    }

    /// Adds something to release when this scope is disposed, for something that belongs to it but
    /// isn't a presenter (e.g. a document's tab content provider).
    ///
    /// @param cleanUp What to run, after the presenters are released.
    public void addCleanUp(final Runnable cleanUp) {
        if (!disposed) {
            cleanUps.add(cleanUp);
        }
    }

    /// Releases every presenter in this scope, the last made first (see
    /// [MyPresenterWidget#dispose()]), then runs its clean ups. Presenters made in it afterwards
    /// don't belong to it.
    ///
    /// @throws RuntimeException The first failure, once everything else has been released (one
    ///                          failing to let go mustn't stop the rest).
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        RuntimeException failure = null;
        for (int i = presenters.size() - 1; i >= 0; i--) {
            try {
                presenters.get(i).dispose();
            } catch (final RuntimeException e) {
                failure = failure == null
                        ? e
                        : failure;
            }
        }
        presenters.clear();
        for (final Runnable cleanUp : cleanUps) {
            try {
                cleanUp.run();
            } catch (final RuntimeException e) {
                failure = failure == null
                        ? e
                        : failure;
            }
        }
        cleanUps.clear();
        if (failure != null) {
            throw failure;
        }
    }

    /// Disposes the scope a presenter belongs to, if it belongs to one.
    ///
    /// @param presenter The presenter, e.g. a closed document's editor.
    public static void disposeScopeOf(final Object presenter) {
        if (presenter instanceof final MyPresenterWidget<?> myPresenterWidget
            && myPresenterWidget.getPresenterScope() != null) {
            myPresenterWidget.getPresenterScope().dispose();
        }
    }

    /// @return Whether the scope has been disposed.
    public boolean isDisposed() {
        return disposed;
    }

    /// @return The number of presenters in the scope.
    public int size() {
        return presenters.size();
    }
}
