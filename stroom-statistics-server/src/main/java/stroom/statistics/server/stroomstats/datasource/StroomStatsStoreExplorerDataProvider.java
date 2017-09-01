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

package stroom.statistics.server.stroomstats.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.shared.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.EntityData;
import stroom.statistics.server.stroomstats.entity.FindStroomStatsStoreEntityCriteria;
import stroom.statistics.server.stroomstats.entity.StroomStatsStoreEntityService;
import stroom.stats.shared.StroomStatsStoreEntity;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashSet;
import java.util.Set;

@ProvidesExplorerData
@Component
public class StroomStatsStoreExplorerDataProvider
        extends AbstractExplorerDataProvider<StroomStatsStoreEntity, FindStroomStatsStoreEntityCriteria> {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomStatsStoreExplorerDataProvider.class);

    private static final Set<String> tags = new HashSet<>();

    static {
        tags.add("DataSource");
    }

    private final StroomStatsStoreEntityService stroomStatsStoreEntityService;

    @Inject
    StroomStatsStoreExplorerDataProvider(@Named("cachedFolderService") final FolderService folderService,
                                         final StroomStatsStoreEntityService stroomStatsStoreEntityService) {
        super(folderService);
        LOGGER.debug("Initialising: {}", this.getClass().getCanonicalName());
        this.stroomStatsStoreEntityService = stroomStatsStoreEntityService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {

        addItems(stroomStatsStoreEntityService, treeModel);
    }

    @Override
    protected EntityData createEntityData(final StroomStatsStoreEntity entity) {
        final EntityData entityData = super.createEntityData(entity);
        entityData.setTags(tags);
        return entityData;
    }

    @Override
    public String getType() {
        return StroomStatsStoreEntity.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return StroomStatsStoreEntity.ENTITY_TYPE_FOR_DISPLAY;
    }

    @Override
    public int getPriority() {
        return 12;
    }
}
