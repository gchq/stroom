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

package stroom.ai.api;

import stroom.docref.DocRef;
import stroom.docstore.api.DocumentStore;
import stroom.openai.shared.OpenAIModelDoc;

import java.util.List;
import java.util.Optional;

public interface OpenAIModelStore extends DocumentStore<OpenAIModelDoc> {

    List<DocRef> list();

    /**
     * @return The {@link DocRef}s of all models with the supplied name that the current user can view.
     * Names are not unique so this may return more than one.
     */
    List<DocRef> findByName(String name);

    /**
     * @return The {@link DocRef} for the supplied UUID, or empty if there is no such model or the current
     * user cannot view it.
     */
    Optional<DocRef> findByUuid(String uuid);
}
