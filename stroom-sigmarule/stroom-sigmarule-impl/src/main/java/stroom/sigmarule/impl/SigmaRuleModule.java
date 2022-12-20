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

package stroom.sigmarule.impl;

import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.sigmarule.shared.SigmaRuleDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class SigmaRuleModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(SigmaRuleStore.class).to(SigmaRuleStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(SigmaRuleStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(SigmaRuleStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(SigmaRuleDoc.DOCUMENT_TYPE, SigmaRuleStoreImpl.class);

        // Provide object info to the logging service.
        ObjectInfoProviderBinder.create(binder())
                .bind(SigmaRuleDoc.class, SigmaRuleDocObjectInfoProvider.class);

        RestResourcesBinder.create(binder())
                .bind(SigmaRuleResourceImpl.class);
    }
}
