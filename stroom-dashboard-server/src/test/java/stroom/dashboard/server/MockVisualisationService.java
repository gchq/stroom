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

package stroom.dashboard.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.MockDocumentEntityService;
import stroom.util.spring.StroomSpringProfiles;
import stroom.visualisation.shared.FindVisualisationCriteria;
import stroom.visualisation.shared.Visualisation;
import stroom.visualisation.shared.VisualisationService;

@Profile(StroomSpringProfiles.TEST)
@Component("visualisationService")
public class MockVisualisationService extends MockDocumentEntityService<Visualisation, FindVisualisationCriteria> implements VisualisationService {
    @Override
    public Class<Visualisation> getEntityClass() {
        return Visualisation.class;
    }
}
