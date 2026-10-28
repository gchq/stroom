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

import stroom.alert.client.event.ConfirmEvent;
import stroom.content.client.component.ContentTabController;
import stroom.data.table.client.Refreshable;
import stroom.processor.client.presenter.ProcessorProfileClient;
import stroom.processor.shared.ProcessorProfile;
import stroom.svg.client.IconColour;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

import java.util.List;

/// Shows the processor profiles, and adds, edits and deletes them.
public class ProcessorProfilesController extends ContentTabController implements Refreshable {

    public static final String TAB_TYPE = "ProcessorProfiles";

    private final ProcessorProfileListController processorProfileList;
    private final Provider<ProcessorProfileEditController> editProvider;
    private final ProcessorProfileClient processorProfileClient;

    /// Made by GIN, with its parts.
    @Inject
    public ProcessorProfilesController(
            final EventBus eventBus,
            final ProcessorProfileListController processorProfileList,
            final Provider<ProcessorProfileEditController> editProvider,
            final ProcessorProfileClient processorProfileClient) {

        super(eventBus);
        own(processorProfileList);
        this.processorProfileList = processorProfileList;
        this.editProvider = editProvider;
        this.processorProfileClient = processorProfileClient;

        processorProfileList.setOnNew(this::add);
        processorProfileList.setOnEdit(this::edit);
        processorProfileList.setOnDelete(this::delete);
        initWidget(processorProfileList.asWidget());
    }

    private void add() {
        final ProcessorProfile processorProfile = ProcessorProfile.builder().name("New Profile").build();
        editProvider.get().show(this, processorProfile, "Create Processor Profile", result -> refresh());
    }

    private void edit() {
        final ProcessorProfile processorProfile = processorProfileList.getSelectionModel().getSelected();
        if (processorProfile != null) {
            processorProfileClient.fetchById(processorProfile.getId(), this::edit, this);
        }
    }

    private void edit(final ProcessorProfile processorProfile) {
        editProvider.get().show(this,
                processorProfile,
                "Edit Processor Profile - " + processorProfile.getName(),
                result -> refresh());
    }

    private void delete() {
        final List<ProcessorProfile> list = processorProfileList.getSelectionModel().getSelectedItems();
        if (!NullSafe.isEmptyCollection(list)) {
            String message = "Are you sure you want to delete the selected processor profile?";
            if (list.size() > 1) {
                message = "Are you sure you want to delete the selected processor profile?";
            }
            ConfirmEvent.fire(ProcessorProfilesController.this, message,
                    result -> {
                        if (result) {
                            processorProfileList.getSelectionModel().clear();
                            for (final ProcessorProfile processorProfile : list) {
                                processorProfileClient.delete(processorProfile.getId(), response -> refresh(), this);
                            }
                        }
                    });
        }
    }

    @Override
    public void refresh() {
        processorProfileList.refresh();
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.PROCESS;
    }

    @Override
    public IconColour getIconColour() {
        return IconColour.GREY;
    }

    @Override
    public String getLabel() {
        return "Processor Profiles";
    }

    @Override
    public String getType() {
        return TAB_TYPE;
    }
}
