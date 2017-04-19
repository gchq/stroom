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

package stroom.pipeline.client.presenter;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import stroom.entity.client.presenter.ContentCallback;
import stroom.entity.client.presenter.EntityEditTabPresenter;
import stroom.entity.client.presenter.LinkTabPanelView;
import stroom.entity.client.presenter.TabContentProvider;
import stroom.pipeline.processor.client.presenter.ProcessorPresenter;
import stroom.pipeline.shared.PipelineEntity;
import stroom.pipeline.structure.client.presenter.PipelineStructurePresenter;
import stroom.security.client.ClientSecurityContext;
import stroom.streamstore.client.presenter.ClassificationWrappedStreamPresenter;
import stroom.streamstore.client.presenter.StreamTaskPresenter;
import stroom.streamstore.shared.Stream;
import stroom.streamtask.shared.StreamProcessor;
import stroom.widget.tab.client.presenter.TabData;
import stroom.widget.tab.client.presenter.TabDataImpl;

public class PipelinePresenter extends EntityEditTabPresenter<LinkTabPanelView, PipelineEntity> {
    public static final TabData SETTINGS = new TabDataImpl("Settings");
    public static final TabData DATA = new TabDataImpl("Data");
    public static final TabData STRUCTURE = new TabDataImpl("Structure");
    public static final TabData PROCESSORS = new TabDataImpl("Processors");
    public static final TabData TASKS = new TabDataImpl("Active Tasks");

    private final TabContentProvider<PipelineEntity> tabContentProvider = new TabContentProvider<PipelineEntity>();

    @Inject
    public PipelinePresenter(final EventBus eventBus, final LinkTabPanelView view,
                             final Provider<PipelineSettingsPresenter> settingsPresenter,
                             final Provider<ClassificationWrappedStreamPresenter> streamPresenterProvider,
                             final Provider<PipelineStructurePresenter> structurePresenter,
                             final Provider<ProcessorPresenter> processorPresenter,
                             final Provider<StreamTaskPresenter> streamTaskPresenterProvider,
                             final ClientSecurityContext securityContext) {
        super(eventBus, view, securityContext);

        tabContentProvider.setDirtyHandler(event -> {
            if (event.isDirty()) {
                setDirty(true);
            }
        });

        tabContentProvider.add(SETTINGS, settingsPresenter);
        addTab(SETTINGS);

        if (securityContext.hasAppPermission(Stream.VIEW_DATA_PERMISSION)) {
            tabContentProvider.add(DATA, streamPresenterProvider);
            addTab(DATA);
        }

        tabContentProvider.add(STRUCTURE, structurePresenter);
        addTab(STRUCTURE);

        if (securityContext.hasAppPermission(StreamProcessor.MANAGE_PROCESSORS_PERMISSION)) {
            tabContentProvider.add(PROCESSORS, processorPresenter);
            addTab(PROCESSORS);
            tabContentProvider.add(TASKS, streamTaskPresenterProvider);
            addTab(TASKS);
        }

        selectTab(SETTINGS);
    }

    @Override
    public void getContent(final TabData tab, final ContentCallback callback) {
        callback.onReady(tabContentProvider.getPresenter(tab));
    }

    @Override
    public void onRead(final PipelineEntity pipelineEntity) {
        tabContentProvider.read(pipelineEntity);
    }

    @Override
    protected void onWrite(final PipelineEntity pipelineEntity) {
        tabContentProvider.write(pipelineEntity);
    }

    @Override
    public String getType() {
        return PipelineEntity.ENTITY_TYPE;
    }

    public interface HasVisible {
        void setVisible(boolean visible);
    }
}
