package stroom.analytics.client.presenter;

import stroom.job.client.presenter.DateTimeBox;
import stroom.job.client.presenter.ScheduleBox;

import com.google.gwt.user.client.ui.Focus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

import java.util.List;

public interface ExecutionScheduleEditView extends View,
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
}
