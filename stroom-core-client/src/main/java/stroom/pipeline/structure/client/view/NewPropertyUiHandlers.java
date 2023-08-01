package stroom.pipeline.structure.client.view;

import stroom.pipeline.structure.client.presenter.PropertyListPresenter.Source;

import com.gwtplatform.mvp.client.UiHandlers;

public interface NewPropertyUiHandlers extends UiHandlers {

    void onSourceChange(Source source);
}
