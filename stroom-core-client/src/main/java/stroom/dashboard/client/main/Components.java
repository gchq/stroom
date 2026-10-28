/*
 * Copyright 2016 Crown Copyright
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

import stroom.dashboard.client.component.UnknownComponentController;
import stroom.dashboard.client.main.ComponentRegistry.ComponentType;
import stroom.docref.HasDisplayValue;
import stroom.widget.component.client.ControllerNode;
import stroom.widget.component.client.HasControllerNode;

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

public class Components implements Iterable<DashboardPane> {

    private final ComponentRegistry componentRegistry;

    private final Map<String, DashboardPane> idMap = new HashMap<>();
    private final Map<String, List<DashboardPane>> typeMap = new HashMap<>();

    private final Provider<UnknownComponentController> unknownComponentProvider;

    private final JavaScriptObject context;
    // What owns the components made (the dashboard), so that closing it releases them
    private ControllerNode owner;

    @Inject
    public Components(final ComponentRegistry componentRegistry,
                      final Provider<UnknownComponentController> unknownComponentProvider) {
        this.componentRegistry = componentRegistry;
        this.unknownComponentProvider = unknownComponentProvider;
        this.context = new JSONObject().getJavaScriptObject();
    }

    /// @param owner What owns the components made, e.g. the dashboard's controller.
    public void setOwner(final HasControllerNode owner) {
        this.owner = owner.getControllerNode();
    }

    public DashboardPane add(final String type,
                         final String id) {
        final DashboardPane component = addComponent(type, id);
        if (owner != null && component instanceof final HasControllerNode hasComponentNode) {
            owner.adopt(hasComponentNode.getControllerNode());
        }
        return component;
    }

    private DashboardPane addComponent(final String type,
                                   final String id) {
        DashboardPane component = componentRegistry.getComponent(type);
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
        final DashboardPane component = idMap.remove(id);
        if (component != null) {
            component.onClose();
        }
    }

    public DashboardPane remove(final String id) {
        final DashboardPane component = idMap.remove(id);
        if (component != null) {
            final String type = component.getComponentType().getId();
            final List<DashboardPane> list = typeMap.get(type);
            if (list != null) {
                list.remove(component);
                if (list.isEmpty()) {
                    typeMap.remove(type);
                }
            }

            component.onRemove();
            // Removed for good
            if (owner != null && component instanceof final HasControllerNode hasComponentNode
                && hasComponentNode.getControllerNode().parent() == owner) {
                owner.release(hasComponentNode.getControllerNode());
            }
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

    public Collection<DashboardPane> getComponents() {
        return idMap.values();
    }

    public DashboardPane get(final String id) {
        return idMap.get(id);
    }

    public List<DashboardPane> getSortedComponentsByType(final String... type) {
        final List<DashboardPane> list = new ArrayList<>();
        for (final String t : type) {
            final List<DashboardPane> components = typeMap.get(t);
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
            final DashboardPane component = get(id);
            // If we have found the component check that it is the right type.
            if (component != null && typeId.equals(component.getComponentType().getId())) {
                // Set the id as this is a valid component.
                newId = id;
            }
        }

        if (newId == null) {
            // If we didn't find the component or it has an invalid type then just choose the last component of the
            // required type if we can find one.
            final List<DashboardPane> list = typeMap.get(typeId);
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
    public Iterator<DashboardPane> iterator() {
        return idMap.values().iterator();
    }

    public int size() {
        return idMap.size();
    }

    public JavaScriptObject getContext() {
        return context;
    }
}
