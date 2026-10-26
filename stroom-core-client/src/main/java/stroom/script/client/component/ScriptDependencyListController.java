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

package stroom.script.client.component;

import stroom.alert.client.event.ConfirmEvent;
import stroom.docref.DocRef;
import stroom.explorer.client.component.ExplorerPopupController;
import stroom.script.shared.ScriptDoc;
import stroom.security.shared.DocumentPermission;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;

/// A script's dependencies ([ScriptDependencyList]), by name: Add picks a script to depend on, Remove
/// removes those selected once confirmed, unless the script is read only ([Contexts#READ_ONLY]).
public class ScriptDependencyListController extends Controller {

    private final ScriptDependencyList view;
    private final ExplorerPopupController scriptPicker;
    private List<DocRef> scripts;

    private Runnable onChange = () -> {
    };

    /// Made by GIN, with its content.
    ///
    /// @param eventBus     The application's event bus.
    /// @param view         The content.
    /// @param scriptPicker Picks a script to depend on.
    @Inject
    public ScriptDependencyListController(final EventBus eventBus,
                                          final ScriptDependencyList view,
                                          final ExplorerPopupController scriptPicker) {
        super(eventBus);
        this.view = view;
        this.scriptPicker = own(scriptPicker);
        initWidget(view);

        scriptPicker.setIncludeNullSelection(false);
        scriptPicker.setIncludedTypes(ScriptDoc.TYPE);
        scriptPicker.setRequiredPermissions(DocumentPermission.USE);

        view.setOnAdd(this::onAdd);
        view.setOnRemove(this::onRemove);
        // Read only as the script document is
        watch(Contexts.READ_ONLY, view::setReadOnly);
    }

    private void onAdd() {
        scriptPicker.show(script -> {
            if (script != null) {
                if (!scripts.contains(script) && scripts.add(script)) {
                    onChange.run();
                    refresh();
                }
            }
        });
    }

    private void onRemove() {
        final List<DocRef> list = view.getSelectionModel().getSelectedItems();
        //noinspection SizeReplaceableByIsEmpty
        if (list != null && list.size() > 0) {
            String message = "Are you sure you want to remove this script dependency?";
            if (list.size() > 1) {
                message = "Are you sure you want to remove these script dependencies?";
            }

            ConfirmEvent.fire(this, message, result -> {
                if (result) {
                    for (final DocRef script : list) {
                        scripts.remove(script);
                    }

                    view.getSelectionModel().clear();
                    onChange.run();
                    refresh();
                }
            });
        }
    }

    /// Shows a script's dependencies.
    ///
    /// @param docRef   The script.
    /// @param document Its document.
    public void read(final DocRef docRef, final ScriptDoc document) {
        if (docRef != null && docRef.getName() != null) {
            view.setScriptName(docRef.getName());
        }

        scripts = new ArrayList<>();
        if (document != null) {
            if (document.getDependencies() != null) {
                scripts = document.getDependencies();
            }
        }
        refresh();
    }

    /// @param document The script's document.
    /// @return A copy of it with the dependencies shown.
    public ScriptDoc write(final ScriptDoc document) {
        return document.copy().dependencies(scripts).build();
    }

    private void refresh() {
        if (scripts != null) {
            final List<DocRef> list = new ArrayList<>(scripts);
            list.sort(DocRef::compareTo);
            view.setData(list);
        } else {
            view.setData(new ArrayList<>());
        }
    }

    /// @param onChange What to run when the user changes the dependencies.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }
}
