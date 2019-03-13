/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.entity;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentType;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;
import java.util.Map;

@Singleton
public class DocumentActionHandlerRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(DocumentActionHandlerRegistry.class);
    private final Map<DocumentType, Provider<DocumentActionHandler>> entityServiceByType;

    @Inject
    DocumentActionHandlerRegistry(final Map<DocumentType, Provider<DocumentActionHandler>> entityServiceByType) {
        this.entityServiceByType = entityServiceByType;
    }

    public DocumentActionHandler getHandler(final String type) {
        final Provider<DocumentActionHandler> serviceProvider = entityServiceByType.get(new DocumentType(type));
        if (serviceProvider == null) {
            final String message = "No handler found for '" + type + "'";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }

        return serviceProvider.get();
    }
}