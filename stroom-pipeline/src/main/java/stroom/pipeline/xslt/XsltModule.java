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

package stroom.pipeline.xslt;

import com.google.inject.AbstractModule;
import stroom.docstore.api.DocumentActionHandlerBinder;
import stroom.explorer.api.ExplorerActionHandler;
import stroom.importexport.api.ImportExportActionHandler;
import stroom.pipeline.shared.XsltDoc;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.RestResource;

import javax.xml.transform.URIResolver;

public class XsltModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(XsltStore.class).to(XsltStoreImpl.class);
        bind(URIResolver.class).to(CustomURIResolver.class);

        GuiceUtil.buildMultiBinder(binder(), ExplorerActionHandler.class)
                .addBinding(XsltStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), ImportExportActionHandler.class)
                .addBinding(XsltStoreImpl.class);

        DocumentActionHandlerBinder.create(binder())
                .bind(XsltDoc.DOCUMENT_TYPE, XsltStoreImpl.class);

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(XsltResourceImpl.class);
    }
}