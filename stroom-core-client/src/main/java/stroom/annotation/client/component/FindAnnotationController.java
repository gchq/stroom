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

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.explorer.client.component.FindPanel;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.widget.component.client.Controller;
import stroom.widget.component.client.ControllerDialog;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.function.Consumer;

/// The Choose Annotation dialog ([FindPanel]): finds annotations by their fields, and gives the
/// one chosen. Made once by the app and shown again, it keeps the last filter.
public class FindAnnotationController extends Controller {

    private final FindPanel view;
    private final FindAnnotationListController findAnnotationList;
    private boolean showing;

    /// Made by GIN, with its content.
    ///
    /// @param eventBus                         The application's event bus.
    /// @param view                             The content.
    /// @param findAnnotationList               Finds the annotations, and lists them.
    /// @param quickFilterContextHandlerFactory Makes the filter's recent filters.
    @Inject
    public FindAnnotationController(final EventBus eventBus,
                                    final FindPanel view,
                                    final FindAnnotationListController findAnnotationList,
                                    final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus);
        this.view = view;
        this.findAnnotationList = own(findAnnotationList);
        initWidget(view);

        findAnnotationList.setBordered(true);
        view.setResultView(findAnnotationList);
        view.setOnFilterChange(this::changeQuickFilter);
        view.setOnFilterKeyDown(this::onFilterKeyDown);
        findAnnotationList.setOnOpen(annotation -> choose());
        findAnnotationList.setOnFocusFilter(view::focus);
        findAnnotationList.setOnFilterError(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                FindAnnotationRequest.QUICK_FILTER_CONTEXT, this, () -> taskMonitor().createTaskMonitor()));
    }

    /// Shows the dialog, unless it is shown already, finding with the filter it last had.
    ///
    /// @param onChosen Given the annotation chosen on OK; not called on Cancel.
    public void show(final Consumer<Annotation> onChosen) {
        if (!showing) {
            showing = true;

            // Make sure we are set to focus text next time we show.
            findAnnotationList.setFocusText(true);

            // Refresh the results.
            findAnnotationList.refresh();

            ControllerDialog.show(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(PopupSize.resizable(800, 600))
                    .caption("Choose Annotation")
                    .onShow(e -> view.focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final Annotation selected = findAnnotationList.getSelected();
                            if (selected == null) {
                                // The consumers can't handle no annotation, so keep the dialog open
                                AlertEvent.fireWarn(this, "No annotation has been selected", e::reset);
                                return;
                            }
                            onChosen.accept(selected);
                        }
                        e.hide();
                    })
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    // Chooses the annotation found, closing the dialog as OK
    private void choose() {
        ControllerDialog.pressAction(this, DialogAction.OK);
    }

    private void changeQuickFilter(final String name) {
        findAnnotationList.setFilter(name);
        findAnnotationList.refresh();
    }

    private void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            choose();
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findAnnotationList.setKeyboardSelectedRow(0, true);
        }
    }

}
