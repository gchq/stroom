/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.preferences.client;

import stroom.editor.client.presenter.ChangeThemeEvent;
import stroom.editor.client.presenter.CurrentTheme;
import stroom.preferences.client.PreferencesPresenter.PreferencesView;
import stroom.ui.config.shared.UserPreferences;
import stroom.widget.popup.client.event.HidePopupEvent;
import stroom.widget.popup.client.event.ShowPopupEvent;
import stroom.widget.popup.client.presenter.PopupSize;
import stroom.widget.popup.client.presenter.PopupUiHandlers;
import stroom.widget.popup.client.presenter.PopupView.PopupType;

import com.google.gwt.event.shared.HasHandlers;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

import java.util.ArrayList;
import java.util.List;

public final class PreferencesPresenter
        extends MyPresenterWidget<PreferencesView>
        implements PreferencesUiHandlers {

    private final PreferencesManager preferencesManager;
    private UserPreferences originalPreferences;

    private final List<String> themes = new ArrayList<>();

    @Inject
    public PreferencesPresenter(
            final EventBus eventBus,
            final PreferencesView view,
            final PreferencesManager preferencesManager,
            final CurrentTheme currentTheme) {

        super(eventBus, view);
        themes.add("Light");
        themes.add("Dark");

        view.setUiHandlers(this);
        this.preferencesManager = preferencesManager;
    }

    @Override
    protected void onBind() {
    }

    @Override
    public void onChange() {
        final UserPreferences userPreferences = write();
        preferencesManager.updateClassNames(userPreferences);
        final HasHandlers handlers = event -> getEventBus().fireEvent(event);
        ChangeThemeEvent.fire(handlers, userPreferences.getTheme());
    }

    public void show() {
        final String caption = "User Preferences";
        final PopupType popupType = PopupType.OK_CANCEL_DIALOG;
        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final UserPreferences userPreferences = write();
                    preferencesManager.update(userPreferences, (result) -> hide());
                    preferencesManager.updateClassNames(userPreferences);
                } else {
                    preferencesManager.updateClassNames(originalPreferences);
                    hide();
                }
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
            }
        };

        preferencesManager.fetch(userPreferences -> {
            read(userPreferences);
            ShowPopupEvent.fire(
                    PreferencesPresenter.this,
                    PreferencesPresenter.this,
                    popupType,
                    getPopupSize(),
                    caption,
                    popupUiHandlers);
        });
    }

    private PopupSize getPopupSize() {
        return new PopupSize(
                700, 556,
                700, 556,
                1024, 556,
                true);
    }

    protected void hide() {
        HidePopupEvent.fire(
                PreferencesPresenter.this,
                PreferencesPresenter.this);
    }


    private void read(final UserPreferences userPreferences) {
        this.originalPreferences = userPreferences;
        getView().setThemes(themes);
        getView().setTheme(userPreferences.getTheme());
    }

    private UserPreferences write() {
        return UserPreferences.builder()
                .theme(getView().getThere())
                .build();
    }

    public interface PreferencesView extends View, HasUiHandlers<PreferencesUiHandlers> {

        String getThere();

        void setTheme(String theme);

        void setThemes(List<String> themes);
    }
}
