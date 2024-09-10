package stroom.processor.client.presenter;

import stroom.task.client.TaskHandlerFactory;

import com.gwtplatform.mvp.client.UiHandlers;

public interface BatchProcessorFilterEditUiHandlers extends UiHandlers {

    void validate();

    void apply(TaskHandlerFactory taskHandlerFactory);
}
