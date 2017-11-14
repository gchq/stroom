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

package stroom.index.server;

import org.springframework.stereotype.Component;
import stroom.index.shared.Index;
import stroom.index.shared.IndexService;
import stroom.query.shared.DataSource;
import stroom.query.shared.DataSourceProvider;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Component
public class LuceneIndexDataSourceProvider implements DataSourceProvider {
    public static final String ENTITY_TYPE = Index.ENTITY_TYPE;

    private final IndexService indexService;
    private final SecurityContext securityContext;

    @Inject
    public LuceneIndexDataSourceProvider(final IndexService indexService, final SecurityContext securityContext) {
        this.indexService = indexService;
        this.securityContext = securityContext;
    }

    @Override
    public DataSource getDataSource(final String uuid) {
        securityContext.elevatePermissions();
        try {
            return indexService.loadByUuid(uuid);
        } finally {
            securityContext.restorePermissions();
        }
    }

    @Override
    public String getEntityType() {
        return ENTITY_TYPE;
    }
}
