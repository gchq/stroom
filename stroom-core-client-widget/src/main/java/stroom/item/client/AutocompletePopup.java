package stroom.item.client;

import stroom.docref.HasDisplayValue;
import stroom.widget.popup.client.presenter.PopupPosition;

import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.KeyCodes;
import com.google.gwt.event.dom.client.KeyUpEvent;
import com.google.gwt.event.logical.shared.CloseHandler;
import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.ListBox;
import com.google.gwt.user.client.ui.PopupPanel;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.VerticalPanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.stream.Collectors;

public class AutocompletePopup<T extends HasDisplayValue> extends Composite implements ItemListBoxDisplay<T> {

    private static final int DEFAULT_VISIBLE_ITEM_COUNT = 10;

    private final PopupPanel popupPanel;
    private final TextBox autoTextBox;
    private final ListBox autoListBox;

    private final Map<String, T> items;
    private T selectedItem;

    public AutocompletePopup() {
        popupPanel = new PopupPanel();
        autoTextBox = new TextBox();
        autoListBox = new ListBox();

        items = new HashMap<>();

        final VerticalPanel verticalPanel = new VerticalPanel();
        verticalPanel.add(autoTextBox);
        verticalPanel.add(autoListBox);
        popupPanel.add(verticalPanel);
        popupPanel.setAutoHideEnabled(true);
        popupPanel.addStyleName("autocompletePanel");
        popupPanel.addCloseHandler(event -> hide());

        autoTextBox.addKeyUpHandler(this::filterItems);
        autoListBox.addClickHandler(this::onListSelectionChange);
        autoListBox.addKeyUpHandler(this::onListKeyDown);
        setVisibleItemCount(DEFAULT_VISIBLE_ITEM_COUNT);
    }

    public Collection<T> getItems() {
        return items.values();
    }

    @Override
    public void addItem(final T item) {
        if (item != null && !items.containsKey(item.getDisplayValue())) {
            items.put(item.getDisplayValue(), item);
            updateListBoxItems(items.keySet());
        }
    }

    @Override
    public void addItems(final Collection<T> list) {
        if (list != null) {
            for (final T item : list) {
                if (item != null && !items.containsKey(item.getDisplayValue())) {
                    items.put(item.getDisplayValue(), item);
                }
            }
        }

        updateListBoxItems(items.keySet());
    }

    @Override
    public void addItems(final T[] list) {
        if (list != null) {
            for (final T item : list) {
                if (item != null && !items.containsKey(item.getDisplayValue())) {
                    items.put(item.getDisplayValue(), item);
                }
            }
        }

        updateListBoxItems(items.keySet());
    }

    @Override
    public void removeItem(final T item) {
        if (item != null) {
            items.remove(item.getDisplayValue());
            updateListBoxItems(items.keySet());

            if (item.equals(selectedItem)) {
                clearSelection();
            }
        }
    }

    @Override
    public void setName(final String name) {
        autoTextBox.setName(name);
    }

    @Override
    public void clear() {
        clearSelection();
        items.clear();
        resetAutocomplete();
        autoListBox.clear();
    }

    @Override
    public T getSelectedItem() {
        return selectedItem;
    }

    @Override
    public void setSelectedItem(final T item) {
        selectedItem = item;
        updateListBoxItems(items.keySet());
    }

    public void clearSelection() {
        selectedItem = null;
        resetAutocomplete();
    }

    private void resetAutocomplete() {
        autoTextBox.setText("");
        updateListBoxItems(items.keySet());
    }

    @Override
    public void setEnabled(final boolean enabled) {
        if (!enabled) {
            popupPanel.hide();
        }
    }

    public void setVisibleItemCount(int itemCount) {
        this.autoListBox.setVisibleItemCount(itemCount);
    }

    public void show(final Widget displayTarget) {
        popupPanel.showRelativeTo(displayTarget);
        afterShow();
    }

    public void show(final PopupPosition position) {
        popupPanel.setPopupPosition(position.getLeft(), position.getTop());
        popupPanel.show();
        afterShow();
    }

    private void afterShow() {
        resetAutocomplete();
        updateSelectedItem();
        autoTextBox.setFocus(true);
    }

    public void hide() {
        popupPanel.hide();
    }

    /**
     * Refresh the list box from the provided key list
     */
    private void updateListBoxItems(final Collection<String> items) {
        this.autoListBox.clear();

        for (final String item : items) {
            autoListBox.addItem(item);
        }

        updateSelectedItem();
    }

    /**
     * Select the autocomplete list box item, based on the selected object
     */
    private void updateSelectedItem() {
        if (selectedItem != null) {
            // Find the key for the selected item
            final Optional<String> foundItem = items.entrySet().stream()
                    .filter(entry -> entry.getValue() == selectedItem)
                    .map(Entry::getKey)
                    .findFirst();

            // Select the corresponding item in the autocomplete list
            if (foundItem.isPresent()) {
                for (int i = 0; i < autoListBox.getItemCount(); i++) {
                    if (autoListBox.getItemText(i).equals(foundItem.get())) {
                        autoListBox.setSelectedIndex(i);
                        return;
                    }
                }
            }
        }

        autoListBox.setSelectedIndex(-1);
    }

    private void filterItems(KeyUpEvent event) {
        int keyCode = event.getNativeKeyCode();
        final Collection<String> filteredItems;

        switch (keyCode) {
            case KeyCodes.KEY_ESCAPE:
                if (!autoTextBox.getText().isEmpty()) {
                    // If the user has entered filter text, clear it
                    resetAutocomplete();
                } else {
                    // Otherwise close the popup
                    hide();
                }
                return;
            // Allow the user to navigate from the filter box to the options list using the arrow keys
            case KeyCodes.KEY_DOWN:
                if (autoListBox.getItemCount() > 0) {
                    autoListBox.setSelectedIndex(0);
                    autoListBox.setFocus(true);
                }
                return;
            case KeyCodes.KEY_UP:
                if (autoListBox.getItemCount() > 0) {
                    autoListBox.setSelectedIndex(autoListBox.getItemCount() - 1);
                    autoListBox.setFocus(true);
                }
                return;
        }

        TextBox textBox = (TextBox) event.getSource();
        final String text = textBox.getValue();
        if (text != null && !text.isEmpty()) {
            final String textLowerCase = text.toLowerCase();
            filteredItems = this.items.keySet().stream()
                    .filter(item -> item.toLowerCase().contains(textLowerCase))
                    .collect(Collectors.toList());
        } else {
            filteredItems = this.items.keySet();
        }

        updateListBoxItems(filteredItems);
    }

    private void onListSelectionChange(final ClickEvent event) {
        selectListItem(autoListBox.getSelectedItemText());
    }

    private void onListKeyDown(final KeyUpEvent event) {
        switch (event.getNativeKeyCode()) {
            case KeyCodes.KEY_ENTER:
                selectListItem(autoListBox.getSelectedItemText());
                break;
            case KeyCodes.KEY_ESCAPE:
                hide();
                break;
        }
    }

    private void selectListItem(String itemText) {
        selectedItem = items.get(itemText);
        SelectionEvent.fire(this, selectedItem);
        hide();
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<T> handler) {
        return addHandler(handler, SelectionEvent.getType());
    }

    public HandlerRegistration addCloseHandler(final CloseHandler<PopupPanel> handler) {
        return popupPanel.addCloseHandler(handler);
    }
}
