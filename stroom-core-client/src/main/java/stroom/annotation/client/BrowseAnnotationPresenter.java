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

import stroom.annotation.client.component.FindAnnotationListController;
import stroom.annotation.shared.Annotation;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.client.presenter.FindDocResultListHandler;
import stroom.explorer.client.presenter.FindUiHandlers;
import stroom.security.shared.DocumentPermission;
import stroom.svg.shared.SvgImage;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.tab.client.presenter.TabData;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class BrowseAnnotationPresenter
        extends MyPresenterWidget<FindView>
        implements TabData, FindUiHandlers, FindDocResultListHandler<Annotation> {

    private final FindAnnotationListController findResultListPresenter;

    @Inject
    public BrowseAnnotationPresenter(final EventBus eventBus,
                                     final FindView view,
                                     final FindAnnotationListController findResultListPresenter) {
        super(eventBus, view);
        own(findResultListPresenter);
        this.findResultListPresenter = findResultListPresenter;
        // To browse, users only need view permission.
        findResultListPresenter.setPermission(DocumentPermission.VIEW);
        view.setDialogMode(false);
        view.setResultView(findResultListPresenter);
        view.setUiHandlers(this);
        findResultListPresenter.setOnOpen(this::openDocument);
        findResultListPresenter.setOnFocusFilter(this::focus);
    }

    @Override
    public void openDocument(final Annotation match) {
        if (match != null) {
            EditAnnotationEvent.fire(this, match.getId());
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }

    public void refresh() {
        findResultListPresenter.refresh();
    }

    @Override
    public void changeQuickFilter(final String name) {
        findResultListPresenter.setFilter(name);
        findResultListPresenter.refresh();
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultListPresenter.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultListPresenter.setKeyboardSelectedRow(0, true);
        }
    }

    @Override
    public SvgImage getIcon() {
        return SvgImage.EYE;
    }

    @Override
    public String getLabel() {
        return "Annotations";
    }

    @Override
    public boolean isCloseable() {
        return true;
    }

    @Override
    public String getType() {
        return "Annotations";
    }
}
