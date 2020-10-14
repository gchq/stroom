package stroom.data.client.presenter;

import stroom.data.client.presenter.CharacterNavigatorPresenter.CharacterNavigatorView;
import stroom.util.shared.DataRange;
import stroom.util.shared.HasCharacterData;
import stroom.util.shared.RowCount;
import stroom.widget.popup.client.presenter.PopupUiHandlers;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.MyPresenterWidget;
import com.gwtplatform.mvp.client.View;

public class CharacterNavigatorPresenter extends MyPresenterWidget<CharacterNavigatorView> {

    private final Provider<CharacterRangeSelectionPresenter> characterRangeSelectionPresenterProvider;
    private CharacterRangeSelectionPresenter characterRangeSelectionPresenter = null;
    private HasCharacterData display;

    @Inject
    public CharacterNavigatorPresenter(final EventBus eventBus,
                                       final CharacterNavigatorView view,
                                       final Provider<CharacterRangeSelectionPresenter> characterRangeSelectionPresenterProvider) {
        super(eventBus, view);
        this.characterRangeSelectionPresenterProvider = characterRangeSelectionPresenterProvider;

        getView().setLabelClickHandler(this::handleLabelClick);
    }

    public void setDisplay(final HasCharacterData display) {
        this.display = display;
        getView().setDisplay(display);
        refreshNavigator();
    }

    public void refreshNavigator() {
        getView().refreshNavigator();
    }

    private CharacterRangeSelectionPresenter getCharacterRangeSelectionPresenter() {
        if (characterRangeSelectionPresenter == null) {
            characterRangeSelectionPresenter = characterRangeSelectionPresenterProvider.get();
        }
        return characterRangeSelectionPresenter;
    }

    private void handleLabelClick(ClickEvent clickEvent) {

        final CharacterRangeSelectionPresenter characterRangeSelectionPresenter = getCharacterRangeSelectionPresenter();
        characterRangeSelectionPresenter.setDataRange(display.getDataRange());

        characterRangeSelectionPresenter.setTotalCharsCount(
                RowCount.of(display.getTotalChars()
                        .orElse(0L), display.getTotalChars()
                        .isPresent()));

        final PopupUiHandlers popupUiHandlers = new PopupUiHandlers() {
            @Override
            public void onHideRequest(final boolean autoClose, final boolean ok) {
                characterRangeSelectionPresenter.hide(autoClose, ok);
            }

            @Override
            public void onHide(final boolean autoClose, final boolean ok) {
                if (ok) {
                    final DataRange dataRange = characterRangeSelectionPresenter.getDataRange();

                    display.setDataRange(dataRange);
                }
            }
        };
        characterRangeSelectionPresenter.show(popupUiHandlers);
    }

    public interface CharacterNavigatorView extends View {

        void setDisplay(final HasCharacterData hasCharacterData);

        void refreshNavigator();

        void setLabelClickHandler(final ClickHandler clickHandler);
    }
}
