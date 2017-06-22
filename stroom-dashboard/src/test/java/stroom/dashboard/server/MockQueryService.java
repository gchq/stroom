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

package stroom.dashboard.server;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import stroom.dashboard.shared.FindQueryCriteria;
import stroom.dashboard.shared.Query;
import stroom.dashboard.shared.QueryService;
import stroom.entity.server.GenericEntityService;
import stroom.entity.server.MockDocumentEntityService;
import stroom.importexport.server.EntityPathResolver;
import stroom.util.spring.StroomSpringProfiles;

import javax.inject.Inject;
import java.util.List;

@Profile(StroomSpringProfiles.TEST)
@Component("queryService")
public class MockQueryService extends MockDocumentEntityService<Query, FindQueryCriteria> implements QueryService {
    @Inject
    public MockQueryService(final GenericEntityService genericEntityService, final EntityPathResolver entityPathResolver) {
        super(genericEntityService, entityPathResolver);
    }

    @Override
    public void clean(final String user, final boolean favourite, final Integer oldestId, final long oldestCrtMs) {

    }

    @Override
    public List<String> getUsers(final boolean favourite) {
        return null;
    }

    @Override
    public Integer getOldestId(final String user, final boolean favourite, final int retain) {
        return null;
    }

    @Override
    public Class<Query> getEntityClass() {
        return Query.class;
    }
}
