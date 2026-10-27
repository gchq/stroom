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

package stroom.datagen.client.presenter;

import stroom.analytics.client.component.ScheduledProcessingController;
import stroom.datagen.client.component.DataGenSettingsController;
import stroom.datagen.shared.DataGenDoc;
import stroom.docref.DocRef;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

/**
 * Editor for a data generator, presenting Settings (destination feed and template), Execution
 * (schedule), Documentation and Permissions as tabs.
 */
public class DataGenPresenter
        extends DocTabPresenter<LinkTabPanelView, DataGenDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData EXECUTION = new TabDataImpl("Execution");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    @Inject
    public DataGenPresenter(final EventBus eventBus,
                            final LinkTabPanelView view,
                            final Provider<DataGenSettingsController> dataGenSettingsProvider,
                            final Provider<ScheduledProcessingController> scheduledProcessingProvider,
                            final Provider<MarkdownEditController> markdownEditProvider,
                            final DocumentUserPermissionsTabProvider<DataGenDoc> documentUserPermissionsTabProvider) {
        super(eventBus, view);

        // Its schedules are saved straight away, not with the document
        final ScheduledProcessingController scheduledProcessing = make(scheduledProcessingProvider);
        scheduledProcessing.setIsDocumentDirty(this::isDirty);

        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, dataGenSettingsProvider::get,
                DataGenSettingsController::read,
                DataGenSettingsController::write));
        addTab(EXECUTION, ControllerTabProvider.ofOwned(eventBus, scheduledProcessing,
                (processing, docRef, document) -> processing.read(docRef)));
        addTab(DOCUMENTATION, new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final DataGenDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public DataGenDoc onWrite(final MarkdownEditController markdownEdit,
                                      final DataGenDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);
        selectTab(SETTINGS);
    }

    @Override
    public String getType() {
        return DataGenDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }
}
