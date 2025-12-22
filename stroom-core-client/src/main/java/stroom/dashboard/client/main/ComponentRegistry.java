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

package stroom.dashboard.client.main;

import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Singleton
public class ComponentRegistry {

    private final Map<ComponentType, Provider<?>> providers = new HashMap<>();
    private final Map<String, Provider<?>> providersByTypeString = new HashMap<>();

    public void register(final ComponentType componentType, final Provider<?> provider) {
        providers.put(componentType, provider);
        providersByTypeString.put(componentType.id, provider);
    }

    public List<ComponentType> getTypes() {
        final List<ComponentType> types = new ArrayList<>(providers.keySet());
        Collections.sort(types, new ComponentTypeComparator());
        return types;
    }

    public Component getComponent(final ComponentType componentType) {
        final Provider<?> provider = providers.get(componentType);
        if (provider != null) {
            final Object component = provider.get();
            if (component != null && component instanceof Component) {
                return (Component) component;
            }
        }
        return null;
    }

    public Component getComponent(final String type) {
        final Provider<?> provider = providersByTypeString.get(type);
        if (provider != null) {
            final Object component = provider.get();
            if (component != null && component instanceof Component) {
                return (Component) component;
            }
        }
        return null;
    }

    public enum ComponentUse {
        PANEL, INPUT
    }

    public static class ComponentType {

        private final int priority;
        private final String id;
        private final String name;
        private final ComponentUse use;

        public ComponentType(final int priority, final String id, final String name, final ComponentUse use) {
            this.priority = priority;
            this.id = id;
            this.name = name;
            this.use = use;
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public ComponentUse getUse() {
            return use;
        }

        @Override
        public int hashCode() {
            return id.hashCode();
        }

        @Override
        public boolean equals(final Object obj) {
            return id.equals(((ComponentType) obj).id);
        }
    }

    private static class ComponentTypeComparator implements Comparator<ComponentType> {

        @Override
        public int compare(final ComponentType o1, final ComponentType o2) {
            return Integer.compare(o1.priority, o2.priority);
        }
    }
}
