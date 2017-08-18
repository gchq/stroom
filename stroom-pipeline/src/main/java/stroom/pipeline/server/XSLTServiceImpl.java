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

package stroom.pipeline.server;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import stroom.dashboard.server.logging.DocumentEventLog;
import stroom.entity.server.DocumentEntityServiceImpl;
import stroom.entity.server.util.StroomEntityManager;
import stroom.importexport.server.ImportExportHelper;
import stroom.pipeline.shared.FindXSLTCriteria;
import stroom.pipeline.shared.XSLT;
import stroom.security.SecurityContext;

import javax.inject.Inject;

@Component
@Transactional
public class XSLTServiceImpl extends DocumentEntityServiceImpl<XSLT, FindXSLTCriteria> implements XSLTService {
    @Inject
    XSLTServiceImpl(final StroomEntityManager entityManager, final ImportExportHelper importExportHelper, final SecurityContext securityContext, final DocumentEventLog documentEventLog) {
        super(entityManager, importExportHelper, securityContext, documentEventLog);
    }

    @Override
    public Class<XSLT> getEntityClass() {
        return XSLT.class;
    }

//    @Override
//    protected List<EntityReferenceQuery> getReferenceTableList() {
//        final boolean mySql = stroomDatabaseInfo.isMysql();
//        final ArrayList<EntityReferenceQuery> rtnList = new ArrayList<>();
//        if (mySql) {
//            rtnList.add(new EntityReferenceQuery(PipelineEntity.ENTITY_TYPE, PipelineEntity.TABLE_NAME,
//                    PipelineEntity.DATA + " regexp '<type>@TYPE@</type>[[:space:]]*<id>@ID@</id>'"));
//            rtnList.add(new EntityReferenceQuery(XSLT.ENTITY_TYPE, XSLT.TABLE_NAME,
//                    XSLT.DATA + " regexp 'import[[:space:]]href=\"@NAME@\"'"));
//        } else {
//            // This won't work too well as we really need to match with a regex
//            // that we can only do in MySQL
//            rtnList.add(new EntityReferenceQuery(PipelineEntity.ENTITY_TYPE, PipelineEntity.TABLE_NAME,
//                    "(locate('<type>@TYPE@</type>', CAST(" + PipelineEntity.DATA
//                            + " AS LONGVARCHAR)) <> 0 AND locate('<id>@ID@</id>', CAST(" + PipelineEntity.DATA
//                            + " AS LONGVARCHAR)) <> 0)"));
//            rtnList.add(new EntityReferenceQuery(XSLT.ENTITY_TYPE, XSLT.TABLE_NAME,
//                    "locate('xsl:import href=\"@NAME@\"', CAST(" + XSLT.DATA + " AS LONGVARCHAR)) <> 0"));
//
//        }
//
//        return rtnList;
//    }

    @Override
    public FindXSLTCriteria createCriteria() {
        return new FindXSLTCriteria();
    }
}
