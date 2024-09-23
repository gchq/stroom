/*
 * Copyright 2024 Crown Copyright
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

import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.QueryField;
import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardFields;
import stroom.query.common.v2.FieldInfoResultPageBuilder;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ValuesConsumer;
import stroom.searchable.api.Searchable;
import stroom.security.api.SecurityContext;
import stroom.security.shared.PermissionNames;
import stroom.util.NullSafe;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Optional;

@Singleton
public class IndexShardServiceImpl implements IndexShardService, Searchable {

    private final SecurityContext securityContext;
    private final IndexShardDao indexShardDao;

    @Inject
    IndexShardServiceImpl(final SecurityContext securityContext,
                          final IndexShardDao indexShardDao) {
        this.securityContext = securityContext;
        this.indexShardDao = indexShardDao;
    }

    @Override
    public ResultPage<IndexShard> find(final FindIndexShardCriteria criteria) {
        return securityContext.secureResult(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION,
                () -> indexShardDao.find(criteria));
    }

    @Override
    public DocRef getDocRef() {
        return IndexShardFields.INDEX_SHARDS_PSEUDO_DOC_REF;
    }

    @Override
    public ResultPage<QueryField> getFieldInfo(final FindFieldCriteria criteria) {
        return FieldInfoResultPageBuilder.builder(criteria).addAll(getFields()).build();
    }

    private List<QueryField> getFields() {
        return IndexShardFields.getFields();
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return NullSafe.size(getFields());
    }

    @Override
    public Optional<String> fetchDocumentation(final DocRef docRef) {
        return Optional.empty();
    }

    @Override
    public QueryField getTimeField() {
        return null;
    }

    @Override
    public void search(final ExpressionCriteria criteria,
                       final FieldIndex fieldIndex,
                       final ValuesConsumer consumer) {
        securityContext.secure(PermissionNames.MANAGE_INDEX_SHARDS_PERMISSION, () ->
                indexShardDao.search(criteria, fieldIndex, consumer));
    }
}
