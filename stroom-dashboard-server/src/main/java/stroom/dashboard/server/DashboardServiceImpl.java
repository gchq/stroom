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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.logging.DocumentEventLog;
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.FindDashboardCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.QueryAppender;
import stroom.entity.server.util.SqlBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.DocRefUtil;
import stroom.importexport.server.ImportExportHelper;
import stroom.security.SecurityContext;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;

@Component
@Transactional
@AutoMarshal
public class DashboardServiceImpl extends DocumentEntityServiceImpl<Dashboard, FindDashboardCriteria>
        implements DashboardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private final ResourceLoader resourceLoader;
    private String xmlTemplate;

    @Inject
    DashboardServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final DocumentEventLog documentEventLog, final ResourceLoader resourceLoader) {
        super(entityManager, importExportHelper, securityContext, documentEventLog);
        this.resourceLoader = resourceLoader;
    }

    @Override
    public Class<Dashboard> getEntityClass() {
        return Dashboard.class;
    }

    @Override
    public FindDashboardCriteria createCriteria() {
        return new FindDashboardCriteria();
    }

//    @Override
//    protected Dashboard create(final Dashboard entity) {
//        final Dashboard dashboard = super.create(entity);
//        // Add the template.
//        if (dashboard.getData() == null) {
//            dashboard.setData(getTemplate());
//        }
//        return super.save(dashboard);
//    }
//
//    @Override
//    public Dashboard save(Dashboard entity) throws RuntimeException {
//        if (entity.getData() == null) {
//            entity.setData(getTemplate());
//        }
//        return super.save(entity);
//    }

    @Override
    public Boolean delete(final Dashboard entity) throws RuntimeException {
        checkDeletePermission(DocRefUtil.create(entity));

        // Delete associated queries first.
        final SqlBuilder sql = new SqlBuilder();
        sql.append("DELETE FROM ");
        sql.append(QueryEntity.TABLE_NAME);
        sql.append(" WHERE ");
        sql.append(Dashboard.FOREIGN_KEY);
        sql.append(" = ");
        sql.arg(entity.getId());
        getEntityManager().executeNativeUpdate(sql);

        return super.delete(entity);
    }

    private String getTemplate() {
        if (xmlTemplate == null) {
            try {
                final org.springframework.core.io.Resource resource = resourceLoader
                        .getResource("classpath:/stroom/dashboard/DashboardTemplate.data.xml");
                xmlTemplate = StreamUtil.streamToString(resource.getInputStream());
            } catch (final Exception e) {
                LOGGER.error(e.getMessage(), e);
            }

            // Don't try and load this template again if it fails.
            if (xmlTemplate == null) {
                xmlTemplate = "";
            }
        }

        return xmlTemplate;
    }

    @Override
    protected QueryAppender<Dashboard, FindDashboardCriteria> createQueryAppender(final StroomEntityManager entityManager) {
        return new DashboardQueryAppender(entityManager, this);
    }

    private static class DashboardQueryAppender extends QueryAppender<Dashboard, FindDashboardCriteria> {
        private final DashboardServiceImpl dashboardService;
        private final DashboardMarshaller marshaller;

        DashboardQueryAppender(final StroomEntityManager entityManager, final DashboardServiceImpl dashboardService) {
            super(entityManager);
            this.dashboardService = dashboardService;
            this.marshaller = new DashboardMarshaller();
        }

        @Override
        protected void postLoad(final Dashboard entity) {
            super.postLoad(entity);
            marshaller.unmarshal(entity);
        }

        @Override
        protected void preSave(final Dashboard entity) {
            super.preSave(entity);

            if (entity.getData() == null && entity.getDashboardData() == null) {
                entity.setData(dashboardService.getTemplate());
            } else {
                marshaller.marshal(entity);
            }
        }
    }
}
