package stroom.data.grid.client;

import com.google.gwt.safehtml.shared.SafeHtml;

import java.util.List;

public interface MessagePanel {

    void showMessage(List<String> errors);

    void showMessage(SafeHtml message);

    void showMessage(String message);

    void hideMessage();
}
