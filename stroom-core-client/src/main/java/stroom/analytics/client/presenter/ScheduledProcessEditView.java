package stroom.analytics.client.presenter;

import stroom.schedule.client.ScheduleBox;
import stroom.widget.datepicker.client.DateTimeBox;

import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public interface ScheduledProcessEditView extends View,
        HasUiHandlers<ProcessingStatusUiHandlers>,
        Focus {

    String getName();

    void setName(String name);

    boolean isEnabled();

    void setEnabled(final boolean enabled);

    void setNodes(List<String> nodes);

    String getNode();

    void setNode(String node);

    ScheduleBox getScheduleBox();

    DateTimeBox getStartTime();

    DateTimeBox getEndTime();

    void setRunAsUserView(View view);
}
