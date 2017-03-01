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

package stroom.statistics.server.common.engines;

import org.junit.Assert;
import org.junit.Test;
import stroom.entity.server.MockDocumentEntityService;
import stroom.entity.shared.BaseResultList;
import stroom.explorer.server.TreeModelImpl;
import stroom.explorer.shared.ExplorerData;
import stroom.node.server.MockStroomPropertyService;
import stroom.statistics.common.CommonStatisticConstants;
import stroom.statistics.common.FindStatisticsEntityCriteria;
import stroom.statistics.common.StatisticStoreEntityService;
import stroom.statistics.shared.StatisticStoreEntity;

import java.util.List;
import java.util.Map;

public class StatisticsDataSourceExplorerDataProviderTest {
    @Test
    public void testAddItems_NullEnabledStores() throws Exception {
        final FindStatisticsEntityCriteria criteria = doTest(null);

        Assert.assertTrue(criteria.getEngineNames() != null);
        Assert.assertEquals(1, criteria.getEngineNames().size());

    }

    @Test
    public void testAddItems_EmptyStringEnabledStores() throws Exception {
        final FindStatisticsEntityCriteria criteria = doTest("");

        Assert.assertTrue(criteria.getEngineNames() != null);
        Assert.assertEquals(1, criteria.getEngineNames().size());

    }

    @Test
    public void testAddItems_OneStore() throws Exception {
        final FindStatisticsEntityCriteria criteria = doTest("SQL");

        Assert.assertTrue(criteria.getEngineNames() != null);
        Assert.assertEquals(2, criteria.getEngineNames().size());

    }


    private FindStatisticsEntityCriteria doTest(final String statEnginesPropValue) {
        final MockStroomPropertyService stroomPropertyService = new MockStroomPropertyService();
        stroomPropertyService.setProperty(CommonStatisticConstants.STROOM_STATISTIC_ENGINES_PROPERTY_NAME,
                statEnginesPropValue);

        final MockStatisticsDataSourceService mockStatisticsDataSourceService = new MockStatisticsDataSourceService();

        final StatisticsDataSourceExplorerDataProvider explorerDataProvider = new StatisticsDataSourceExplorerDataProvider(
                null, mockStatisticsDataSourceService, stroomPropertyService);

        explorerDataProvider.addItems(new TreeModelImpl() {
            @Override
            public void add(ExplorerData parent, ExplorerData child) {
            }

            @Override
            public Map<ExplorerData, List<ExplorerData>> getChildMap() {
                return null;
            }
        });

        final FindStatisticsEntityCriteria criteria = mockStatisticsDataSourceService.getCriteria();

        return criteria;

    }

    private static class MockStatisticsDataSourceService extends MockDocumentEntityService<StatisticStoreEntity, FindStatisticsEntityCriteria> implements StatisticStoreEntityService {
        private FindStatisticsEntityCriteria criteria;

        public FindStatisticsEntityCriteria getCriteria() {
            return criteria;
        }

        @Override
        public BaseResultList<StatisticStoreEntity> find(final FindStatisticsEntityCriteria criteria)
                throws RuntimeException {
            this.criteria = criteria;
            return super.find(criteria);
        }

        @Override
        public Class<StatisticStoreEntity> getEntityClass() {
            return StatisticStoreEntity.class;
        }
    }
}
