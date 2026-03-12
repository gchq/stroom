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
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.query.api.StringExpressionUtil;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Collection;
import java.util.Collections;

@Singleton
public class IndexFieldServiceImpl implements IndexFieldService {

    private final IndexFieldDao indexFieldDao;
    private final SecurityContext securityContext;

    @Inject
    public IndexFieldServiceImpl(final IndexFieldDao indexFieldDao,
                                 final SecurityContext securityContext) {
        this.indexFieldDao = indexFieldDao;
        this.securityContext = securityContext;
    }

    @Override
    public void addFields(final DocRef docRef, final Collection<IndexField> fields) {
        indexFieldDao.addFields(docRef, fields);
    }

    @Override
    public ResultPage<IndexField> findFields(final FindFieldCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();

        // Check for read permission.
        if (docRef == null || !securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
            // If there is no read permission then return no fields.
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }
        return indexFieldDao.findFields(criteria);
    }

    @Override
    public Boolean addField(final AddField addField) {
        if (checkEditPermission(addField.getIndexDocRef())) {
            indexFieldDao.addField(addField);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean updateField(final UpdateField updateField) {
        if (checkEditPermission(updateField.getIndexDocRef())) {
            indexFieldDao.updateField(updateField);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean deleteField(final DeleteField deleteField) {
        if (checkEditPermission(deleteField.getIndexDocRef())) {
            indexFieldDao.deleteField(deleteField);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private boolean checkEditPermission(final DocRef docRef) {
        return docRef != null && securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
    }

    @Override
    public int getFieldCount(final DocRef docRef) {
        return indexFieldDao.getFieldCount(docRef);
    }

    @Override
    public IndexField getIndexField(final DocRef docRef, final String fieldName) {
        return securityContext.useAsReadResult(() -> {

            // Check for read permission.
            if (!securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                // If there is no read permission then return no fields.
                return null;
            }

            final FindFieldCriteria findIndexFieldCriteria = new FindFieldCriteria(
                    PageRequest.oneRow(),
                    FindFieldCriteria.DEFAULT_SORT_LIST,
                    docRef,
                    StringExpressionUtil.equalsCaseSensitive(fieldName),
                    null);
            final ResultPage<IndexField> resultPage = findFields(findIndexFieldCriteria);
            if (!resultPage.isEmpty()) {
                return resultPage.getFirst();
            }
            return null;
        });
    }

    @Override
    public void deleteAll(final DocRef docRef) {
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
            indexFieldDao.deleteAll(docRef);
        }
    }

    @Override
    public void copyAll(final DocRef source, final DocRef dest) {
        if (securityContext.hasDocumentPermission(source, DocumentPermission.VIEW)) {
            indexFieldDao.copyAll(source, dest);
        }
    }

    @Override
    public String getDataSourceType() {
        return LuceneIndexDoc.TYPE;
    }
}
