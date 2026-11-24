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

package stroom.search.elastic.client.presenter;

import stroom.search.elastic.client.presenter.ElasticVirtualFieldNamePresenter.ElasticVirtualFieldNameView;
import stroom.util.shared.NullSafe;
import stroom.widget.popup.client.event.DialogEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupType;
import stroom.widget.popup.client.view.DialogAction;

import com.google.gwt.user.client.ui.Focus;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/// A dialog asking for the name of a virtual field, used both to create a virtual field and to rename one.
///
/// The name is checked as it is typed and again when OK is pressed; the dialog stays open while the name is not
/// valid. Nothing is changed unless OK is pressed with a valid name.
public class ElasticVirtualFieldNamePresenter
        extends MyPresenterWidget<ElasticVirtualFieldNameView>
        implements ElasticVirtualFieldDialogUiHandlers {

    private Function<String, String> validator = name -> null;

    @Inject
    public ElasticVirtualFieldNamePresenter(final EventBus eventBus,
                                            final ElasticVirtualFieldNameView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    /// Shows the dialog.
    ///
    /// @param caption     the dialog's caption
    /// @param initialName the name to show to begin with, or `null` for none
    /// @param validator   gives the reason a name can't be used, or `null` if it can
    /// @param onOk        given the trimmed name when OK is pressed with a valid name
    public void show(final String caption,
                     final String initialName,
                     final Function<String, String> validator,
                     final Consumer<String> onOk) {
        this.validator = Objects.requireNonNull(validator);
        getView().setName(NullSafe.string(initialName));
        getView().setError(null);
        ShowPopupEvent.builder(this)
                .popupType(PopupType.OK_CANCEL_DIALOG)
                .caption(caption)
                .onShow(event -> getView().focus())
                .onHideRequest(event -> {
                    if (event.isOk()) {
                        final String name = NullSafe.trim(getView().getName());
                        final String error = validator.apply(name);
                        getView().setError(error);
                        if (error == null) {
                            onOk.accept(name);
                            event.hide();
                        } else {
                            event.reset();
                            getView().focus();
                        }
                    } else {
                        event.hide();
                    }
                })
                .fire();
    }

    @Override
    public void onNameEdit() {
        getView().setError(validator.apply(getView().getName()));
    }

    @Override
    public void onDialogAction(final DialogAction action) {
        DialogEvent.fire(this, this, action);
    }

    /// The view of the virtual field name dialog.
    public interface ElasticVirtualFieldNameView
            extends View, Focus, HasUiHandlers<ElasticVirtualFieldDialogUiHandlers> {

        /// @return the name as entered
        String getName();

        /// @param name the name to show
        void setName(String name);

        /// @param error the reason the name can't be used, or `null`
        void setError(String error);
    }
}
