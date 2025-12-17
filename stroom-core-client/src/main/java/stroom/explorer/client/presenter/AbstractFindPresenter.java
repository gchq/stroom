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

package stroom.explorer.client.presenter;

import stroom.document.client.event.OpenDocumentEvent;
import stroom.explorer.client.presenter.AbstractFindPresenter.FindView;
import stroom.explorer.shared.FindResult;
import stroom.widget.popup.client.event.HidePopupRequestEvent;

import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyDownEvent;
import com.google.gwt.user.client.ui.Focus;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenter;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.proxy.Proxy;

public abstract class AbstractFindPresenter<T_PROXY extends Proxy<?>>
        extends MyPresenter<FindView, T_PROXY>
        implements FindUiHandlers, FindDocResultListHandler<FindResult> {

    private final FindDocResultListPresenter findResultListPresenter;

    public AbstractFindPresenter(final EventBus eventBus,
                                 final FindView view,
                                 final T_PROXY proxy,
                                 final FindDocResultListPresenter findResultListPresenter) {
        super(eventBus, view, proxy);
        this.findResultListPresenter = findResultListPresenter;

        // Ensure list has a border in the popup view.
        findResultListPresenter.getView().asWidget().addStyleName("form-control-border form-control-background");
        view.setResultView(findResultListPresenter.getView());
        view.setUiHandlers(this);
        findResultListPresenter.setFindResultListHandler(this);
    }

    @Override
    public void openDocument(final FindResult match) {
        if (match != null) {
            OpenDocumentEvent.fire(this, match.getDocRef(), true);
            hide();
        }
    }

    @Override
    public void focus() {
        getView().focus();
    }


    @Override
    public void changeQuickFilter(final String name) {
        if (findResultListPresenter.getExplorerTreeFilterBuilder().setNameFilter(name)) {
            refresh();
        }
    }

    @Override
    public void onFilterKeyDown(final KeyDownEvent event) {
        if (event.getNativeKeyCode() == KeyCodes.KEY_ENTER) {
            openDocument(findResultListPresenter.getSelected());
        } else if (event.getNativeKeyCode() == KeyCodes.KEY_DOWN) {
            findResultListPresenter.setKeyboardSelectedRow(0, true);
        }
    }

    public void refresh() {
        findResultListPresenter.refresh();
    }

    private void hide() {
        HidePopupRequestEvent.builder(this).fire();
    }

    @Override
    protected void revealInParent() {
    }

    public FindDocResultListPresenter getFindResultListPresenter() {
        return findResultListPresenter;
    }

    public interface FindView extends View, Focus, HasUiHandlers<FindUiHandlers> {

        void setResultView(View view);

        void setDialogMode(boolean dialog);
    }
}
