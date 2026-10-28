/*
 * Copyright 2019 Crown Copyright
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

package stroom.search.elastic.client.component;

import stroom.config.global.client.ListDataProvider;
import stroom.docref.DocRef;
import stroom.preferences.client.DateTimeFormatter;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.search.elastic.shared.ElasticIndexField;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/// An Elastic index's Fields tab ([ElasticIndexFieldList]): the fields of the index read, by name.
public class ElasticIndexFieldListController extends Controller {

    private final ElasticIndexFieldList view;
    private final DateTimeFormatter dateTimeFormatter;
    private final ListDataProvider<ElasticIndexField> dataProvider = new ListDataProvider<>();

    /// Made by GIN, with its content.
    ///
    /// @param eventBus          The application's event bus.
    /// @param view              The content.
    /// @param dateTimeFormatter Formats when the fields were read.
    @Inject
    public ElasticIndexFieldListController(final EventBus eventBus,
                                           final ElasticIndexFieldList view,
                                           final DateTimeFormatter dateTimeFormatter) {
        super(eventBus);
        this.view = view;
        this.dateTimeFormatter = dateTimeFormatter;
        initWidget(view);
        view.setDataProvider(dataProvider);
    }

    /// Shows an index's fields.
    ///
    /// @param docRef   The index's reference.
    /// @param document The index, or `null`.
    public void read(final DocRef docRef, final ElasticIndexDoc document) {
        view.setIndexName(docRef.getName());
        final List<ElasticIndexField> fields;
        if (document != null) {
            fields = document.getFields().stream()
                    .sorted(Comparator.comparing(ElasticIndexField::getFldName, String.CASE_INSENSITIVE_ORDER))
                    .collect(Collectors.toList());
            view.setUpdatedAt(dateTimeFormatter.format(System.currentTimeMillis()));
        } else {
            fields = new ArrayList<>();
        }
        dataProvider.setCompleteList(fields);
    }
}
