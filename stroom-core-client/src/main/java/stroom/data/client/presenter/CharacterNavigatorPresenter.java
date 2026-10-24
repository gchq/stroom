/*
 * Copyright 2020 Crown Copyright
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

package stroom.data.client.presenter;

import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.util.shared.HasCharacterData;
import stroom.widget.presenter.client.MyPresenterWidget;
import stroom.widget.presenter.client.View;
import stroom.widget.progress.client.component.ProgressBar;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;

public class CharacterNavigatorPresenter extends MyPresenterWidget<CharacterNavigatorView> {

    private final Provider<CharacterRangeSelectionPresenter> characterRangeSelectionPresenterProvider;
    private final ProgressBar progressBar;
    private CharacterRangeSelectionPresenter characterRangeSelectionPresenter = null;
    private HasCharacterData display;

    @Inject
    public CharacterNavigatorPresenter(
            final EventBus eventBus,
            final ProgressBar progressBar,
            final CharacterNavigatorView view,
            final Provider<CharacterRangeSelectionPresenter> characterRangeSelectionPresenterProvider) {

        super(eventBus, view);
        this.progressBar = progressBar;
        this.characterRangeSelectionPresenterProvider = characterRangeSelectionPresenterProvider;

        getView().setLabelClickHandler(this::handleLabelClick);
//        getView().setProgressPresenter(progressBar);
//        getView().setProgressView(progressBar);
        progressBar.setVisible(false);
    }

    public void setDisplay(final HasCharacterData display) {
        this.display = display;
        getView().setDisplay(display);
//        refreshNavigator();
    }

    public void refreshNavigator() {
        getView().refreshNavigator();
//        refreshProgressBar();
    }

//    private void refreshProgressBar() {
//        if (display != null
//                && display.getCharOffsetFrom().isPresent()
//                && display.getCharOffsetTo().isPresent()) {
//            progressBar.setVisible(true);
//            if (display.getTotalChars().isPresent()) {
//                progressBar.setProgress(Progress.boundedRange(
//                        display.getTotalChars().get(),
//                        display.getCharOffsetFrom().get(),
//                        display.getCharOffsetTo().get()));
//            } else {
//                progressBar.setProgress(Progress.unboundedRange(
//                        display.getCharOffsetFrom().get(),
//                        display.getCharOffsetTo().get()));
//            }
//        } else {
//            progressBar.setVisible(false);
//        }
//    }

    private CharacterRangeSelectionPresenter getCharacterRangeSelectionPresenter() {
        if (characterRangeSelectionPresenter == null) {
            characterRangeSelectionPresenter = make(characterRangeSelectionPresenterProvider);
        }
        return characterRangeSelectionPresenter;
    }

    private void handleLabelClick(final ClickEvent clickEvent) {
        final CharacterRangeSelectionPresenter characterRangeSelectionPresenter = getCharacterRangeSelectionPresenter();
        characterRangeSelectionPresenter.setDataRange(display.getDataRange());

        characterRangeSelectionPresenter.setTotalCharsCount(display.getTotalChars());
        characterRangeSelectionPresenter.show(dataRange -> display.setDataRange(dataRange));
    }

    public interface CharacterNavigatorView extends View {

//        void setProgressPresenter(final ProgressBar progressBar);

//        void setProgressView(final ProgressView progressView);

        void setDisplay(final HasCharacterData hasCharacterData);

        void refreshNavigator();

        void setLabelClickHandler(final ClickHandler clickHandler);
    }
}
