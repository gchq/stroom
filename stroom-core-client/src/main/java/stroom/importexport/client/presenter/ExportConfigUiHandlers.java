package stroom.importexport.client.presenter;

import com.google.gwt.event.dom.client.MouseDownEvent;
import com.gwtplatform.mvp.client.UiHandlers;

import java.util.function.Consumer;

public interface ExportConfigUiHandlers extends UiHandlers {

    void changeQuickFilter(String name);

    void showTypeFilter(MouseDownEvent event,
                        Consumer<Boolean> filterStateConsumer);
}
