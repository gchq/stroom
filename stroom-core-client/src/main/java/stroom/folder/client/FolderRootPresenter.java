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

package stroom.folder.client;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.LinkTabPanelPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.explorer.shared.DocumentType;
import stroom.explorer.shared.ExplorerConstants;
import stroom.process.client.presenter.ProcessorPresenter;
import stroom.security.shared.PermissionNames;
import stroom.security.client.ClientSecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.streamstore.client.presenter.ClassificationWrappedStreamPresenter;
import stroom.streamstore.client.presenter.StreamTaskPresenter;
import stroom.svg.client.Icon;
import stroom.svg.client.SvgPreset;
import stroom.util.client.ImageUtil;
import stroom.docref.SharedObject;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class FolderRootPresenter extends LinkTabPanelPresenter implements TabData {
    private static final TabData DATA = new TabDataImpl("Data");
    private static final TabData TASKS = new TabDataImpl("Active Tasks");
    private static final TabData PROCESSORS = new TabDataImpl("Processors");

    private final ClientSecurityContext securityContext;
    private final TabContentProvider<SharedObject> tabContentProvider = new TabContentProvider<>();
    private ProcessorPresenter processorPresenter;

    @Inject
    public FolderRootPresenter(final EventBus eventBus,
                               final ClientSecurityContext securityContext,
                               final LinkTabPanelView view,
                               final Provider<ClassificationWrappedStreamPresenter> streamPresenterProvider,
                               final Provider<ProcessorPresenter> processorPresenterProvider,
                               final Provider<StreamTaskPresenter> streamTaskPresenterProvider) {
        super(eventBus, view);
        this.securityContext = securityContext;

        TabData selectedTab = null;

        if (securityContext.hasAppPermission(PermissionNames.VIEW_DATA_PERMISSION)) {
            addTab(DATA);
            tabContentProvider.add(DATA, streamPresenterProvider);
            selectedTab = DATA;
        }

        if (securityContext.hasAppPermission(PermissionNames.MANAGE_PROCESSORS_PERMISSION)) {
            addTab(PROCESSORS);
            tabContentProvider.add(PROCESSORS, processorPresenterProvider);
            addTab(TASKS);
            tabContentProvider.add(TASKS, streamTaskPresenterProvider);

            if (selectedTab == null) {
                selectedTab = PROCESSORS;
            }
        }

        selectTab(selectedTab);
    }

    @Override
    public void getContent(final TabData tab, final ContentCallback callback) {
        if (PROCESSORS.equals(tab) && this.processorPresenter == null) {
            this.processorPresenter = (ProcessorPresenter) tabContentProvider.getPresenter(tab);
            this.processorPresenter.setAllowUpdate(securityContext.hasAppPermission(PermissionNames.ADMINISTRATOR));
        }

        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getLabel() {
        return ExplorerConstants.SYSTEM;
    }

    @Override
    public Icon getIcon() {
        return new SvgPreset(ImageUtil.getImageURL() + DocumentType.DOC_IMAGE_URL + getLabel() + ".svg", null, true);
    }
}
