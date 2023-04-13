package stroom.widget.customdatebox.client;

import com.google.gwt.user.client.ui.Focus;
import com.google.gwt.user.client.ui.HasValue;

public interface DateBoxView extends HasValue<String>, Focus {

    Long getMilliseconds();

    void setMilliseconds(Long milliseconds);
}
