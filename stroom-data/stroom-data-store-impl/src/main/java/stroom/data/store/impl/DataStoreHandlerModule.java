/*
 * Copyright 2018 Crown Copyright
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

package stroom.data.store.impl;

import com.google.inject.AbstractModule;
import stroom.data.shared.DownloadDataAction;
import stroom.data.shared.UploadDataAction;
import stroom.meta.shared.FetchFullMetaInfoAction;
import stroom.meta.shared.FindMetaRowAction;
import stroom.meta.shared.UpdateStatusAction;
import stroom.pipeline.shared.FetchDataAction;
import stroom.pipeline.shared.FetchDataWithPipelineAction;
import stroom.task.api.TaskHandlerBinder;
import stroom.util.shared.RestResource;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.ServletBinder;

public class DataStoreHandlerModule extends AbstractModule {
    @Override
    protected void configure() {
        TaskHandlerBinder.create(binder())
                .bind(FetchDataAction.class, FetchDataHandler.class)
                .bind(FetchDataWithPipelineAction.class, FetchDataWithPipelineHandler.class)
                .bind(FetchFullMetaInfoAction.class, FetchFullMetaInfoHandler.class)
                .bind(FindMetaRowAction.class, FindMetaRowHandler.class)
                .bind(UpdateStatusAction.class, UpdateStatusHandler.class)
                .bind(DownloadDataAction.class, DownloadDataHandler.class)
                .bind(DataDownloadTask.class, DataDownloadTaskHandler.class)
                .bind(StreamUploadTask.class, StreamUploadTaskHandler.class)
                .bind(UploadDataAction.class, UploadDataHandler.class);

        ServletBinder.create(binder())
                .bind(ImportFileServlet.class);

        // TODO probably not the right place for this binding
        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(DataResource.class);
    }
}