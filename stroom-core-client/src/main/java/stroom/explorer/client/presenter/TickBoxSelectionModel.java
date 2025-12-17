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

package stroom.explorer.client.presenter;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.explorer.shared.ExplorerNode;

import com.google.gwt.user.cellview.client.HasSelection;
import com.google.gwt.view.client.SelectionModel.AbstractSelectionModel;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class TickBoxSelectionModel extends AbstractSelectionModel<ExplorerNode> implements HasSelection<ExplorerNode> {

    // Ensure one value per key
    private final Map<ExplorerNode, TickBoxState> stateMap = new HashMap<>();
    private final Set<ExplorerNode> stateChanges = new HashSet<>();

    private final Map<ExplorerNode, ExplorerNode> parents = new HashMap<>();
    private final Map<ExplorerNode, Set<ExplorerNode>> descendants = new HashMap<>();

    public TickBoxSelectionModel() {
        super(null);
    }

    /**
     * Get the set of selected items.
     *
     * @return the set of selected items
     */
    public Set<ExplorerNode> getSelectedSet() {
        final Set<ExplorerNode> selected = new HashSet<>();
        for (final Entry<ExplorerNode, TickBoxState> entry : stateMap.entrySet()) {
            if (TickBoxState.TICK.equals(entry.getValue())) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    // This method will just return the opposite of the previous state to make
    // sure that the UI refreshes the appropriate rows.
    @Override
    public boolean isSelected(final ExplorerNode item) {
        final TickBoxState currentState = getState(item);
        return currentState != TickBoxState.UNTICK;
    }

    @Override
    public void setSelected(final ExplorerNode item, final boolean selected) {
        if (selected) {
            setState(item, TickBoxState.TICK);
        } else {
            setState(item, TickBoxState.UNTICK);
        }
    }

    private void setState(final ExplorerNode item, final TickBoxState state) {
        final TickBoxState currentState = getState(item);
        if (currentState != state) {
            modifyState(item, state);

            // Change the parent's state.
            changeParent(item);

            if (TickBoxState.TICK.equals(state)) {
                addDescendants(getParent(item), item);
                selectChildren(item);
            } else {
                removeDescendants(item);
            }

            fireSelectionChangeEvent();
        }
    }

    private void removeDescendants(final ExplorerNode item) {
        modifyState(item, TickBoxState.UNTICK);
        final Set<ExplorerNode> set = descendants.get(item);
        if (set != null) {
            for (final ExplorerNode descendant : set) {
                removeDescendants(descendant);
            }
            descendants.remove(item);
        }
    }

    private void addDescendants(final ExplorerNode ancestor, final ExplorerNode descendant) {
        if (ancestor != null) {
            descendants.computeIfAbsent(ancestor, k -> new HashSet<>()).add(descendant);
            addDescendants(getParent(ancestor), descendant);
        }
    }

    @Override
    public boolean hasSelectionChanged(final ExplorerNode item) {
        return stateChanges.remove(item);
    }

    private void changeParent(final ExplorerNode item) {
        final ExplorerNode parent = getParent(item);
        if (parent != null) {
            boolean allTicked = true;
            boolean allUnticked = true;
            final List<ExplorerNode> children = parent.getChildren();
            if (children != null && children.size() > 0) {
                for (final ExplorerNode child : children) {
                    final TickBoxState childState = getState(child);
                    switch (childState) {
                        case TICK:
                            allUnticked = false;
                            break;
                        case HALF_TICK:
                            allUnticked = false;
                            allTicked = false;
                            break;
                        case UNTICK:
                            allTicked = false;
                            break;
                    }
                }
            }

            if (allUnticked) {
                modifyState(parent, TickBoxState.UNTICK);

                // No longer selecting parent items to fix issue #3671
//            } else if (allTicked) {
//                modifyState(parent, TickBoxState.TICK);
            } else {
                modifyState(parent, TickBoxState.HALF_TICK);
            }

            // Change the parent's parent state.
            changeParent(parent);
        }
    }

    private void selectChildren(final ExplorerNode item) {
        final List<ExplorerNode> children = item.getChildren();
        if (children != null && children.size() > 0) {
            for (final ExplorerNode child : children) {
                modifyState(child, TickBoxState.TICK);
                addDescendants(getParent(child), child);
            }
        }
    }

    private void modifyState(final ExplorerNode item, final TickBoxState state) {
        final TickBoxState currentState = getState(item);
        if (currentState != state) {
            stateChanges.add(item);
            if (state == null || state == TickBoxState.UNTICK) {
                stateMap.remove(item);
            } else {
                stateMap.put(item, state);
            }
        }
    }

    public TickBoxState getState(final ExplorerNode item) {
        final TickBoxState state = stateMap.get(item);
        if (state == null) {
            return TickBoxState.UNTICK;
        }
        return state;
    }

    private ExplorerNode getParent(final ExplorerNode object) {
        return parents.get(object);
    }

    public void setRoots(final List<ExplorerNode> roots) {
        parents.clear();

        // Once the tree structure changes ensure we auto select descendants of selected ancestors.
        final Set<ExplorerNode> selectedSet = getSelectedSet();
        stateMap.clear();

        if (roots != null) {
            addChildren(null, roots);
            selectChildren(null, roots, false, selectedSet);
        }
    }

    private void addChildren(final ExplorerNode parent,
                             final List<ExplorerNode> children) {
        if (children == null) {
            return;
        }

        for (final ExplorerNode child : children) {
            parents.put(child, parent);
            addChildren(child, child.getChildren());
        }
    }

    private void selectChildren(final ExplorerNode parent,
                                final List<ExplorerNode> children,
                                final boolean select,
                                final Set<ExplorerNode> selectedSet) {
        if (children == null) {
            return;
        }

        for (final ExplorerNode child : children) {
            final boolean selectChildren = select || selectedSet.contains(parent) || selectedSet.contains(child);
            setSelected(child, selectChildren);
            selectChildren(child, child.getChildren(), selectChildren, selectedSet);
        }
    }
}
