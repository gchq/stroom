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

import stroom.task.client.TaskMonitorFactory;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.util.client.PresenterScope;

import com.google.gwt.user.client.ui.Widget;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/// Puts an existing presenter into the ownership tree, so that a component can show one it
/// isn't rewritten as (e.g. the code editor or the permissions tab) and have it released with
/// itself:
///
/// ```java
/// editor = own(PresenterAdapter.of(editorPresenterProvider::get)
///         .onReadOnly(EditorPresenter::setReadOnly));
/// ```
///
/// GWTP doesn't unbind the presenters a presenter holds when it is unbound, so the adapter makes
/// the presenter in a [PresenterScope] of its own, which records every presenter made meanwhile
/// (the presenter's own, made by GIN) and those made later with `inScope`. Disposing the adapter
/// disposes that scope, and so all of them. A presenter made elsewhere would belong to whatever
/// scope was current then, so the adapter only takes something that makes one.
///
/// The presenter is given context the old way: the task monitor through
/// [MyPresenterWidget#setTaskMonitorFactory(TaskMonitorFactory)], once one is provided, and
/// read-only through the setter given to [#onReadOnly(BiConsumer)].
///
/// @param <P> The presenter's type.
public final class PresenterAdapter<P extends MyPresenterWidget<?>> extends Component {

    private final PresenterScope scope;
    private final P presenter;

    private PresenterAdapter(final PresenterScope scope, final P presenter) {
        // Named after the presenter, so a leaked one can be told apart from the others
        super("PresenterAdapter(" + presenter.getClass().getSimpleName() + ")", null);
        this.scope = scope;
        this.presenter = own(presenter);
        // The presenters it made, which it doesn't own yet, are disposed with the scope
        onDispose(scope::dispose);
        watch(Contexts.TASK_MONITOR, this::setTaskMonitorFactory);
    }

    /// Makes a presenter and an adapter that owns it.
    ///
    /// @param maker What makes the presenter, e.g. `editorPresenterProvider::get`.
    /// @param <P>   The presenter's type.
    /// @return The adapter, for a component to own.
    public static <P extends MyPresenterWidget<?>> PresenterAdapter<P> of(final Supplier<P> maker) {
        Objects.requireNonNull(maker, "maker");
        final PresenterScope scope = new PresenterScope();
        final P presenter = Objects.requireNonNull(scope.collect(maker), "presenter");
        return new PresenterAdapter<>(scope, presenter);
    }

    /// Gives the presenter read-only now (once this adapter is owned) and whenever it changes.
    ///
    /// @param setReadOnly How the presenter is made read-only, e.g. `EditorPresenter::setReadOnly`.
    /// @return This adapter.
    public PresenterAdapter<P> onReadOnly(final BiConsumer<P, Boolean> setReadOnly) {
        Objects.requireNonNull(setReadOnly, "setReadOnly");
        watch(Contexts.READ_ONLY, readOnly -> setReadOnly.accept(presenter, readOnly));
        return this;
    }

    /// @return The presenter.
    public P getPresenter() {
        return presenter;
    }

    /// @return The number of presenters this adapter releases when it is disposed.
    public int presenterCount() {
        return scope.size();
    }

    @Override
    public Widget asWidget() {
        return presenter.getWidget();
    }

    // The presenter keeps its own task monitor (which fires task events on the bus) unless one
    // is provided
    private void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        if (taskMonitorFactory != Contexts.TASK_MONITOR.getDefaultValue()) {
            presenter.setTaskMonitorFactory(taskMonitorFactory);
        }
    }
}
