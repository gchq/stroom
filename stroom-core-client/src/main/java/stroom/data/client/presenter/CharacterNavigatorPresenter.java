package stroom.data.client.presenter;

import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.util.shared.HasCharacterData;
import stroom.widget.progress.client.presenter.ProgressPresenter;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CharacterNavigatorPresenter extends MyPresenterWidget<CharacterNavigatorView> {

    private final Provider<CharacterRangeSelectionPresenter> characterRangeSelectionPresenterProvider;
    private final ProgressPresenter progressPresenter;
    private CharacterRangeSelectionPresenter characterRangeSelectionPresenter = null;
    private HasCharacterData display;

    @Inject
    public CharacterNavigatorPresenter(
            final EventBus eventBus,
            final ProgressPresenter progressPresenter,
            final CharacterNavigatorView view,
            final Provider<CharacterRangeSelectionPresenter> characterRangeSelectionPresenterProvider) {

        super(eventBus, view);
        this.progressPresenter = progressPresenter;
        this.characterRangeSelectionPresenterProvider = characterRangeSelectionPresenterProvider;

        getView().setLabelClickHandler(this::handleLabelClick);
//        getView().setProgressPresenter(progressPresenter);
//        getView().setProgressView(progressPresenter.getView());
        progressPresenter.setVisible(false);
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
//            progressPresenter.setVisible(true);
//            if (display.getTotalChars().isPresent()) {
//                progressPresenter.setProgress(Progress.boundedRange(
//                        display.getTotalChars().get(),
//                        display.getCharOffsetFrom().get(),
//                        display.getCharOffsetTo().get()));
//            } else {
//                progressPresenter.setProgress(Progress.unboundedRange(
//                        display.getCharOffsetFrom().get(),
//                        display.getCharOffsetTo().get()));
//            }
//        } else {
//            progressPresenter.setVisible(false);
//        }
//    }

    private CharacterRangeSelectionPresenter getCharacterRangeSelectionPresenter() {
        if (characterRangeSelectionPresenter == null) {
            characterRangeSelectionPresenter = characterRangeSelectionPresenterProvider.get();
        }
        return characterRangeSelectionPresenter;
    }

    private void handleLabelClick(ClickEvent clickEvent) {
        final CharacterRangeSelectionPresenter characterRangeSelectionPresenter = getCharacterRangeSelectionPresenter();
        characterRangeSelectionPresenter.setDataRange(display.getDataRange());

        characterRangeSelectionPresenter.setTotalCharsCount(display.getTotalChars());
        characterRangeSelectionPresenter.show(dataRange -> display.setDataRange(dataRange));
    }

    public interface CharacterNavigatorView extends View {

//        void setProgressPresenter(final ProgressPresenter progressPresenter);

//        void setProgressView(final ProgressView progressView);

        void setDisplay(final HasCharacterData hasCharacterData);

        void refreshNavigator();

        void setLabelClickHandler(final ClickHandler clickHandler);
    }
}
