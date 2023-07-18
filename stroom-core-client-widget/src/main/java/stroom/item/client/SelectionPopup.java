package stroom.item.client;

import stroom.svg.client.SvgIconBox;
import stroom.svg.shared.SvgImage;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.core.client.Scheduler;
import com.google.gwt.dom.client.Element;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class SelectionPopup extends Composite {

    private static final int DEFAULT_VISIBLE_ITEM_COUNT = 10;

    private final PopupPanel popupPanel;
    private final TextBox textBox;
    private final ListBox listBox;
    private int[] filteredIndexMapping;
    private SelectionBoxModel<?> model;
    private String lastFilterValue;

    private final EventBinder eventBinder = new EventBinder() {
        @Override
        protected void onBind() {
            super.onBind();
            registerHandler(popupPanel.addCloseHandler(event -> hide()));
            registerHandler(textBox.addKeyUpHandler(e -> onTextKeyUp(e)));
            registerHandler(listBox.addClickHandler(e -> onListSelectionChange(e)));
            registerHandler(listBox.addKeyUpHandler(e -> onListKeyUp(e)));
        }
    };

    public SelectionPopup() {
        popupPanel = new PopupPanel();
        textBox = new TextBox();
        listBox = new ListBox();

        textBox.addStyleName("allow-focus");

        final SvgIconBox svgIconBox = new SvgIconBox();
        svgIconBox.setWidget(textBox, SvgImage.SEARCH);

        final FlowPanel layout = new FlowPanel();
        layout.setStyleName("max SelectionPopup-layout");
        layout.add(svgIconBox);
        layout.add(listBox);

        popupPanel.add(layout);
        popupPanel.setAutoHideEnabled(true);
        popupPanel.setStyleName("simplePopup-background SelectionPopup");

        listBox.setVisibleItemCount(DEFAULT_VISIBLE_ITEM_COUNT);
    }

    public void addAutoHidePartner(Element partner) {
        popupPanel.addAutoHidePartner(partner);
    }

    public void setModel(final SelectionBoxModel<?> model) {
        this.model = model;
    }

    public void setName(final String name) {
        textBox.setName(name);
    }

    public void setEnabled(final boolean enabled) {
        if (!enabled) {
            popupPanel.hide();
        }
    }

    public void show(final Widget displayTarget) {
        final PopupPosition position = new PopupPosition(displayTarget.getAbsoluteLeft() - 4,
                displayTarget.getAbsoluteTop() + displayTarget.getOffsetHeight() + 4);
        show(position);
    }

    public void show(final PopupPosition position) {
        if (popupPanel.isShowing()) {
            hide();
        } else {
            textBox.setText("");
            lastFilterValue = null;
            updateListBox();

            eventBinder.bind();
            popupPanel.setPopupPositionAndShow((offsetWidth, offsetHeight) -> {
                popupPanel.setPopupPosition(
                        position.getLeft(),
                        position.getTop());
                afterShow();
            });
        }
    }

    private void afterShow() {
        Scheduler.get().scheduleDeferred(() -> {
            textBox.setFocus(true);
        });
    }

    public void hide() {
        eventBinder.unbind();
        popupPanel.hide();
    }

    private void onTextKeyUp(KeyUpEvent event) {
        final int keyCode = event.getNativeKeyCode();
        switch (keyCode) {
            case KeyCodes.KEY_ESCAPE:
                if (!textBox.getText().isEmpty()) {
                    // If the user has entered filter text, clear it
                    textBox.setText("");
                } else {
                    // Otherwise close the popup
                    hide();
                }
                return;
            // Allow the user to navigate from the filter box to the options list using the arrow keys
            case KeyCodes.KEY_DOWN:
                if (listBox.getItemCount() > 0) {
                    final int index = listBox.getSelectedIndex();
                    if (index == -1) {
                        listBox.setSelectedIndex(0);
                    } else if (index < listBox.getItemCount() - 2) {
                        listBox.setSelectedIndex(index + 1);
                    }
                    listBox.setFocus(true);
                }
                return;
            case KeyCodes.KEY_UP:
                if (listBox.getItemCount() > 0) {
                    final int index = listBox.getSelectedIndex();
                    if (index == -1) {
                        listBox.setSelectedIndex(listBox.getItemCount() - 1);
                    } else if (index > 0) {
                        listBox.setSelectedIndex(index - 1);
                    }
                    listBox.setFocus(true);
                }
                return;
        }

        updateListBox();
    }

    private void updateListBox() {
        final int selectedIndex = model.getSelectedIndex();
        int filteredSelectedIndex = -1;
        final String text = textBox.getValue();
        if (!Objects.equals(text, lastFilterValue)) {
            lastFilterValue = text;

            filteredIndexMapping = new int[model.getStrings().size()];
            List<String> filteredItems = new ArrayList<>(model.getStrings().size());
            if (text != null && !text.isEmpty()) {
                final String textLowerCase = text.toLowerCase();
                int filteredIndex = 0;
                int index = 0;
                for (final String string : model.getStrings()) {
                    if (string.toLowerCase().contains(textLowerCase)) {
                        filteredItems.add(string);
                        filteredIndexMapping[filteredIndex] = index;
                        if (selectedIndex == index) {
                            filteredSelectedIndex = filteredIndex;
                        }
                        filteredIndex++;
                    }
                    index++;
                }
            } else {
                int index = 0;
                filteredSelectedIndex = selectedIndex;
                for (final String string : model.getStrings()) {
                    filteredItems.add(string);
                    filteredIndexMapping[index] = index;
                    index++;
                }
            }

            listBox.clear();
            listBox.setVisibleItemCount(filteredItems.size());
            for (final String string : filteredItems) {
                listBox.addItem(string);
            }

            listBox.setSelectedIndex(filteredSelectedIndex);
        }
    }

    private void onListSelectionChange(final ClickEvent event) {
        int index = -1;
        if (listBox.getSelectedIndex() >= 0) {
            index = filteredIndexMapping[listBox.getSelectedIndex()];
        }
        model.setSelectedIndex(index);
    }

    private void onListKeyUp(final KeyUpEvent event) {
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_ENTER:
                model.setSelectedIndex(listBox.getSelectedIndex());
                break;
            case KeyCodes.KEY_ESCAPE:
                hide();
                break;
        }
    }

    public HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler) {
        return popupPanel.addCloseHandler(handler);
    }
}
