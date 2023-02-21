package stroom.util.client;

import com.google.gwt.dom.client.BodyElement;
import com.google.gwt.dom.client.Document;
import com.google.gwt.dom.client.Element;
import com.google.gwt.user.client.ui.TextArea;

public final class ClipboardUtil {

    /**
     * Copies the provided text to the clipboard via an invisible textarea
     * @return Whether the copy was successful
     */
    public static boolean copy(String text) {
        final BodyElement body = Document.get().getBody();
        final TextArea textArea = new TextArea();
        final Element textAreaElement = textArea.getElement();

        body.appendChild(textAreaElement);
        textArea.setStyleName("clipboardCopy");
        textArea.setText(text);

        final boolean result = copy();
        body.removeChild(textAreaElement);

        return result;
    }

    private static native boolean copy() /*-{
        var textArea = $doc.getElementsByClassName('clipboardCopy')[0];
        textArea.focus();
        textArea.select();
        return $doc.execCommand('copy');
    }-*/;
}
