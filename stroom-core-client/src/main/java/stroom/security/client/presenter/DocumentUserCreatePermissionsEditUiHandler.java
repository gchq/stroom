package stroom.security.client.presenter;

import stroom.task.client.TaskMonitorFactory;

import com.gwtplatform.mvp.client.UiHandlers;

public interface DocumentUserCreatePermissionsEditUiHandler extends UiHandlers {

    void onApplyToDescendants(TaskMonitorFactory taskMonitorFactory);
}
