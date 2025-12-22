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

import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.dashboard.client.unknown.UnknownComponentPresenter;
import stroom.docref.HasDisplayValue;

import com.google.gwt.core.client.JavaScriptObject;
import com.google.gwt.json.client.JSONObject;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Components implements Iterable<Component> {

    private final ComponentRegistry componentRegistry;

    private final Map<String, Component> idMap = new HashMap<>();
    private final Map<String, List<Component>> typeMap = new HashMap<>();

    private final Provider<UnknownComponentPresenter> unknownComponentProvider;

    private final JavaScriptObject context;

    @Inject
    public Components(final ComponentRegistry componentRegistry,
                      final Provider<UnknownComponentPresenter> unknownComponentProvider) {
        this.componentRegistry = componentRegistry;
        this.unknownComponentProvider = unknownComponentProvider;
        this.context = new JSONObject().getJavaScriptObject();
    }

    public Component add(final String type,
                         final String id) {
        Component component = componentRegistry.getComponent(type);
        if (component == null) {
            component = unknownComponentProvider.get();
            idMap.put(id, component);

        } else {
            idMap.put(id, component);
            typeMap.computeIfAbsent(type, k -> new ArrayList<>()).add(component);
        }
        return component;
    }

    public void onClose(final String id) {
        final Component component = idMap.remove(id);
        if (component != null) {
            component.onClose();
        }
    }

    public Component remove(final String id) {
        final Component component = idMap.remove(id);
        if (component != null) {
            final String type = component.getComponentType().getId();
            final List<Component> list = typeMap.get(type);
            if (list != null) {
                list.remove(component);
                if (list.isEmpty()) {
                    typeMap.remove(type);
                }
            }

            component.onRemove();
        }
        return component;
    }

    public void onClose() {
        final List<String> componentIdList = new ArrayList<>(idMap.keySet());
        for (final String id : componentIdList) {
            onClose(id);
        }
    }

    public void removeAll() {
        final List<String> componentIdList = new ArrayList<>(idMap.keySet());
        for (final String id : componentIdList) {
            remove(id);
        }
    }

    public Collection<Component> getComponents() {
        return idMap.values();
    }

    public Component get(final String id) {
        return idMap.get(id);
    }

    public List<Component> getSortedComponentsByType(final String... type) {
        final List<Component> list = new ArrayList<>();
        for (final String t : type) {
            final List<Component> components = typeMap.get(t);
            if (components != null) {
                list.addAll(components);
            }
        }
        list.sort(Comparator.comparing(HasDisplayValue::getDisplayValue));
        return list;
    }

    public String validateOrGetLastComponentId(final String id, final String typeId) {
        String newId = null;

        if (id != null) {
            // See if we can find this component.
            final Component component = get(id);
            // If we have found the component check that it is the right type.
            if (component != null && typeId.equals(component.getComponentType().getId())) {
                // Set the id as this is a valid component.
                newId = id;
            }
        }

        if (newId == null) {
            // If we didn't find the component or it has an invalid type then just choose the last component of the
            // required type if we can find one.
            final List<Component> list = typeMap.get(typeId);
            if (list != null && !list.isEmpty()) {
                newId = list.get(list.size() - 1).getId();
            }
        }

        return newId;
    }

    public List<ComponentType> getComponentTypes() {
        return componentRegistry.getTypes();
    }

    public void clear() {
        idMap.clear();
        typeMap.clear();
    }

    @Override
    public Iterator<Component> iterator() {
        return idMap.values().iterator();
    }

    public int size() {
        return idMap.size();
    }

    public JavaScriptObject getContext() {
        return context;
    }
}
