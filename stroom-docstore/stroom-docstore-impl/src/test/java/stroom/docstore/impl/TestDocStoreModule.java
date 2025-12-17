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

package stroom.docstore.impl;

import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docrefinfo.api.DocRefInfoService;
import stroom.docrefinfo.mock.MockDocRefInfoService;
import stroom.docstore.api.DocumentSerialiser2;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.Store;
import stroom.docstore.api.StoreFactory;
import stroom.docstore.shared.AbstractDoc;
import stroom.event.logging.api.DocumentEventLog;
import stroom.security.api.SecurityContext;
import stroom.util.entityevent.EntityEventBus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.util.Providers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TestDocStoreModule {

    @Mock
    private Persistence persistenceMock;
    @Mock
    private EntityEventBus entityEventBus;
    @Mock
    private SecurityContext securityContextMock;
    @Mock
    private AbstractDoc docMock;

    @Inject
    StoreFactory storeFactory;

    @Test
    void testInjection() {
        final Injector injector = Guice.createInjector(new AbstractModule() {
            @Override
            protected void configure() {
                bind(Persistence.class).toInstance(persistenceMock);
                bind(EntityEventBus.class).toInstance(entityEventBus);
                bind(SecurityContext.class).toInstance(securityContextMock);
                bind(DocumentEventLog.class).toProvider(Providers.of(null));
                bind(DocRefInfoService.class).to(MockDocRefInfoService.class);
                bind(DocRefDecorator.class).to(MockDocRefInfoService.class);
                install(new DocStoreModule());
            }
        });

        final Serialiser2Factory serialiser2Factory = injector.getInstance(Serialiser2Factory.class);
        injector.injectMembers(this);
        final DocumentSerialiser2<MyDoc> serialiser = serialiser2Factory.createSerialiser(MyDoc.class);

        final Store<MyDoc> store2 = storeFactory.createStore(serialiser, "MyDocType", MyDoc::builder);
    }

    private static class MyDoc extends AbstractDoc {

        public MyDoc(@JsonProperty("uuid") final String uuid,
                     @JsonProperty("name") final String name,
                     @JsonProperty("version") final String version,
                     @JsonProperty("createTimeMs") final Long createTimeMs,
                     @JsonProperty("updateTimeMs") final Long updateTimeMs,
                     @JsonProperty("createUser") final String createUser,
                     @JsonProperty("updateUser") final String updateUser) {
            super("MyDoc", uuid, name, version, createTimeMs, updateTimeMs, createUser, updateUser);
        }

        public Builder copy() {
            return new Builder(this);
        }

        public static Builder builder() {
            return new Builder();
        }

        public static final class Builder extends AbstractDoc.AbstractBuilder<MyDoc, MyDoc.Builder> {

            private Builder() {
            }

            private Builder(final MyDoc doc) {
                super(doc);
            }

            @Override
            protected Builder self() {
                return this;
            }

            public MyDoc build() {
                return new MyDoc(
                        uuid,
                        name,
                        version,
                        createTimeMs,
                        updateTimeMs,
                        createUser,
                        updateUser);
            }
        }
    }
}
