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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestControllerNode {

    private final List<String> events = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ControllerChecks.setEnabled(true);
        ControllerChecks.reset();
    }

    @AfterEach
    void tearDown() {
        ControllerChecks.setEnabled(true);
        ControllerChecks.reset();
    }

    @Nested
    class Disposal {

        @Test
        void childrenNewestFirstThenRegistrationsThenCleanUps() {
            final ControllerNode root = ControllerNode.root("root");
            root.onDispose(() -> events.add("cleanUp1"));
            root.add(registration("registration1"));
            final ControllerNode first = owned(root, "first");
            first.onDispose(() -> events.add("first"));
            root.onDispose(() -> events.add("cleanUp2"));
            root.add(registration("registration2"));
            final ControllerNode second = owned(root, "second");
            second.onDispose(() -> events.add("second"));

            root.dispose();

            assertThat(events).containsExactly(
                    "second",
                    "first",
                    "registration2",
                    "registration1",
                    "cleanUp2",
                    "cleanUp1");
        }

        @Test
        void cascadesDepthFirst() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = owned(root, "child");
            final ControllerNode grandChild = owned(child, "grandChild");
            grandChild.onDispose(() -> events.add("grandChild"));
            child.onDispose(() -> events.add("child"));

            root.dispose();

            assertThat(events).containsExactly("grandChild", "child");
            assertThat(child.isDisposed()).isTrue();
            assertThat(grandChild.isDisposed()).isTrue();
        }

        @Test
        void eachRunsOnce() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = owned(root, "child");
            child.onDispose(() -> events.add("child"));
            root.add(registration("registration"));

            child.dispose();
            root.dispose();
            root.dispose();

            assertThat(events).containsExactly("child", "registration");
        }

        @Test
        void aDisposedChildLeavesItsOwner() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = owned(root, "child");

            child.dispose();

            assertThat(root.childCount()).isZero();
        }

        @Test
        void oneFailureDoesNotStopTheRestAndTheFirstIsRethrown() {
            final ControllerNode root = ControllerNode.root("root");
            root.onDispose(() -> events.add("cleanUp1"));
            root.onDispose(() -> {
                throw new IllegalStateException("second");
            });
            root.onDispose(() -> {
                throw new IllegalStateException("first");
            });
            final ControllerNode child = owned(root, "child");
            child.onDispose(() -> events.add("child"));

            assertThatThrownBy(root::dispose)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("first");
            assertThat(events).containsExactly("child", "cleanUp1");
            assertThat(root.isDisposed()).isTrue();
        }

        @Test
        void aFailingChildDoesNotStopItsSiblings() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode first = owned(root, "first");
            first.onDispose(() -> events.add("first"));
            final ControllerNode second = owned(root, "second");
            second.onDispose(() -> {
                throw new IllegalStateException("second");
            });

            assertThatThrownBy(root::dispose).hasMessage("second");
            assertThat(events).containsExactly("first");
            assertThat(first.isDisposed()).isTrue();
        }

        @Test
        void registrationsAndCleanUpsGivenOnceDisposedRunAtOnce() {
            final ControllerNode root = ControllerNode.root("root");
            root.dispose();

            root.add(registration("registration"));
            root.onDispose(() -> events.add("cleanUp"));

            assertThat(events).containsExactly("registration", "cleanUp");
            assertThat(root.registrationCount()).isZero();
        }

        @Test
        void releaseDisposesOneChildEarly() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode replaced = owned(root, "replaced");
            final ControllerNode kept = owned(root, "kept");

            root.release(replaced);

            assertThat(replaced.isDisposed()).isTrue();
            assertThat(kept.isDisposed()).isFalse();
            assertThat(root.childCount()).isEqualTo(1);
        }

        @Test
        void aChildOwnedWhileItsOwnerIsDisposedIsDisposedWhenChecksAreDisabled() {
            ControllerChecks.setEnabled(false);
            final ControllerNode root = ControllerNode.root("root");
            root.dispose();
            final ControllerNode late = new ControllerNode("late");

            root.adopt(late);

            assertThat(late.isDisposed()).isTrue();
        }
    }

    @Nested
    class Guard {

        @Test
        void dropsCallbacksOnceDisposed() {
            final ControllerNode root = ControllerNode.root("root");
            final Consumer<String> consumer = root.guard(events::add);
            final Runnable runnable = root.guard(() -> events.add("runnable"));

            consumer.accept("before");
            runnable.run();
            root.dispose();
            consumer.accept("after");
            runnable.run();

            assertThat(events).containsExactly("before", "runnable");
        }

        @Test
        void dropsCallbacksOfAChildDisposedWithItsOwner() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = owned(root, "child");
            final Consumer<String> consumer = child.guard(events::add);

            root.dispose();
            consumer.accept("late");

            assertThat(events).isEmpty();
        }
    }

    @Nested
    class Misuse {

        @Test
        void owningADisposedChildFails() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = new ControllerNode("child");
            child.dispose();

            assertThatThrownBy(() -> root.adopt(child))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("disposed");
        }

        @Test
        void owningTwiceFails() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = owned(root, "child");

            assertThatThrownBy(() -> root.adopt(child))
                    .hasMessageContaining("already owned by root");
        }

        @Test
        void owningAChildOwnedByAnotherFails() {
            final ControllerNode first = ControllerNode.root("first");
            final ControllerNode second = ControllerNode.root("second");
            final ControllerNode child = owned(first, "child");

            assertThatThrownBy(() -> second.adopt(child))
                    .hasMessageContaining("already owned by first");
        }

        @Test
        void owningASharedNodeFails() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode singleton = new ControllerNode("singleton");
            singleton.markShared();

            assertThatThrownBy(() -> root.adopt(singleton))
                    .hasMessageContaining("shared");
        }

        @Test
        void owningARootFails() {
            final ControllerNode root = ControllerNode.root("root");

            assertThatThrownBy(() -> root.adopt(ControllerNode.root("other")))
                    .hasMessageContaining("is a root");
        }

        @Test
        void owningAnOwnerFails() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode child = owned(root, "child");
            final ControllerNode grandChild = owned(child, "grandChild");

            assertThatThrownBy(() -> grandChild.adopt(child))
                    .hasMessageContaining("it owns grandChild");
            assertThatThrownBy(() -> child.adopt(child))
                    .hasMessageContaining("it owns child");
        }

        @Test
        void owningOnceDisposedFails() {
            final ControllerNode root = ControllerNode.root("root");
            root.dispose();

            assertThatThrownBy(() -> root.adopt(new ControllerNode("late")))
                    .hasMessageContaining("root is disposed");
        }

        @Test
        void releasingANodeThatIsNotAChildFails() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode stranger = new ControllerNode("stranger");

            assertThatThrownBy(() -> root.release(stranger))
                    .hasMessageContaining("isn't owned by root");
            assertThat(stranger.isDisposed()).isFalse();
        }

        @Test
        void withChecksDisabledMisuseIsIgnored() {
            ControllerChecks.setEnabled(false);
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode disposed = new ControllerNode("disposed");
            disposed.dispose();
            final ControllerNode singleton = new ControllerNode("singleton");
            singleton.markShared();
            final ControllerNode child = owned(root, "child");

            root.adopt(disposed);
            root.adopt(singleton);
            root.adopt(child);
            root.release(new ControllerNode("stranger"));

            assertThat(root.childCount()).isEqualTo(1);
            assertThat(singleton.parent()).isNull();
        }

        @Test
        void withChecksDisabledAChildOwnedByAnotherMoves() {
            ControllerChecks.setEnabled(false);
            final ControllerNode first = ControllerNode.root("first");
            final ControllerNode second = ControllerNode.root("second");
            final ControllerNode child = owned(first, "child");

            second.adopt(child);

            assertThat(child.parent()).isSameAs(second);
            assertThat(first.childCount()).isZero();
            first.dispose();
            assertThat(child.isDisposed()).isFalse();
        }
    }

    @Nested
    class UnownedRegistry {

        @Test
        void countsLiveNodesNothingOwns() {
            final ControllerNode root = ControllerNode.root("root");
            final ControllerNode owned = new ControllerNode("owned");
            new ControllerNode("leaked");
            final ControllerNode disposed = new ControllerNode("disposed");
            disposed.dispose();
            final ControllerNode singleton = new ControllerNode("singleton");
            singleton.markShared();

            root.adopt(owned);

            assertThat(ControllerChecks.unownedCount()).isEqualTo(1);
            assertThat(ControllerChecks.unownedNames()).containsExactly("leaked");
        }

        @Test
        void countsEachNameAsOftenAsItIsUnowned() {
            new ControllerNode("leaked");
            new ControllerNode("leaked");
            final ControllerNode owned = new ControllerNode("owned");
            ControllerNode.root("root").adopt(owned);

            assertThat(ControllerChecks.unownedNames()).containsExactly("leaked", "leaked");
        }

        @Test
        void nodesCountedBeforeAResetAreNotTakenOffTheNewCount() {
            final ControllerNode before = new ControllerNode("before");
            ControllerChecks.reset();
            new ControllerNode("after");

            ControllerNode.root("root").adopt(before);

            assertThat(ControllerChecks.unownedCount()).isEqualTo(1);
            assertThat(ControllerChecks.unownedNames()).containsExactly("after");
        }

        @Test
        void rootsAreNotCounted() {
            ControllerNode.root("root");

            assertThat(ControllerChecks.unownedCount()).isZero();
        }

        @Test
        void nothingIsCountedWhenChecksAreDisabled() {
            ControllerChecks.setEnabled(false);
            new ControllerNode("leaked");

            assertThat(ControllerChecks.unownedCount()).isZero();
        }
    }

    @Nested
    class Context {

        private final ContextKey<String> colour = ContextKey.of("colour", "none");

        @Test
        void lookupAboveSkipsWhatTheNodeProvidesItself() {
            final ControllerNode root = ControllerNode.root("root");
            root.provide(colour, ContextValue.of("red"));
            final ControllerNode document = new ControllerNode("document");
            root.adopt(document);
            document.provide(colour, ContextValue.of("blue"));

            assertThat(document.lookup(colour)).isEqualTo("blue");
            assertThat(document.lookupAbove(colour)).isEqualTo("red");
            assertThat(root.lookupAbove(colour)).isEqualTo("none");
        }

        @Test
        void theNearestProviderWins() {
            final ControllerNode root = ControllerNode.root("root");
            root.provide(colour, ContextValue.of("red"));
            final ControllerNode child = owned(root, "child");
            child.provide(colour, ContextValue.of("blue"));
            final ControllerNode grandChild = owned(child, "grandChild");

            assertThat(root.lookup(colour)).isEqualTo("red");
            assertThat(child.lookup(colour)).isEqualTo("blue");
            assertThat(grandChild.lookup(colour)).isEqualTo("blue");
        }

        @Test
        void theDefaultAppliesWhenNothingProvides() {
            final ControllerNode unowned = new ControllerNode("unowned");

            assertThat(unowned.lookup(colour)).isEqualTo("none");
            assertThat(unowned.lookup(Contexts.READ_ONLY)).isTrue();
            assertThat(unowned.lookup(Contexts.DOCUMENT)).isNull();
            assertThat(unowned.lookup(Contexts.TASK_MONITOR).createTaskMonitor()).isNotNull();
        }

        @Test
        void providingAKeyTwiceFails() {
            final ControllerNode root = ControllerNode.root("root");
            root.provide(colour, ContextValue.of("red"));

            assertThatThrownBy(() -> root.provide(colour, ContextValue.of("blue")))
                    .hasMessageContaining("already provides colour");
        }

        @Test
        void aWatchReportsNowAndOnEveryChange() {
            final ControllerNode root = ControllerNode.root("root");
            final ContextValue<String> value = ContextValue.of("red");
            root.provide(colour, value);
            final ControllerNode child = owned(root, "child");

            child.watch(colour, events::add);
            value.set("blue");
            value.set("blue");
            value.set("green");

            assertThat(events).containsExactly("red", "blue", "green");
        }

        @Test
        void watchesMadeBeforeOwnershipAreAppliedWhenConnected() {
            final ControllerNode root = ControllerNode.root("root");
            root.provide(colour, ContextValue.of("red"));
            final ControllerNode parent = new ControllerNode("parent");
            final ControllerNode child = owned(parent, "child");

            child.watch(colour, events::add);
            assertThat(events).isEmpty();
            assertThat(child.isConnected()).isFalse();

            root.adopt(parent);

            assertThat(child.isConnected()).isTrue();
            assertThat(events).containsExactly("red");
        }

        @Test
        void aProviderAddedLaterIsPickedUp() {
            final ControllerNode root = ControllerNode.root("root");
            root.provide(colour, ContextValue.of("red"));
            final ControllerNode child = owned(root, "child");
            final ControllerNode grandChild = owned(child, "grandChild");
            grandChild.watch(colour, events::add);
            final ContextValue<String> nearer = ContextValue.of("blue");

            child.provide(colour, nearer);
            nearer.set("green");

            assertThat(events).containsExactly("red", "blue", "green");
        }

        @Test
        void aWatchStopsWhenItsNodeIsDisposed() {
            final ControllerNode root = ControllerNode.root("root");
            final ContextValue<String> value = ContextValue.of("red");
            root.provide(colour, value);
            final ControllerNode child = owned(root, "child");
            child.watch(colour, events::add);

            child.dispose();
            value.set("blue");

            assertThat(events).containsExactly("red");
            assertThat(value.subscriberCount()).isZero();
        }

        @Test
        void aWatchCanBeStoppedEarly() {
            final ControllerNode root = ControllerNode.root("root");
            final ContextValue<String> value = ContextValue.of("red");
            root.provide(colour, value);
            final HandlerRegistration registration = root.watch(colour, events::add);

            registration.removeHandler();
            value.set("blue");

            assertThat(events).containsExactly("red");
            assertThat(root.registrationCount()).isZero();
        }

        @Test
        void aWatchOnlyReportsChangesOfItsEffectiveValue() {
            final ControllerNode root = ControllerNode.root("root");
            final ContextValue<String> outer = ContextValue.of("red");
            root.provide(colour, outer);
            final ControllerNode child = owned(root, "child");
            child.provide(colour, ContextValue.of("blue"));
            child.watch(colour, events::add);

            outer.set("green");

            assertThat(events).containsExactly("blue");
            assertThat(outer.subscriberCount()).isZero();
        }

        @Test
        void readOnlyOnlyNarrows() {
            final ControllerNode root = ControllerNode.root("root");
            final ContextValue<Boolean> documentReadOnly = ContextValue.of(true);
            root.provide(Contexts.READ_ONLY, documentReadOnly);
            final ControllerNode editor = owned(root, "editor");
            final ContextValue<Boolean> editorReadOnly = ContextValue.of(false);
            editor.provide(Contexts.READ_ONLY, editorReadOnly);
            final ControllerNode field = owned(editor, "field");
            final List<Boolean> readOnly = new ArrayList<>();
            field.watch(Contexts.READ_ONLY, readOnly::add);

            // A read-only document can't be made editable below
            assertThat(field.lookup(Contexts.READ_ONLY)).isTrue();

            // Unlocking the document makes it editable
            documentReadOnly.set(false);
            assertThat(field.lookup(Contexts.READ_ONLY)).isFalse();

            // The editor can then make its own subtree read-only
            editorReadOnly.set(true);
            assertThat(field.lookup(Contexts.READ_ONLY)).isTrue();
            assertThat(root.lookup(Contexts.READ_ONLY)).isFalse();

            assertThat(readOnly).containsExactly(true, false, true);
        }

        @Test
        void aRootThatProvidesEditableMakesItsTreeEditable() {
            final ControllerNode root = ControllerNode.root("story");
            root.provide(Contexts.READ_ONLY, ContextValue.of(false));
            final ControllerNode field = owned(root, "field");

            assertThat(field.lookup(Contexts.READ_ONLY)).isFalse();
        }
    }

    private ControllerNode owned(final ControllerNode owner, final String name) {
        final ControllerNode node = new ControllerNode(name);
        owner.adopt(node);
        return node;
    }

    private HandlerRegistration registration(final String name) {
        return () -> events.add(name);
    }
}
