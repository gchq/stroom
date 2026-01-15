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

package stroom.test.common.util.guice;

import stroom.util.ConsoleColour;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import com.google.inject.Binder;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.TypeLiteral;
import com.google.inject.spi.ConstructorBinding;
import com.google.inject.spi.ElementSource;
import com.google.inject.spi.InstanceBinding;
import com.google.inject.spi.LinkedKeyBinding;
import com.google.inject.spi.ProviderBinding;
import com.google.inject.spi.ProviderInstanceBinding;
import com.google.inject.spi.ProviderKeyBinding;
import org.mockito.Mockito;

import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GuiceTestUtil {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(GuiceTestUtil.class);

    private static final String SCOPE_PATTERN = "(?<=scope=)[^,}]+";
    // Give each bind type a colour in the console
    private static final Map<BindType, ConsoleColour> TYPE_TO_COLOUR_MAP = new EnumMap<>(Map.of(
            BindType.MAP_BINDER, ConsoleColour.RED,
            BindType.MULTI_BINDER, ConsoleColour.YELLOW,
            BindType.PROVIDER, ConsoleColour.MAGENTA,
            BindType.INSTANCE, ConsoleColour.BLUE,
            BindType.IMPL, ConsoleColour.CYAN));

    // Util methods only
    private GuiceTestUtil() {
    }

    /**
     * Binds classToMock to a {@link Mockito} mock of that class
     * <p>
     * Consider using {@link AbstractTestModule#bindMock(Class)} instead.
     * </p>
     *
     * @return The mock instance in case you want to add behaviour to it.
     */
    public static <T> T bindMock(final Binder binder, final Class<T> classToMock) {
        Objects.requireNonNull(binder);
        Objects.requireNonNull(classToMock);

        final T mock = Mockito.mock(classToMock);
        binder.bind(classToMock)
                .toInstance(mock);
        return mock;
    }

    /**
     * Create a {@link MockBinderBuilder} to bind multiple interfaces to a {@link Mockito} mock.
     */
    public static MockBinderBuilder buildMockBinder(final Binder binder) {
        return new MockBinderBuilder(binder);
    }

    public static String dumpGuiceModuleHierarchy(final com.google.inject.Module... modules) {
        final Map<String, ModuleInfo> allModuleInfoMap = buildModuleInfoMap(modules);

        final StringBuilder stringBuilder = new StringBuilder();
        allModuleInfoMap.values()
                .stream()
                .filter(ModuleInfo::isRoot)
                .findAny()
                .ifPresent(moduleInfo ->
                        dumpModule(moduleInfo, stringBuilder, ""));
        return stringBuilder.toString();
    }

    public static String dumpBindsSortedByKey(final com.google.inject.Module... modules) {
        final Map<String, ModuleInfo> allModuleInfoMap = buildModuleInfoMap(modules);

        final StringBuilder stringBuilder = new StringBuilder();
        allModuleInfoMap.values()
                .stream()
                .flatMap(moduleInfo -> moduleInfo.binds.values().stream())
                .sorted(Comparator.comparing(bindInfo -> bindInfo.getKey().getTypeLiteral().toString()))
                .forEach(bindInfo -> {
                    dumpBindInfo(bindInfo, stringBuilder);
                    stringBuilder
                            .append("  (")
                            .append(ConsoleColour.cyan(bindInfo.moduleInfo.moduleClass))
                            .append(")\n");
                });
        return stringBuilder.toString();
    }

    private static Map<String, ModuleInfo> buildModuleInfoMap(final com.google.inject.Module[] modules) {
        final Injector injector = Guice.createInjector(modules);

        final Map<Key<?>, Binding<?>> allBindings = injector.getAllBindings();
        final Map<String, ModuleInfo> allModuleInfoMap = new HashMap<>();

        allBindings.forEach((key, binding) ->
                addBindInfo(allModuleInfoMap, key, binding));
        return allModuleInfoMap;
    }

    private static Optional<BindInfo> addBindInfo(final Map<String, ModuleInfo> allModuleInfoMap,
                                                  final Key<?> key,
                                                  final Binding<?> binding) {
        final Class<?> bindingClass = binding.getClass();
        final Object source = binding.getSource();
        // This is the hierarchy of modules, with the first being the module the
        // key is defined in and then going up the chain.
        final List<String> moduleClassNames;
        if (source instanceof ElementSource) {
            moduleClassNames = ((ElementSource) source).getModuleClassNames();
        } else {
            moduleClassNames = Collections.emptyList();
        }

        if (!moduleClassNames.isEmpty()
            && moduleClassNames.get(0).startsWith("stroom.")) {

            final String bindingInfo = determineBindInfo(key, binding, bindingClass);
            if (bindingInfo != null) {
                final BindType bindType = determineBindType(key, binding, bindingClass);

                final Provider<?> provider = binding.getProvider();
                final ModuleInfo moduleInfo = getModuleInfo(moduleClassNames, allModuleInfoMap);
                final BindInfo bindInfo = new BindInfo(
                        key,
                        binding,
                        provider,
                        bindingInfo,
                        moduleInfo,
                        bindType);
                moduleInfo.addBind(bindInfo);
                return Optional.of(bindInfo);
            } else {
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private static BindType determineBindType(final Key<?> key,
                                              final Binding<?> binding,
                                              final Class<?> bindingClass) {
        LOGGER.trace("Anno: {}", key.getAnnotation());
        final Annotation annotation = key.getAnnotation();

        if (annotation != null
            && annotation.toString().contains("@com.google.inject.internal.Element")
            && annotation.toString().contains("MAPBINDER")) {
            return BindType.MAP_BINDER;
        } else if (annotation != null
                   && annotation.toString().contains("@com.google.inject.internal.Element")
                   && annotation.toString().contains("MULTIBINDER")) {
            return BindType.MULTI_BINDER;
        } else {
            if (InstanceBinding.class.isAssignableFrom(bindingClass)) {
                return BindType.INSTANCE;
            } else if (ProviderInstanceBinding.class.isAssignableFrom(bindingClass)
                       || ProviderKeyBinding.class.isAssignableFrom(bindingClass)) {
                return BindType.PROVIDER;
            } else {
                return BindType.IMPL;
            }
        }
    }

    private static String determineBindInfo(final Key<?> key,
                                            final Binding<?> binding,
                                            final Class<?> bindingClass) {
        final String bindingInfo;
        if (Entry.class.isAssignableFrom(key.getTypeLiteral().getRawType())) {
            // dup for multibinds
            bindingInfo = null;
        } else if (ProviderBinding.class.isAssignableFrom(bindingClass)) {
            // dup
            bindingInfo = null;
        } else if (InstanceBinding.class.isAssignableFrom(bindingClass)) {
            bindingInfo = "Instance of " + NullSafe.get(
                    (InstanceBinding<?>) binding,
                    InstanceBinding::getInstance,
                    Object::getClass,
                    Class::getName);
        } else if (ProviderInstanceBinding.class.isAssignableFrom(bindingClass)) {

            final ProviderInstanceBinding<?> providerInstanceBinding =
                    (ProviderInstanceBinding<?>) binding;
            bindingInfo = "Provider (" + NullSafe.get(
                    providerInstanceBinding,
                    ProviderInstanceBinding::getSource,
                    Object::toString)
                          + ")";
        } else if (ProviderKeyBinding.class.isAssignableFrom(bindingClass)) {
            final ProviderKeyBinding<?> providerInstanceBinding =
                    (ProviderKeyBinding<?>) binding;
            bindingInfo = "Provider (" + NullSafe.get(
                    providerInstanceBinding,
                    ProviderKeyBinding::getProviderKey,
                    Key::getTypeLiteral,
                    TypeLiteral::getRawType,
                    Class::getName)
                          + ")";
        } else if (LinkedKeyBinding.class.isAssignableFrom(bindingClass)) {
            // Standard binding of iface to impl
            final LinkedKeyBinding<?> linkedKeyBinding = (LinkedKeyBinding<?>) binding;
            bindingInfo = linkedKeyBinding.getLinkedKey().getTypeLiteral().toString();
        } else if (ConstructorBinding.class.isAssignableFrom(bindingClass)) {
            final ConstructorBinding<?> constructorBinding = (ConstructorBinding<?>) binding;
            final Matcher matcher = Pattern.compile(SCOPE_PATTERN)
                    .matcher(constructorBinding.toString());
            final String scope = matcher.find()
                    ? matcher.group()
                    : "";
            bindingInfo = "Constructor - " + scope;
        } else {
            bindingInfo = bindingClass.getSimpleName();
        }
        return bindingInfo;
    }

    private static void dumpModule(final ModuleInfo moduleInfo,
                                   final StringBuilder stringBuilder,
                                   final String pad) {
        final String childPad = pad + "    ";
        stringBuilder.append(pad)
                .append(ConsoleColour.cyan(moduleInfo.toString()))
                .append("\n");

        final Comparator<BindInfo> byKeyComparator = Comparator.comparing(bindInfo ->
                bindInfo.getKey().getTypeLiteral().toString());
        final Comparator<BindInfo> byInfoComparator = Comparator.comparing(BindInfo::getInfo);

        // dump the binds on this module first
        moduleInfo.binds
                .values()
                .stream()
                .sorted(byKeyComparator.thenComparing(byInfoComparator))
                .forEach(bindInfo -> {
                    stringBuilder.append(childPad);
                    dumpBindInfo(bindInfo, stringBuilder);
                    stringBuilder.append("\n");
                });

        // dump the child modules and their binds
        moduleInfo.childModules
                .stream()
                .sorted(Comparator.comparing(moduleInfo2 -> moduleInfo2.moduleClass))
                .forEach(moduleInfo3 -> {
                    dumpModule(moduleInfo3, stringBuilder, childPad);
                });
    }

    private static void dumpBindInfo(final BindInfo bindInfo, final StringBuilder stringBuilder) {

        final ConsoleColour typeColour = Objects.requireNonNullElse(
                TYPE_TO_COLOUR_MAP.get(bindInfo.bindType),
                ConsoleColour.NO_COLOUR);

        stringBuilder
                .append(ConsoleColour.green(bindInfo.key.getTypeLiteral().getRawType().getName()))
                .append(" => ")
                .append(ConsoleColour.colourise(bindInfo.bindType.toString(), typeColour))
                .append(" ")
                .append(bindInfo.info);
    }

    private static ModuleInfo getModuleInfo(final List<String> moduleClassNames,
                                            final Map<String, ModuleInfo> allModuleInfoMap) {
        if (NullSafe.isEmptyCollection(moduleClassNames)) {
            throw new RuntimeException(LogUtil.message("Empty list"));
        }

        final String owningModuleClassName = moduleClassNames.get(0);

        if (moduleClassNames.size() == 1) {
            // root module
            return allModuleInfoMap.computeIfAbsent(
                    owningModuleClassName,
                    className -> new ModuleInfo(className, null));
        } else {
            // recurse
            final List<String> parentClassNames = moduleClassNames.subList(1, moduleClassNames.size());
            final ModuleInfo parentModuleInfo = getModuleInfo(parentClassNames, allModuleInfoMap);
            final ModuleInfo owningModuleInfo = allModuleInfoMap.computeIfAbsent(
                    owningModuleClassName,
                    k -> new ModuleInfo(k, parentModuleInfo));
            // Add the child to the parent
            parentModuleInfo.addChildModule(owningModuleInfo);
            return owningModuleInfo;
        }
    }


    // --------------------------------------------------------------------------------


    private static class ModuleInfo {

        private final String moduleClass;
        private final Map<Key<?>, BindInfo> binds = new HashMap<>();
        private final Set<ModuleInfo> childModules = new HashSet<>();
        private final ModuleInfo parentModule;

        public ModuleInfo(final String moduleClass, final ModuleInfo parentModule) {
            this.moduleClass = Objects.requireNonNull(moduleClass);
            this.parentModule = parentModule;
        }

        public String getModuleClass() {
            return moduleClass;
        }

        public void addBind(final BindInfo bindInfo) {
            binds.put(bindInfo.key, bindInfo);
        }

        public Set<BindInfo> getBinds() {
            return new HashSet<>(binds.values());
        }

        public void addChildModule(final ModuleInfo moduleInfo) {
            childModules.add(moduleInfo);
        }

        public Set<ModuleInfo> getChildModules() {
            return childModules;
        }

        public boolean isRoot() {
            return parentModule == null;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ModuleInfo that = (ModuleInfo) o;
            return moduleClass.equals(that.moduleClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(moduleClass);
        }

        @Override
        public String toString() {
            return moduleClass;
        }
    }


    // --------------------------------------------------------------------------------


    private static enum BindType {
        PROVIDER,
        MULTI_BINDER,
        MAP_BINDER,
        INSTANCE,
        IMPL
    }


    // --------------------------------------------------------------------------------


    private static class BindInfo {

        private final Key<?> key;
        private final Binding<?> binding;
        private final Provider<?> provider;
        private final String info;
        private final ModuleInfo moduleInfo;
        private final BindType bindType;

        public BindInfo(final Key<?> key,
                        final Binding<?> binding,
                        final Provider<?> provider,
                        final String info,
                        final ModuleInfo moduleInfo,
                        final BindType bindType) {
            this.key = Objects.requireNonNull(key);
            this.binding = binding;
            this.provider = provider;
            this.info = info;
            this.moduleInfo = moduleInfo;
            this.bindType = bindType;
        }

        public Key<?> getKey() {
            return key;
        }

        public Binding<?> getBinding() {
            return binding;
        }

        public String getInfo() {
            return info;
        }

        public ModuleInfo getModuleInfo() {
            return moduleInfo;
        }

        public BindType getBindType() {
            return bindType;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final BindInfo bindInfo = (BindInfo) o;
            return key.equals(bindInfo.key);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }

        @Override
        public String toString() {
            return LogUtil.message("{} => {}\n  {}\n {}",
                    key.getTypeLiteral().getRawType().getName(),
                    info,
                    binding,
                    provider);
        }
    }


    // --------------------------------------------------------------------------------


    public static class MockBinderBuilder {

        private final Binder binder;

        private MockBinderBuilder(final Binder binder) {
            this.binder = binder;
        }

        public <T> MockBinderBuilder addMockBindingFor(final Class<T> interfaceType) {
            final T mock = Mockito.mock(interfaceType);
            binder.bind(interfaceType).toInstance(mock);
            return this;
        }
    }
}
