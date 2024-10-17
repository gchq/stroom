/*
 * Copyright 2019 Crown Copyright
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

package stroom.aws.s3.impl;

import stroom.aws.s3.shared.S3ConfigDoc;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.factory.PipelineElementModule;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

public class S3ConfigModule extends PipelineElementModule {

    @Override
    protected void configure() {
        super.configure();

        bind(S3ConfigStore.class).to(S3ConfigStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(S3ConfigStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(S3ConfigStoreImpl.class);
        GuiceUtil.buildMultiBinder(binder(), ContentIndexable.class)
                .addBinding(S3ConfigStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(S3ConfigDoc.DOCUMENT_TYPE, S3ConfigStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(S3ClientConfigCache.class);
    }

    @Override
    protected void configureElements() {
        bindElement(S3Appender.class);
    }
}
