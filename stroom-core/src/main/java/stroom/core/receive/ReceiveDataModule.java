/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.core.receive;

import stroom.receive.common.CertificateExtractorImpl;
import stroom.receive.common.ContentAutoCreationAttrMapFilterFactory;
import stroom.receive.common.FeedStatusService;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.RequestHandler;
import stroom.util.cert.CertificateExtractor;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public class ReceiveDataModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(CertificateExtractor.class).to(CertificateExtractorImpl.class);
        bind(ContentAutoCreationService.class).to(ContentAutoCreationServiceImpl.class);
        bind(ContentAutoCreationAttrMapFilterFactory.class).to(StroomContentAutoCreationAttrMapFactoryImpl.class);
        bind(FeedStatusService.class).to(FeedStatusServiceImpl.class);
        bind(ReceiptIdGenerator.class).to(StroomReceiptIdGenerator.class).asEagerSingleton();
        bind(RequestHandler.class).to(ReceiveDataRequestHandler.class);
        bind(ContentTemplateStore.class).to(ContentTemplateStoreImpl.class);
        bind(ContentTemplateService.class).to(ContentTemplateServiceImpl.class);

        RestResourcesBinder.create(binder())
                .bind(ContentTemplateResourceImpl.class);
    }
}
