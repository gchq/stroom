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

package stroom.processor.client.component;

import stroom.alert.client.event.AlertEvent;
import stroom.data.client.component.FeedRefCell;
import stroom.data.client.component.RefCellsController;
import stroom.data.shared.StreamTypeNames;
import stroom.processor.shared.FeedDependency;
import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// A processor filter's feed dependencies ([FeedDependencyList]), as its owner gives them: New and Edit
/// open a feed dependency's editor, Remove removes the selected one. A processor filter is not a
/// document, so the list is never read only.
public class FeedDependencyListController extends Controller implements Focus {

    private final FeedDependencyList view;
    private final List<FeedDependency> feedDependencies = new ArrayList<>();
    private final Provider<FeedDependencyEditorController> feedDependencyEditorProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param view                         The content.
    /// @param feedDependencyEditorProvider Makes a feed dependency's editor.
    /// @param refCells                     Lets the feed cells open their feeds.
    @Inject
    public FeedDependencyListController(
            final EventBus eventBus,
            final FeedDependencyList view,
            final Provider<FeedDependencyEditorController> feedDependencyEditorProvider,
            final RefCellsController refCells) {
        super(eventBus);
        this.view = view;
        this.feedDependencyEditorProvider = feedDependencyEditorProvider;
        own(refCells);
        initWidget(view);
        view.addColumns(refCells.configure(new FeedRefCell.Builder<>()));
        view.setOnAdd(this::onAdd);
        view.setOnEdit(feedDependency -> showEditor(feedDependency, false));
        view.setOnRemove(this::onRemove);
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// @param list The feed dependencies, or `null` for none.
    public void setFeedDependencies(final List<FeedDependency> list) {
        this.feedDependencies.clear();
        if (list != null) {
            this.feedDependencies.addAll(list);
        }
        refresh();
    }

    /// @return A copy of the feed dependencies shown.
    public List<FeedDependency> getFeedDependencies() {
        return new ArrayList<>(feedDependencies);
    }

    private void onAdd() {
        final FeedDependency feedDependency = new FeedDependency(UUID.randomUUID().toString(),
                null,
                StreamTypeNames.REFERENCE);
        showEditor(feedDependency, true);
    }

    private void showEditor(final FeedDependency feedDependency,
                            final boolean isNew) {
        if (feedDependency != null) {
            feedDependencyEditorProvider.get().edit(this,
                    isNew
                            ? "New Feed Dependency"
                            : "Edit Feed Dependency",
                    feedDependency,
                    (updated, e) -> {
                        if (updated.getFeedName() == null) {
                            AlertEvent.fireError(this, "You must specify a feed to use.", e::reset);
                        } else if (updated.getStreamType() == null) {
                            AlertEvent.fireError(this, "You must specify a stream type to use.", e::reset);
                        } else {
                            final int index = feedDependencies.indexOf(updated);
                            if (index == -1) {
                                feedDependencies.add(updated);
                            } else {
                                feedDependencies.remove(index);
                                feedDependencies.add(index, updated);
                            }
                            refresh();
                            e.hide();
                        }
                    });
        }
    }

    private void onRemove(final FeedDependency selected) {
        if (selected != null) {
            feedDependencies.remove(selected);
            refresh();
        }
    }

    private void refresh() {
        view.setData(feedDependencies);
    }
}
