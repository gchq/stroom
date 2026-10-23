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

package stroom.widget.presenter.client.gin;

import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;

import com.google.gwt.inject.client.AbstractGinModule;
import com.google.inject.Singleton;

/// A GIN module that binds presenters and their views.
public abstract class AbstractPresenterModule extends AbstractGinModule {

    /// Binds a presenter made once, when first needed, and kept (e.g. a dialog opened from the main
    /// menu), its view, and its listener: a class made when the application starts, which hears the
    /// app-wide events the presenter handles and makes it on the first.
    ///
    /// @param presenter The presenter.
    /// @param view      Its view's interface.
    /// @param viewImpl  Its view.
    /// @param listener  Its listener.
    /// @param <P>       The presenter's type.
    /// @param <V>       The view's type.
    protected <P extends MyPresenterWidget<?>, V extends View> void bindPresenter(final Class<P> presenter,
                                                                                  final Class<V> view,
                                                                                  final Class<? extends V> viewImpl,
                                                                                  final Class<?> listener) {
        bind(presenter).in(Singleton.class);
        bind(viewImpl).in(Singleton.class);
        bind(view).to(viewImpl);
        bind(listener).asEagerSingleton();
    }

    /// Binds a presenter made once, when first needed, and kept, that hears no app-wide events (it is
    /// shown by whatever uses it), and its view.
    ///
    /// @param presenter The presenter.
    /// @param view      Its view's interface.
    /// @param viewImpl  Its view.
    /// @param <P>       The presenter's type.
    /// @param <V>       The view's type.
    protected <P extends MyPresenterWidget<?>, V extends View> void bindPresenter(final Class<P> presenter,
                                                                                  final Class<V> view,
                                                                                  final Class<? extends V> viewImpl) {
        bind(presenter).in(Singleton.class);
        bind(viewImpl).in(Singleton.class);
        bind(view).to(viewImpl);
    }

    /// Binds a presenter made once, when first needed, and kept, whose view is bound elsewhere, and
    /// its listener.
    ///
    /// @param presenter The presenter.
    /// @param listener  Its listener.
    /// @param <P>       The presenter's type.
    protected <P extends MyPresenterWidget<?>> void bindPresenter(final Class<P> presenter,
                                                                  final Class<?> listener) {
        bind(presenter).in(Singleton.class);
        bind(listener).asEagerSingleton();
    }

    /// Binds a presenter, made each time one is asked for, and its view.
    ///
    /// @param presenter The presenter.
    /// @param view      Its view's interface.
    /// @param viewImpl  Its view.
    /// @param <P>       The presenter's type.
    /// @param <V>       The view's type.
    protected <P extends MyPresenterWidget<?>, V extends View> void bindPresenterWidget(
            final Class<P> presenter,
            final Class<V> view,
            final Class<? extends V> viewImpl) {
        bind(presenter);
        bind(view).to(viewImpl);
    }

    /// Binds a view used by several presenters.
    ///
    /// @param view     The view's interface.
    /// @param viewImpl The view.
    /// @param <V>      The view's type.
    protected <V extends View> void bindSharedView(final Class<V> view, final Class<? extends V> viewImpl) {
        bind(view).to(viewImpl);
    }
}
