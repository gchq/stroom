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

import stroom.processor.shared.ProfilePeriod;
import stroom.widget.component.client.Controller;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/// A processor profile's periods ([ProfilePeriodList]), as its owner gives them: New and Edit open the
/// period's editor, Remove removes the selected one. A profile is not a document, so the list is
/// never read only.
public class ProfilePeriodListController extends Controller implements Focus {

    private final ProfilePeriodList view;
    private final List<ProfilePeriod> profilePeriods = new ArrayList<>();
    private final Provider<ProfilePeriodEditorController> profilePeriodEditorProvider;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                    The application's event bus.
    /// @param view                        The content.
    /// @param profilePeriodEditorProvider Makes a period's editor.
    @Inject
    public ProfilePeriodListController(final EventBus eventBus,
                                       final ProfilePeriodList view,
                                       final Provider<ProfilePeriodEditorController> profilePeriodEditorProvider) {
        super(eventBus);
        this.view = view;
        this.profilePeriodEditorProvider = profilePeriodEditorProvider;
        initWidget(view);
        view.setOnAdd(this::onAdd);
        view.setOnEdit(this::showEditor);
        view.setOnRemove(this::onRemove);
    }

    @Override
    public void focus() {
        view.focus();
    }

    /// @param list The periods, or `null` for none.
    public void setProfilePeriods(final List<ProfilePeriod> list) {
        this.profilePeriods.clear();
        if (list != null) {
            this.profilePeriods.addAll(list);
        }
        refresh();
    }

    /// @return A copy of the periods shown.
    public List<ProfilePeriod> getProfilePeriods() {
        return new ArrayList<>(profilePeriods);
    }

    private void onAdd() {
        final ProfilePeriod profilePeriod = ProfilePeriod.builder().uuid(UUID.randomUUID().toString()).build();
        showEditor(profilePeriod);
    }

    private void showEditor(final ProfilePeriod profilePeriod) {
        if (profilePeriod != null) {
            profilePeriodEditorProvider.get().edit(this, profilePeriod, updated -> {
                final int index = profilePeriods.indexOf(profilePeriod);
                if (index == -1) {
                    profilePeriods.add(updated);
                } else {
                    profilePeriods.remove(index);
                    profilePeriods.add(index, updated);
                }
                refresh();
            });
        }
    }

    private void onRemove(final ProfilePeriod selected) {
        if (selected != null) {
            profilePeriods.remove(selected);
            refresh();
        }
    }

    private void refresh() {
        view.setData(profilePeriods);
    }
}
