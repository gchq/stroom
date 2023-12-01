package stroom.item.client;

import com.google.gwt.view.client.AbstractDataProvider;

public interface SelectionListModel<T, I extends SelectionItem> {

    AbstractDataProvider<I> getDataProvider();

    NavigationModel<I> getNavigationModel();

    void reset();

    void setFilter(String filter);

    void refresh();

    boolean displayFilter();

    boolean displayPath();

    boolean displayPager();

    default String getPathRoot() {
        return "Help";
    }

    I wrap(T item);

    T unwrap(I selectionItem);
}
