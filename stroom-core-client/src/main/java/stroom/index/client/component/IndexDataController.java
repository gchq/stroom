/*
 * Copyright 2026 Crown Copyright
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

package stroom.index.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.IndexResource;
import stroom.index.shared.LuceneIndexDoc;
import stroom.query.api.Column;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.query.api.token.QuotedStringUtil;
import stroom.query.client.component.AbstractQueryDataController;
import stroom.query.client.component.QueryDataPane;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.util.shared.PageRequest;

import com.google.gwt.core.client.GWT;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/// An index's Data tab: a StroomQL query of the index's stored fields.
public class IndexDataController
        extends AbstractQueryDataController<LuceneIndexDoc> {


    private static final IndexResource INDEX_RESOURCE = GWT.create(IndexResource.class);

    private final RestFactory restFactory;
    private List<IndexFieldImpl> fields;
    private DocRef lastDocRef;

    /// Made by GIN, with its content and its parts.
    @Inject
    public IndexDataController(final EventBus eventBus,
                               final QueryDataPane view,
                               final QueryResultTableController tablePresenter,
                               final RestFactory restFactory,
                               final DateTimeSettingsFactory dateTimeSettingsFactory,
                               final ResultStoreModel resultStoreModel) {
        super(eventBus, view, tablePresenter, restFactory, dateTimeSettingsFactory, resultStoreModel);
        this.restFactory = restFactory;
    }

    @Override
    public void read(final DocRef docRef, final LuceneIndexDoc doc) {
        this.lastDocRef = docRef;
        final FindFieldCriteria criteria = new FindFieldCriteria(
                PageRequest.unlimited(),
                Collections.emptyList(),
                docRef,
                null,
                null);

        restFactory
                .create(INDEX_RESOURCE)
                .method(res -> res.findFields(criteria))
                .onSuccess(result -> {
                    if (Objects.equals(this.lastDocRef, docRef)) {
                        this.fields = result.getValues();
                        super.read(docRef, doc);
                    }
                })
                .exec();
    }

    @Override
    protected String getDefaultQuery(final DocRef docRef, final LuceneIndexDoc doc) {
        final StringBuilder sb = new StringBuilder();
        sb.append("from \"");
        sb.append(QuotedStringUtil.escapeDoubleQuoted(docRef.getName()));
        sb.append("\"");

        if (fields != null) {
            final List<String> storedFields = fields.stream()
                    .map(IndexField::getFldName)
                    .toList();

            if (!storedFields.isEmpty()) {
                sb.append(" limit 100 select ");

                final String timeField = doc.getTimeField();
                final List<String> orderedFields = new ArrayList<>();
                if (timeField != null && storedFields.contains(timeField)) {
                    orderedFields.add(timeField);
                }
                for (final String field : storedFields) {
                    if (!field.equals(timeField)) {
                        orderedFields.add(field);
                    }
                }

                sb.append(orderedFields.stream()
                        .map(this::formatFieldName)
                        .collect(Collectors.joining(", ")));
            }
        }
        return sb.toString();
    }

    @Override
    protected List<Column> getPreferredColumns(final LuceneIndexDoc doc) {
        if (fields == null) {
            return Collections.emptyList();
        }
        return fields.stream()
                .map(field -> Column.builder()
                        .id(field.getFldName())
                        .name(field.getFldName())
                        .expression(formatFieldName(field.getFldName()))
                        .build())
                .collect(Collectors.toList());
    }

    private String formatFieldName(final String fieldName) {
        if (fieldName == null) {
            return "";
        }
        if (fieldName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            return fieldName;
        } else {
            return "\"" + fieldName.replace("\"", "\"\"") + "\"";
        }
    }
}
