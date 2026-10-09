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

import com.google.web.bindery.event.shared.Event;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestPresenterScope {

    @Test
    void presentersMadeInAScopeBelongToIt() {
        final PresenterScope scope = new PresenterScope();
        final TestPresenter inside = scope.capture(this::newPresenter);
        final TestPresenter outside = newPresenter();

        assertThat(inside.getPresenterScope()).isSameAs(scope);
        assertThat(outside.getPresenterScope()).isNull();
        assertThat(scope.size()).isEqualTo(1);
    }

    @Test
    void theScopeIsOnlyCurrentWhileCapturing() {
        final PresenterScope scope = new PresenterScope();
        scope.run(() -> assertThat(PresenterScope.current()).isSameAs(scope));

        assertThat(PresenterScope.current()).isNull();
    }

    @Test
    void nestedScopesRestoreTheOuterOne() {
        final PresenterScope outer = new PresenterScope();
        final PresenterScope inner = new PresenterScope();
        final List<TestPresenter> made = new ArrayList<>();
        outer.run(() -> {
            inner.run(() -> made.add(newPresenter()));
            made.add(newPresenter());
        });

        assertThat(made.get(0).getPresenterScope()).isSameAs(inner);
        assertThat(made.get(1).getPresenterScope()).isSameAs(outer);
    }

    @Test
    void captureInWithNoScopeJustRuns() {
        final TestPresenter presenter = PresenterScope.captureIn(null, this::newPresenter);

        assertThat(presenter.getPresenterScope()).isNull();
    }

    @Test
    void disposeUnbindsAndCallsOnDispose() {
        final PresenterScope scope = new PresenterScope();
        final TestPresenter presenter = scope.capture(this::newPresenter);
        presenter.bind();

        scope.dispose();

        assertThat(presenter.isBound()).isFalse();
        assertThat(presenter.disposeCount).isEqualTo(1);
        assertThat(scope.isDisposed()).isTrue();
        assertThat(scope.size()).isZero();
    }

    @Test
    void disposeRemovesHandlersAddedWithThePresenterAsSource() {
        final EventBus eventBus = new SimpleEventBus();
        final PresenterScope scope = new PresenterScope();
        final TestPresenter presenter = scope.capture(() -> new TestPresenter(eventBus));
        final List<String> heard = new ArrayList<>();
        // As a parent does with e.g. child.addDirtyHandler(...), throwing the registration away
        presenter.addTestHandler(() -> heard.add("before"));
        presenter.fireTest();

        scope.dispose();
        presenter.fireTest();

        assertThat(heard).containsExactly("before");
    }

    @Test
    void disposeRunsCleanUpsAfterThePresenters() {
        final PresenterScope scope = new PresenterScope();
        final List<String> order = new ArrayList<>();
        final TestPresenter presenter = scope.capture(this::newPresenter);
        presenter.onDisposeAction = () -> order.add("presenter");
        scope.addCleanUp(() -> order.add("clean up"));

        scope.dispose();

        assertThat(order).containsExactly("presenter", "clean up");
    }

    @Test
    void oneFailureDoesNotStopTheRest() {
        final PresenterScope scope = new PresenterScope();
        final TestPresenter first = scope.capture(this::newPresenter);
        final TestPresenter second = scope.capture(this::newPresenter);
        second.onDisposeAction = () -> {
            throw new IllegalStateException("boom");
        };

        // The failure is reported, but only once everything else has been released
        assertThatThrownBy(scope::dispose).hasMessage("boom");

        assertThat(first.disposeCount).isEqualTo(1);
        assertThat(second.disposeCount).isEqualTo(1);
        assertThat(scope.isDisposed()).isTrue();
    }

    @Test
    void disposeTwiceDoesNothingTheSecondTime() {
        final PresenterScope scope = new PresenterScope();
        final TestPresenter presenter = scope.capture(this::newPresenter);

        scope.dispose();
        scope.dispose();

        assertThat(presenter.disposeCount).isEqualTo(1);
    }

    @Test
    void presentersMadeInADisposedScopeDoNotBelongToIt() {
        final PresenterScope scope = new PresenterScope();
        scope.dispose();

        final TestPresenter presenter = scope.capture(this::newPresenter);

        assertThat(presenter.getPresenterScope()).isNull();
        assertThat(scope.size()).isZero();
    }

    @Test
    void disposeScopeOfIgnoresThingsWithNoScope() {
        PresenterScope.disposeScopeOf(newPresenter());
        PresenterScope.disposeScopeOf("not a presenter");
        PresenterScope.disposeScopeOf(null);
    }

    @Test
    void disposeScopeOfDisposesThePresentersScope() {
        final PresenterScope scope = new PresenterScope();
        final TestPresenter presenter = scope.capture(this::newPresenter);

        PresenterScope.disposeScopeOf(presenter);

        assertThat(scope.isDisposed()).isTrue();
        assertThat(presenter.disposeCount).isEqualTo(1);
    }

    private TestPresenter newPresenter() {
        return new TestPresenter(new SimpleEventBus());
    }


    // --------------------------------------------------------------------------------


    private static final class TestPresenter extends MyPresenterWidget<View> {

        private static final Event.Type<Runnable> TYPE = new Event.Type<>();

        private int disposeCount;
        private Runnable onDisposeAction;

        private TestPresenter(final EventBus eventBus) {
            super(eventBus, Mockito.mock(View.class));
        }

        private void addTestHandler(final Runnable handler) {
            addHandlerToSource(TYPE, handler);
        }

        private void fireTest() {
            getEventBus().fireEventFromSource(new TestEvent(), this);
        }

        @Override
        protected void onDispose() {
            disposeCount++;
            if (onDisposeAction != null) {
                onDisposeAction.run();
            }
        }
    }


    // --------------------------------------------------------------------------------


    private static final class TestEvent extends Event<Runnable> {

        @Override
        public Type<Runnable> getAssociatedType() {
            return TestPresenter.TYPE;
        }

        @Override
        protected void dispatch(final Runnable handler) {
            handler.run();
        }
    }
}
