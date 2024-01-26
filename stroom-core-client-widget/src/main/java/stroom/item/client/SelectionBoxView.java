package stroom.item.client;

public interface SelectionBoxView<T, I extends SelectionItem> {

    void setModel(SelectionListModel<T, I> model);
}
