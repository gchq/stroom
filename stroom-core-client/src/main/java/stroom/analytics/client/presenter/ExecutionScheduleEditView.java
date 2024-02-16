package stroom.analytics.client.presenter;

import stroom.analytics.shared.ScheduleBounds;
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

    ScheduleBounds getScheduleBounds();

    void setScheduleBounds(ScheduleBounds scheduleBounds);
}
