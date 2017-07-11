/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import stroom.dashboard.shared.Dashboard;
import stroom.dashboard.shared.DashboardService;
import stroom.dashboard.shared.FindDashboardCriteria;
import stroom.dashboard.shared.QueryEntity;
import stroom.entity.server.AutoMarshal;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.util.SQLBuilder;
import stroom.entity.server.util.StroomEntityManager;
import stroom.entity.shared.PermissionInheritance;
import stroom.importexport.server.ImportExportHelper;
import stroom.query.api.v1.DocRef;
import stroom.security.SecurityContext;
import stroom.util.io.StreamUtil;

import javax.inject.Inject;
import java.util.Arrays;

@Component
@Transactional
@AutoMarshal
public class DashboardServiceImpl extends DocumentEntityServiceImpl<Dashboard, FindDashboardCriteria>
        implements DashboardService {
    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardServiceImpl.class);

    private static final String[] PERMISSIONS = Arrays.copyOf(STANDARD_PERMISSIONS, STANDARD_PERMISSIONS.length + 1);

    static {
        PERMISSIONS[PERMISSIONS.length - 1] = "Download";
    }

    private final ResourceLoader resourceLoader;
    private String xmlTemplate;

    @Inject
    DashboardServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final ResourceLoader resourceLoader) {
        super(entityManager, importExportHelper, securityContext);
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

    @Override
    public Dashboard create(final DocRef folder, final String name, final PermissionInheritance permissionInheritance) throws RuntimeException {
        final Dashboard dashboard = super.create(folder, name, permissionInheritance);
        // Add the template.
        if (dashboard.getData() == null) {
            dashboard.setData(getTemplate());
        }
        return super.save(dashboard);
    }

    @Override
    public Dashboard save(Dashboard entity) throws RuntimeException {
        if (entity.getData() == null) {
            entity.setData(getTemplate());
        }
        return super.save(entity);
    }

    @Override
    public Boolean delete(final Dashboard entity) throws RuntimeException {
        checkDeletePermission(entity);

        // Delete associated queries first.
        final SQLBuilder sql = new SQLBuilder();
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
    public String[] getPermissions() {
        return PERMISSIONS;
    }
}
