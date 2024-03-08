package stroom.data.grid.client;

import com.google.gwt.view.client.CellPreviewEvent;

public interface EventCell {

    boolean isConsumed(final CellPreviewEvent<?> event);
}
