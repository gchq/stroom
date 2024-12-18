package stroom.security.client.presenter;

import stroom.task.client.TaskMonitorFactory;

import com.gwtplatform.mvp.client.UiHandlers;

public interface DocumentUserPermissionsEditUiHandler extends UiHandlers {
    void onEditCreatePermissions(TaskMonitorFactory taskMonitorFactory);

    void onApplyToDescendants(TaskMonitorFactory taskMonitorFactory);
}
