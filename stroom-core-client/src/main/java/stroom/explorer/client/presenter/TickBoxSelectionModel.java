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

package stroom.explorer.client.presenter;

import com.google.gwt.user.cellview.client.HasSelection;
import com.google.gwt.view.client.SelectionModel.AbstractSelectionModel;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.explorer.shared.ExplorerData;
import stroom.explorer.shared.TreeStructure;

import java.util.*;
import java.util.Map.Entry;

public class TickBoxSelectionModel extends AbstractSelectionModel<ExplorerData> implements HasSelection<ExplorerData> {
    // Ensure one value per key
    private final HashMap<ExplorerData, TickBoxState> stateMap = new HashMap<>();
    private final Set<ExplorerData> stateChanges = new HashSet<ExplorerData>();

    private TreeStructure treeStructure;
    private Map<ExplorerData, Set<ExplorerData>> descendants = new HashMap<ExplorerData, Set<ExplorerData>>();

    public TickBoxSelectionModel() {
        super(null);
    }

    /**
     * Get the set of selected items.
     *
     * @return the set of selected items
     */
    public Set<ExplorerData> getSelectedSet() {
        final Set<ExplorerData> selected = new HashSet<ExplorerData>();
        for (final Entry<ExplorerData, TickBoxState> entry : stateMap.entrySet()) {
            if (TickBoxState.TICK.equals(entry.getValue())) {
                selected.add(entry.getKey());
            }
        }
        return selected;
    }

    // This method will just return the opposite of the previous state to make
    // sure that the UI refreshes the appropriate rows.
    @Override
    public boolean isSelected(final ExplorerData item) {
        final TickBoxState currentState = getState(item);
        return currentState != TickBoxState.UNTICK;
    }

    @Override
    public void setSelected(final ExplorerData item, final boolean selected) {
        if (selected) {
            setState(item, TickBoxState.TICK);
        } else {
            setState(item, TickBoxState.UNTICK);
        }
    }

    private void setState(final ExplorerData item, final TickBoxState state) {
        final TickBoxState currentState = getState(item);
        if (currentState != state) {
            modifyState(item, state);

            // Change the parent's state.
            changeParent(item);

            if (TickBoxState.TICK.equals(state)) {
                addDescendants(treeStructure, treeStructure.getParent(item), item);
                selectChildren(item);
            } else {
                removeDescendants(item);
            }

            fireSelectionChangeEvent();
        }
    }

    private void removeDescendants(final ExplorerData item) {
        modifyState(item, TickBoxState.UNTICK);
        Set<ExplorerData> set = descendants.get(item);
        if (set != null) {
            for (final ExplorerData descendant : set) {
                removeDescendants(descendant);
            }
            descendants.remove(item);
        }
    }

    private void addDescendants(final TreeStructure treeStructure, final ExplorerData ancestor, final ExplorerData descendant) {
        if (treeStructure != null && ancestor != null) {
            Set<ExplorerData> set = descendants.get(ancestor);
            if (set == null) {
                set = new HashSet<ExplorerData>();
                descendants.put(ancestor, set);
            }
            set.add(descendant);

            addDescendants(treeStructure, treeStructure.getParent(ancestor), descendant);
        }
    }

    @Override
    public boolean hasSelectionChanged(final ExplorerData item) {
        return stateChanges.remove(item);
    }

    private void changeParent(final ExplorerData item) {
        final ExplorerData parent = getParent(item);
        if (parent != null) {
            boolean allTicked = true;
            boolean allUnticked = true;
            final List<ExplorerData> children = getChildren(parent);
            if (children != null && children.size() > 0) {
                for (final ExplorerData child : children) {
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
            } else if (allTicked) {
                modifyState(parent, TickBoxState.TICK);
            } else {
                modifyState(parent, TickBoxState.HALF_TICK);
            }

            // Change the parent's parent state.
            changeParent(parent);
        }
    }

    private void selectChildren(final ExplorerData item) {
        final List<ExplorerData> children = getChildren(item);
        if (children != null && children.size() > 0) {
            for (final ExplorerData child : children) {
                modifyState(child, TickBoxState.TICK);
                addDescendants(treeStructure, treeStructure.getParent(child), child);
            }
        }
    }

    private void modifyState(final ExplorerData item, final TickBoxState state) {
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

    public TickBoxState getState(final ExplorerData item) {
        final TickBoxState state = stateMap.get(item);
        if (state == null) {
            return TickBoxState.UNTICK;
        }
        return state;
    }

    private ExplorerData getParent(final ExplorerData object) {
        if (treeStructure != null) {
            return treeStructure.getParent(object);
        }
        return null;
    }

    private List<ExplorerData> getChildren(final ExplorerData object) {
        if (treeStructure != null) {
            return treeStructure.getChildren(object);
        }
        return null;
    }

    public void setTreeStructure(final TreeStructure treeStructure) {
        this.treeStructure = treeStructure;

        // Once the tree structure changes ensure we auto select descendants of selected ancestors.
        if (treeStructure != null) {
            for (final ExplorerData item : getSelectedSet()) {
                selectChildren(treeStructure, item);
            }
        }
    }

    private void selectChildren(final TreeStructure treeStructure, final ExplorerData item) {
        final List<ExplorerData> children = treeStructure.getChildren(item);
        if (children != null) {
            for (final ExplorerData child : children) {
                setSelected(child, true);
                selectChildren(treeStructure, child);
            }
        }
    }
}
