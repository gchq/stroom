package stroom.data.grid.client;

import com.google.gwt.dom.client.NativeEvent;

public interface HeadingListener {

    void onMouseDown(NativeEvent event, Heading heading);

    void onMouseUp(NativeEvent event, Heading heading);

    void moveColumn(int fromIndex, int toIndex);

    void resizeColumn(int colIndex, int size);

    boolean isBusy();
}