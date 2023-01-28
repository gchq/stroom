package stroom.item.client;

import stroom.docref.HasDisplayValue;

import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.user.client.ui.Composite;
import com.google.gwt.user.client.ui.TextBox;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public class AutocompleteListBox<T extends HasDisplayValue> extends Composite implements ItemListBoxDisplay<T> {

    private final TextBox textBox;
    private final AutocompletePopup<T> popup;

    public AutocompleteListBox() {
        textBox = new TextBox();
        textBox.setReadOnly(true);
        textBox.addStyleName("autocompleteTextBox");

        popup = new AutocompletePopup<>();
        textBox.addClickHandler(event -> popup.show(textBox));

        initWidget(textBox);
    }


    @Override
    public void addItem(final T item) {
        popup.addItem(item);
        updateTextBoxWidth();
    }

    @Override
    public void addItems(final Collection<T> list) {
        popup.addItems(list);
        updateTextBoxWidth();
    }

    @Override
    public void addItems(final T[] list) {
        popup.addItems(list);
        updateTextBoxWidth();
    }

    @Override
    public void removeItem(final T item) {
        popup.removeItem(item);
        updateTextBoxWidth();
    }

    @Override
    public void setName(final String name) {
        textBox.setName(name);
    }

    @Override
    public void clear() {
        popup.clear();
    }

    @Override
    public T getSelectedItem() {
        return popup.getSelectedItem();
    }

    @Override
    public void setSelectedItem(final T item) {
        popup.setSelectedItem(item);
        if (item != null) {
            textBox.setText(item.getDisplayValue());
        }
    }

    @Override
    public void setEnabled(final boolean enabled) {
        textBox.setEnabled(enabled);
        popup.setEnabled(enabled);
    }

    @Override
    public HandlerRegistration addSelectionHandler(final SelectionHandler<T> handler) {
        return popup.addSelectionHandler(event -> {
            textBox.setText(event.getSelectedItem().getDisplayValue());
            handler.onSelection(event);
        });
    }

    /**
     * Update the text box size to accommodate the longest item
     */
    private void updateTextBoxWidth() {
        final Optional<String> longestItem = popup.getItems().stream()
                .map(HasDisplayValue::getDisplayValue)
                .max(Comparator.comparingInt(String::length));

        longestItem.ifPresent(s -> textBox.setVisibleLength(s.length()));
    }
}
