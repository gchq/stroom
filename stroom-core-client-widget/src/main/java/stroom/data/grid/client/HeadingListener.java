package stroom.data.grid.client;

import com.google.gwt.dom.client.NativeEvent;

import java.util.function.Supplier;

public interface HeadingListener {

    void onMoveStart(NativeEvent event, Supplier<Heading> headingSupplier);

    void onMoveEnd(NativeEvent event, Supplier<Heading> headingSupplier);

    void onShowMenu(NativeEvent event, Supplier<Heading> headingSupplier);

    void moveColumn(int fromIndex, int toIndex);

    void resizeColumn(int colIndex, int size);
}
