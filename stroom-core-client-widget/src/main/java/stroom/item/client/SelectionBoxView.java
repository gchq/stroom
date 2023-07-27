package stroom.item.client;

import com.google.gwt.user.client.ui.HasValue;

import java.util.Collection;

public interface SelectionBoxView<T> extends HasValue<T> {

    void setNonSelectString(String nonSelectString);

    void addItems(Collection<T> items);

    void addItems(T[] items);

    void addItem(T item);

    void clear();
}
