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

package stroom.statistics.server.sql.datasource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import stroom.entity.server.FolderService;
import stroom.explorer.server.AbstractExplorerDataProvider;
import stroom.explorer.server.ProvidesExplorerData;
import stroom.explorer.server.TreeModel;
import stroom.explorer.shared.ExplorerData;
import stroom.node.server.StroomPropertyService;
import stroom.statistics.shared.StatisticStoreEntity;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.HashSet;
import java.util.Set;

@ProvidesExplorerData
@Component
public class StatisticsDataSourceExplorerDataProvider
        extends AbstractExplorerDataProvider<StatisticStoreEntity, FindStatisticsEntityCriteria> {
    private static final Logger LOGGER = LoggerFactory.getLogger(StatisticsDataSourceExplorerDataProvider.class);

    private static final Set<String> tags = new HashSet<>();

    static {
        tags.add("DataSource");
    }

    private final StatisticStoreEntityService statisticsDataSourceService;
    private final StroomPropertyService stroomPropertyService;

    @Inject
    StatisticsDataSourceExplorerDataProvider(@Named("cachedFolderService") final FolderService cachedFolderService,
                                             final StatisticStoreEntityService statisticsDataSourceService,
                                             final StroomPropertyService stroomPropertyService) {
        super(cachedFolderService);
        LOGGER.debug("Initialising: {}", this.getClass().getCanonicalName());
        this.statisticsDataSourceService = statisticsDataSourceService;
        this.stroomPropertyService = stroomPropertyService;
    }

    @Override
    public void addItems(final TreeModel treeModel) {
        final FindStatisticsEntityCriteria criteria = FindStatisticsEntityCriteria.instance();

        addItems(statisticsDataSourceService, treeModel, criteria);
    }

    @Override
    protected ExplorerData createEntityData(final StatisticStoreEntity entity) {
        final ExplorerData entityData = super.createEntityData(entity);
        entityData.setTags(tags);
        return entityData;
    }

    @Override
    public String getType() {
        return StatisticStoreEntity.ENTITY_TYPE;
    }

    @Override
    public String getDisplayType() {
        return StatisticStoreEntity.ENTITY_TYPE_FOR_DISPLAY;
    }

    @Override
    public int getPriority() {
        return 11;
    }
}
