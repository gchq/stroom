package stroom.security.client.presenter;

import stroom.task.client.TaskMonitorFactory;

import com.gwtplatform.mvp.client.UiHandlers;

public interface BatchDocumentPermissionsEditUiHandlers extends UiHandlers {

    void validate();

    void apply(TaskMonitorFactory taskMonitorFactory);
}
