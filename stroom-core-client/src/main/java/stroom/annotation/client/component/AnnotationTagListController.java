/*
 * Copyright 2025 Crown Copyright
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

package stroom.annotation.client.component;

import stroom.annotation.client.AnnotationResourceClient;
import stroom.annotation.shared.AnnotationTag;
import stroom.annotation.shared.AnnotationTagType;
import stroom.annotation.shared.FindAnnotationTagCriteria;
import stroom.data.client.presenter.CriteriaUtil;
import stroom.data.client.presenter.RestDataProvider;
import stroom.dispatch.client.RestErrorHandler;
import stroom.util.shared.ResultPage;
import stroom.widget.button.client.Toolbar;
import stroom.widget.component.client.Controller;
import stroom.widget.util.client.MultiSelectionModel;

import com.google.gwt.view.client.Range;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The annotation tags of the type its owner gives ([AnnotationTagList]), a page at a time.
public class AnnotationTagListController extends Controller {

    private final AnnotationTagList view;
    private final AnnotationResourceClient annotationResourceClient;

    private RestDataProvider<AnnotationTag, ResultPage<AnnotationTag>> dataProvider;
    private AnnotationTagType annotationTagType;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                 The application's event bus.
    /// @param view                     The content.
    /// @param annotationResourceClient Finds the tags.
    @Inject
    public AnnotationTagListController(final EventBus eventBus,
                                       final AnnotationTagList view,
                                       final AnnotationResourceClient annotationResourceClient) {
        super(eventBus);
        this.view = view;
        this.annotationResourceClient = annotationResourceClient;
        initWidget(view);
    }

    /// @return The selection.
    public MultiSelectionModel<AnnotationTag> getSelectionModel() {
        return view.getSelectionModel();
    }

    /// The toolbar beside the list's pager, for the screen's own buttons.
    ///
    /// @return The toolbar.
    public Toolbar getToolbar() {
        return view.getToolbar();
    }

    /// Finds the tags again; the first time, adds the columns for the type and starts the list.
    public void refresh() {
        if (dataProvider == null) {
            view.addColumns(annotationTagType);

            dataProvider = new RestDataProvider<AnnotationTag, ResultPage<AnnotationTag>>(getEventBus()) {
                @Override
                protected void exec(final Range range,
                                    final Consumer<ResultPage<AnnotationTag>> dataConsumer,
                                    final RestErrorHandler errorHandler) {
                    // The type is what this screen lists, so it is a field of the criteria
                    // rather than a term. See FindAnnotationTagCriteria.
                    final FindAnnotationTagCriteria criteria =
                            new FindAnnotationTagCriteria(annotationTagType);
                    CriteriaUtil.setRange(criteria, range);
                    annotationResourceClient.findAnnotationTags(criteria, dataConsumer, errorHandler,
                            view.getTaskMonitor());
                }
            };
            view.setDataProvider(dataProvider);

        } else {
            dataProvider.refresh();
        }
    }

    /// @param annotationTagType The type of tag to list, given before the first [#refresh()].
    public void setAnnotationTagType(final AnnotationTagType annotationTagType) {
        this.annotationTagType = annotationTagType;
    }
}
