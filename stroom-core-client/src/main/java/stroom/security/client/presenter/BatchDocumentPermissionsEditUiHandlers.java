package stroom.security.client.presenter;

import stroom.task.client.TaskHandlerFactory;

import com.gwtplatform.mvp.client.UiHandlers;

public interface BatchDocumentPermissionsEditUiHandlers extends UiHandlers {

    void validate();

    void apply(TaskHandlerFactory taskHandlerFactory);
}
