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
 */

package stroom.datasource.api.v2;

import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import java.util.List;
import java.util.Optional;

public interface DataSourceProvider {

    List<DocRef> list();

    String getType();

    ResultPage<QueryField> getFieldInfo(FindFieldInfoCriteria criteria);

    Optional<String> fetchDocumentation(DocRef docRef);

    DocRef fetchDefaultExtractionPipeline(DocRef docRef);
}
