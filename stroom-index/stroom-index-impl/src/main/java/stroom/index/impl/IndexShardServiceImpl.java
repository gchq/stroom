/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.index.impl;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardFields;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.FieldInfoResultPageFactory;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.shared.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collections;
import java.util.List;

@Singleton
public class IndexShardServiceImpl implements IndexShardService, Searchable {

    private final SecurityContext securityContext;
    private final IndexShardDao indexShardDao;
    private final FieldInfoResultPageFactory fieldInfoResultPageFactory;

    @Inject
    IndexShardServiceImpl(final SecurityContext securityContext,
                          final IndexShardDao indexShardDao,
                          final FieldInfoResultPageFactory fieldInfoResultPageFactory) {
        this.securityContext = securityContext;
        this.indexShardDao = indexShardDao;
        this.fieldInfoResultPageFactory = fieldInfoResultPageFactory;
    }

    @Override
    public ResultPage<IndexShard> find(final FindIndexShardCriteria criteria) {
        return securityContext.secureResult(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION,
                () -> indexShardDao.find(criteria));
    }

    @Override
    public String getDataSourceType() {
        return IndexShardFields.INDEX_SHARDS_PSEUDO_DOC_REF.getType();
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return Collections.singletonList(IndexShardFields.INDEX_SHARDS_PSEUDO_DOC_REF);
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        if (!IndexShardFields.INDEX_SHARDS_PSEUDO_DOC_REF.equals(criteria.getDataSourceRef())) {
            return ResultPage.empty();
        }
        return fieldInfoResultPageFactory.create(criteria, getFields());
    }

    private List<QueryField> getFields() {
        return IndexShardFields.getFields();
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final DateTimeSettings dateTimeSettings,
                       final ValuesConsumer consumer) {
        securityContext.secure(AppPermission.MANAGE_INDEX_SHARDS_PERMISSION, () ->
                indexShardDao.search(criteria, fieldIndex, consumer));
    }
}
