package stroom.data.client.view;

import stroom.task.client.TaskListener;

import com.gwtplatform.mvp.client.View;

public interface TaskListenerView extends View, TaskListener {

    void setChildView(final View childView);
}
