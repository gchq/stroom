/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.event;

import stroom.app.guice.AppModule;
import stroom.test.common.util.guice.GuiceTestUtil;
import stroom.util.entityevent.EntityEvent.Handler;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.entityevent.EntityEventHandlers;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class TestEntityEventHandlers {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestEntityEventHandlers.class);
    private static final Key<Set<Handler>> ENTITY_EVENT_HANDLERS_KEY = Key.get(new TypeLiteral<>() {
    });

    /// Checks that every class declaring an entity event handler implements the handler interface.
    @Test
    void testEntityEventHandlersImplementHandler() {
        doWithEventHandlers(classInfoList -> {
            final List<String> classNames = classInfoList
                    .stream()
                    .filter(classInfo -> {
                        LOGGER.debug("Checking class {} implements EntityEvent.Handler",
                                classInfo.getName());
                        return !classInfo.implementsInterface(Handler.class.getName());
                    })
                    .map(ClassInfo::getName)
                    .sorted()
                    .toList();

            assertThat(classNames)
                    .as("Classes annotated with EntityEventHandler or EntityEventHandlers " +
                        "must implement EntityEvent.Handler")
                    .isEmpty();
        });
    }

    @Test
    void testBindings() {
        doWithEventHandlers(eventHandlers -> {

            // Find all classes annotated with EntityEventHandler or EntityEventHandlers
            final List<String> classWithAnno = eventHandlers.stream()
                    .map(ClassInfo::getName)
                    .sorted()
                    .toList();

            // Find all classes bound to EntityEvent.Handler
            final Module appModule = new AppModule();
            final List<String> boundClasses = GuiceTestUtil.getMultibindTargets(
                            ENTITY_EVENT_HANDLERS_KEY, appModule)
                    .stream()
                    .map(Class::getName)
                    .toList();

            Assertions.assertThat(boundClasses)
                    .as("All classes annotated with EntityEventHandler or EntityEventHandlers " +
                        "must be bound to EntityEvent.Handler")
                    .containsAll(classWithAnno);
        });
    }

//    @Test
//    void testBindings2() {
//        doWithEventHandlers(eventHandlers -> {
//
//            final Module module = new AppModule();
//            final List<String> classWithAnno = eventHandlers.stream()
//                    .map(ClassInfo::getName)
//                    .sorted()
//                    .toList();
//
//            final List<Element> elements = Elements.getElements(module);
//
//
//            MultibinderBinding<?> multibinderBinding = null;
//
//            for (final Element element : elements) {
//
//                if (element instanceof ProviderInstanceBinding<?> binding) {
//                    if (Objects.equals(binding.getKey(), ENTITY_EVENT_HANDLERS_KEY)) {
//                        System.out.println("ProviderInstanceBinding: " + element);
//                        multibinderBinding = binding.acceptTargetVisitor(new MyBindingVisitor());
//                        break;
//                    }
//                }
//
//
////                if (element instanceof LinkedKeyBinding<?> binding) {
////                    if (Objects.equals(binding.getLinkedKey(), ENTITY_EVENT_HANDLERS_KEY)) {
////                        System.out.println("LinkedKeyBinding: " + element);
////                        binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<Object, Void>() {
////                            @Override
////                            public Void visit(final LinkedKeyBinding<?> linkedKeyBinding) {
////                                return super.visit(linkedKeyBinding);
////                            }
////                        });
////                    }
////                }
////
////                if (element instanceof ProviderBinding<?> binding) {
////                    System.out.println("Target Key: " + binding.getKey());
////
////                    // Visit the binding to check if it's a Multibinder
////                    binding.acceptTargetVisitor(new DefaultBindingTargetVisitor<Object, Void>()
////                        implements MultibindingsTargetVisitor<Object, Void> {
////
////                        @Override
////                        public Void visit(MultibinderBinding<?> multibinder) {
////                            System.out.println("Found Multibinder Set: " + multibinder.getSetKey());
////
////                            // Iterate over all element bindings in the Multibinder
////                            for (Binding<?> elementBinding : multibinder.getElements()) {
////                                inspectElementBinding(elementBinding);
////                            }
////                            return null;
////                        }
////                    });
//

    /// /                    // If the element exposes static/declared dependencies
    /// /                    if (element instanceof HasDependencies) {
    /// /                        HasDependencies depHolder = (HasDependencies) element;
    /// /                        for (Dependency<?> dep : depHolder.getDependencies()) {
    /// /                            System.out.println("  └── Depends on: " + dep.getKey());
    /// /                        }
    /// /                    }
    /// /                    }
