package stroom.security.client.presenter;

import stroom.task.client.TaskListener;

import com.gwtplatform.mvp.client.UiHandlers;

public interface DocumentPermissionsEditUiHandlers extends UiHandlers {

    void apply(TaskListener taskListener);
}
