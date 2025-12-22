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

package stroom.docstore.api;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Singleton
public class DocumentActionHandlers {

    private final Map<DocumentTypeName, DocumentActionHandler> handlersMap;

    @Inject
    public DocumentActionHandlers(final Map<DocumentTypeName, DocumentActionHandler> handlersMap) {
        this.handlersMap = handlersMap;
    }

    public DocumentActionHandler<?> getHandler(final String type) {
        return handlersMap.get(new DocumentTypeName(type));
    }

    public void forEach(final Consumer<DocumentActionHandler> consumer) {
        handlersMap.values()
                .stream()
                .filter(Objects::nonNull)
                .forEach(consumer);
    }

    public Stream<DocumentActionHandler> stream() {
        return handlersMap.values()
                .stream()
                .filter(Objects::nonNull);
    }
}
