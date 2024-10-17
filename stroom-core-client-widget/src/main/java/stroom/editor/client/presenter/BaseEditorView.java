package stroom.editor.client.presenter;

import stroom.ui.config.shared.AceEditorTheme;

import com.google.gwt.event.dom.client.HasKeyDownHandlers;
import com.google.gwt.event.dom.client.HasKeyUpHandlers;
import com.google.gwt.event.logical.shared.HasValueChangeHandlers;
import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.RequiresResize;
import com.gwtplatform.mvp.client.View;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

public interface BaseEditorView
        extends View, Focus, HasKeyDownHandlers, HasKeyUpHandlers, HasText,
        HasValueChangeHandlers<String>, RequiresResize {

    String getEditorId();

    void setText(String text);

    boolean isClean();

    void markClean();

    void insertTextAtCursor(String text);

    void replaceSelectedText(String text);

    void insertSnippet(String snippet);

    void setReadOnly(boolean readOnly);

    void setMode(AceEditorMode mode);

    void setTheme(AceEditorTheme theme);

    void setUserKeyBindingsPreference(boolean useVimBindings);

    void setUserLiveAutoCompletePreference(boolean isOn);
}
