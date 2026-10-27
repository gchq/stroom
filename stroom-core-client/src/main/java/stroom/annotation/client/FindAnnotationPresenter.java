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

package stroom.annotation.client;

import stroom.alert.client.event.AlertEvent;
import stroom.annotation.client.component.FindAnnotationListController;
import stroom.annotation.shared.Annotation;
import stroom.annotation.shared.FindAnnotationRequest;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.explorer.client.presenter.FindView;
import stroom.quickfilter.client.QuickFilterContextHandlerFactory;
import stroom.widget.popup.client.event.HidePopupRequestEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;
import stroom.widget.presenter.client.MyPresenterWidget;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class FindAnnotationPresenter
        extends MyPresenterWidget<FindView>
        implements ShowFindAnnotationEvent.Handler, FindUiHandlers {

    private boolean showing;
    private final FindAnnotationListController findAnnotationList;

    @Inject
    public FindAnnotationPresenter(final EventBus eventBus,
                                   final FindView view,
                                   final FindAnnotationListController findAnnotationList,
                                   final QuickFilterContextHandlerFactory quickFilterContextHandlerFactory) {
        super(eventBus, view);
        own(findAnnotationList);
        getView().setDialogMode(false);
        this.findAnnotationList = findAnnotationList;

        view.setResultView(findAnnotationList);
        view.setUiHandlers(this);
        findAnnotationList.setOnOpen(this::openDocument);
        findAnnotationList.setOnFocusFilter(this::focus);
        findAnnotationList.setOnFilterError(view::setFilterError);
        view.setQuickFilterContextHandler(quickFilterContextHandlerFactory.create(
                FindAnnotationRequest.QUICK_FILTER_CONTEXT, this, this));
    }

    /// Puts the focus on the filter.
    public void focus() {
        getView().focus();
    }

    public void onShow(final ShowFindAnnotationEvent event) {
        if (!showing) {
            showing = true;

            // Make sure we are set to focus text next time we show.
            findAnnotationList.setFocusText(true);

            // Refresh the results.
            findAnnotationList.refresh();

            // Ensure list has a border in the popup view.
            findAnnotationList.asWidget().addStyleName("form-control-border form-control-background");

            getView().setDialogMode(true);
            final PopupSize popupSize = PopupSize.resizable(800, 600);
            ShowPopupEvent.builder(this)
                    .popupType(PopupType.OK_CANCEL_DIALOG)
                    .popupSize(popupSize)
                    .caption("Choose Annotation")
                    .onShow(e -> getView().focus())
                    .onHideRequest(e -> {
                        if (e.isOk()) {
                            final Annotation selected = findAnnotationList.getSelected();
                            if (selected == null) {
                                // The consumers can't handle no annotation, so keep the dialog open
                                AlertEvent.fireWarn(this, "No annotation has been selected", e::reset);
                                return;
                            }
                            event.getAnnotationConsumer().accept(selected);
                        }
                        e.hide();
                    })
                    .onHide(e -> showing = false)
                    .fire();
        }
    }

    /// Chooses the annotation found, closing the dialog as OK.
    ///
    /// @param match The annotation.
    public void openDocument(final Annotation match) {
        HidePopupRequestEvent.builder(this).action(DialogAction.OK).fire();
    }

    @Override
    public void changeQuickFilter(final String name) {
        findAnnotationList.setFilter(name);
        findAnnotationList.refresh();
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findAnnotationList.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findAnnotationList.setKeyboardSelectedRow(0, true);
        }
    }

}