//            }
//            LOGGER.info("multibinderBinding key: {}", multibinderBinding.getSetKey());
//
//            final List<Element> mapElements = getMapElements(multibinderBinding, module);
//            mapElements.forEach(element -> {
//                LOGGER.info("element: {}", element);
//            });
//
//            mapElements.stream()
//                    .filter(LinkedKeyBinding.class::isInstance)
//                    .map(element -> (LinkedKeyBinding<?>) element)
//                    .map(LinkedKeyBinding::getLinkedKey)
//                    .map(Key::getTypeLiteral)
//                    .map(TypeLiteral::getRawType)
//                    .forEach(rawType -> {
//                        LOGGER.info("Type: {}", rawType);
//                    });
//        });
//    }

//    List<Element> getMapElements(final MultibinderBinding<?> binding,
//                                 final Module... modules) {
//        final List<Element> elements = new ArrayList<>();
//        for (final Element element : Elements.getElements(modules)) {
//            if (binding.containsElement(element)) {
//                elements.add(element);
//            }
//        }
//        return elements;
//    }


//    MapBinderBinding<?> findMapBinder(final Key<?> mapKey, final Module... modules) {
//        for (Element element : Elements.getElements(modules)) {
//            final MapBinderBinding<?> binding = element.acceptVisitor(
//                    new DefaultElementVisitor<MapBinderBinding<?>>() {
//
//                        MapBinderBinding<?> visit(Binding<T> binding) {
//                            if (binding.getKey().equals(mapKey)) {
//                                return binding.acceptTargetVisitor(new Visitor());
//                            }
//                            return null;
//                        }
//                    });
//            if (binding != null) {
//                return binding;
//            }
//        }
//        return null;
//    }
    private void doWithEventHandlers(final Consumer<List<ClassInfo>> eventHandlersConsumer) {
        try (final ScanResult result = new ClassGraph()
                .acceptPackages("stroom")
                .enableClassInfo()
                .enableAnnotationInfo()
                .scan()) {

            final List<ClassInfo> classInfoList = result.getClassesWithAnyAnnotation(
                            EntityEventHandler.class.getName(),
                            EntityEventHandlers.class.getName())
                    .stream()
                    .toList();
            eventHandlersConsumer.accept(classInfoList);
        }
    }

//    private static class MyBindingVisitor
//            extends DefaultBindingTargetVisitor<Object, MultibinderBinding<?>>
//            implements MultibindingsTargetVisitor<Object, MultibinderBinding<?>> {
//
//        @Override
//        public MultibinderBinding<?> visit(final MultibinderBinding<?> multibinding) {
//            System.out.println(" MultiBind Set Key: " + multibinding.getSetKey());
//            return multibinding;
//        }
//
//        @Override
//        public MultibinderBinding<?> visit(final MapBinderBinding<?> mapbinding) {
//            return null;
//        }
//
//        @Override
//        public MultibinderBinding<?> visit(final OptionalBinderBinding<?> optionalbinding) {
//            return null;
//        }
//    }
//
//    // A visitor that just returns the MapBinderBinding for the binding.
//    private static class Visitor
//            extends DefaultBindingTargetVisitor<Object, MultibinderBinding<?>>
//            implements MultibindingsTargetVisitor<Object, MultibinderBinding<?>> {
//
//        public MultibinderBinding<?> visit(MultibinderBinding<?> multibinderBinding) {
//            return multibinderBinding;
//        }
//
//        public MultibinderBinding<?> visit(final MapBinderBinding<?> mapBinderBinding) {
//            return null;
//        }
//
//        @Override
//        public MultibinderBinding<?> visit(final OptionalBinderBinding<?> optionalBinderBinding) {
//            return null;
//        }
//    }
}
