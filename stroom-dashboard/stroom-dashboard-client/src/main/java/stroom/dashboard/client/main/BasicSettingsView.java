package stroom.dashboard.client.main;

import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.View;

public interface BasicSettingsView extends View, Focus {

    void setId(String id);

    String getName();

    void setName(String name);
}
