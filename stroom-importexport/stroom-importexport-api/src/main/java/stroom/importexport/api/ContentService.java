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

package stroom.importexport.api;

import stroom.docref.DocRef;
import stroom.importexport.shared.Dependency;
import stroom.importexport.shared.DependencyCriteria;
import stroom.importexport.shared.ImportConfigRequest;
import stroom.importexport.shared.ImportConfigResponse;
import stroom.util.shared.DocRefs;
import stroom.util.shared.ResourceGeneration;
import stroom.util.shared.ResourceKey;
import stroom.util.shared.ResultPage;

import java.util.Map;
import java.util.Set;

public interface ContentService {

    ImportConfigResponse importContent(ImportConfigRequest request);

    void abortImport(ResourceKey resourceKey);

    ResourceGeneration exportContent(DocRefs docRefs);

    ResultPage<Dependency> fetchDependencies(DependencyCriteria criteria);

    Map<DocRef, Set<DocRef>> fetchBrokenDependencies();

    ResourceKey exportAll();
}
