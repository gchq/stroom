package stroom.data.client.presenter;

import com.gwtplatform.mvp.client.View;

public interface ClassificationWrapperView extends View {

    void setContent(View content);

    void setClassification(String classification);
}
