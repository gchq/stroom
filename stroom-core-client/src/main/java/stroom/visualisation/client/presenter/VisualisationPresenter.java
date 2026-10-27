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

package stroom.visualisation.client.presenter;

import stroom.docref.DocRef;
import stroom.document.asset.client.component.DocumentAssetsTabController;
import stroom.document.asset.client.presenter.DocumentAssetsTabProvider;
import stroom.entity.client.component.MarkdownEditController;
import stroom.entity.client.presenter.ControllerTabProvider;
import stroom.entity.client.presenter.DocTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.MarkdownTabProvider;
import stroom.security.client.presenter.DocumentUserPermissionsTabProvider;
import stroom.visualisation.client.component.VisualisationSettingsController;
import stroom.visualisation.shared.VisualisationDoc;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.inject.Provider;

public class VisualisationPresenter extends DocTabPresenter<LinkTabPanelView, VisualisationDoc> {

    private static final TabData SETTINGS = new TabDataImpl("Settings");
    private static final TabData ASSETS = new TabDataImpl("Assets");
    private static final TabData DOCUMENTATION = new TabDataImpl("Documentation");
    private static final TabData PERMISSIONS = new TabDataImpl("Permissions");

    private final DocumentAssetsTabController<VisualisationDoc> assetsTab;
    private final DocumentAssetsTabProvider<VisualisationDoc> assetsTabProvider;

    @Inject
    public VisualisationPresenter(final EventBus eventBus,
                                  final LinkTabPanelView view,
                                  final Provider<VisualisationSettingsController> settingsProvider,
                                  final DocumentAssetsTabController<VisualisationDoc> assetsTab,
                                  final Provider<MarkdownEditController> markdownEditProvider,
                                  final DocumentUserPermissionsTabProvider<VisualisationDoc>
                                          documentUserPermissionsTabProvider) {
        super(eventBus, view);
        this.assetsTab = own(assetsTab);
        this.assetsTabProvider = new DocumentAssetsTabProvider<>(eventBus, assetsTab);

        addTab(SETTINGS, new ControllerTabProvider<>(eventBus, settingsProvider::get,
                VisualisationSettingsController::read,
                VisualisationSettingsController::write));
        addTab(ASSETS, assetsTabProvider);
        addTab(DOCUMENTATION, new MarkdownTabProvider<>(getEventBus(), markdownEditProvider::get) {
            @Override
            public void onRead(final MarkdownEditController markdownEdit,
                               final DocRef docRef,
                               final VisualisationDoc document) {
                markdownEdit.setText(document.getDescription());
                markdownEdit.setReadOnly(isReadOnly());
            }

            @Override
            public VisualisationDoc onWrite(final MarkdownEditController markdownEdit,
                                            final VisualisationDoc document) {
                return document.copy().description(markdownEdit.getText()).build();
            }
        });
        addTab(PERMISSIONS, documentUserPermissionsTabProvider);

        selectTab(SETTINGS);
    }

    @Override
    protected void afterSelectTab(final MyPresenterWidget<?> content) {
        if (assetsTabProvider.isPresenter(content)) {
            onChange();
        }
    }

    @Override
    public String getType() {
        return VisualisationDoc.TYPE;
    }

    @Override
    protected TabData getPermissionsTab() {
        return PERMISSIONS;
    }

    @Override
    protected TabData getDocumentationTab() {
        return DOCUMENTATION;
    }

    /**
     * Asset content is held in a server-side draft/live store rather than in the VisualisationDoc, so
     * it is not captured by the normal onWrite() comparison. Surface the assets tab's dirty state here
     * so the Save button reflects pending draft asset changes (mirrors PipelinePresenter's handling of
     * its stepping editors).
     */
    @Override
    protected boolean hasAssociatedDirty() {
        return super.hasAssociatedDirty() || assetsTab.isDirty();
    }

    /**
     * Provide a callback to be inserted into the save chain after the save is complete.
     * @return The consumer for the callback. The second parameter will be the
     * consumer to call after this method has completed.
     */
    @Override
    public BiConsumer<VisualisationDoc, Consumer<VisualisationDoc>> getPostSaveCallback() {
        return this::saveAssets;
    }

    /**
     * Provide a callback to be inserted into the SaveAs chain after the document saveAs
     * has happened.
     * @return The consumer for the callback. The second parameter will be the
     * consumer to call after this method has completed.
     */
    @Override
    public BiConsumer<VisualisationDoc, Consumer<VisualisationDoc>> getPostSaveAsCallback() {
        return this::saveAsAssets;
    }

    /**
     * Called by DocumentPlugin to save the assets associated with the document.
     * Specified in getPostSaveCallback().
     * @param document The document that was written by all the data in all the tabs.
     * @param callback Thing to call when the assets have been saved.
     */
    public void saveAssets(final VisualisationDoc document, final Consumer<VisualisationDoc> callback) {
        assetsTab.onSave(document, callback);
    }

    /**
     * Called by DocumentPlugin to do a SaveAs to a new document.
     * Specified in getPostSaveAsCallback().
     * @param document The new document to save to.
     * @param callback Thing to call when the assets have been saved.
     */
    public void saveAsAssets(final VisualisationDoc document, final Consumer<VisualisationDoc> callback) {
        assetsTab.onSaveAs(document, callback);
    }
}
