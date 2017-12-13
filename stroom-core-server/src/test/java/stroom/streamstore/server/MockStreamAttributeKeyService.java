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

package stroom.streamstore.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.entity.server.MockEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.streamstore.shared.FindStreamAttributeKeyCriteria;
import stroom.streamstore.shared.StreamAttributeKey;
import stroom.util.spring.StroomSpringProfiles;

@Profile(StroomSpringProfiles.TEST)
@Component
public class MockStreamAttributeKeyService extends MockEntityService<StreamAttributeKey, FindStreamAttributeKeyCriteria>
        implements StreamAttributeKeyService {
    @Override
    public BaseResultList<StreamAttributeKey> findAll() throws RuntimeException {
        return find(new FindStreamAttributeKeyCriteria());
    }

    @Override
    public Class<StreamAttributeKey> getEntityClass() {
        return StreamAttributeKey.class;
    }
}
