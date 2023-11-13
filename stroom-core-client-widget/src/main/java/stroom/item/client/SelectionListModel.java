package stroom.item.client;

import com.google.gwt.view.client.AbstractDataProvider;

public interface SelectionListModel {

    AbstractDataProvider<SelectionItem> getDataProvider();

    NavigationModel getNavigationModel();

    void setFilter(String filter);

    void refresh();

    boolean displayPath();

    boolean displayPager();

    default String getNonSelectString() {
        return null;
    }
}
