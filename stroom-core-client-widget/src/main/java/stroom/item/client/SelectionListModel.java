package stroom.item.client;

import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import java.util.function.Consumer;

public interface SelectionListModel<T, I extends SelectionItem> {

    void onRangeChange(I parent,
                       String filter,
                       boolean filterChange,
                       PageRequest pageRequest,
                       Consumer<ResultPage<I>> consumer);

    void reset();

    boolean displayFilter();

    boolean displayPath();

    boolean displayPager();

    default String getPathRoot() {
        return "Help";
    }

    I wrap(T item);

    T unwrap(I selectionItem);

    boolean isEmptyItem(I selectionItem);
}
