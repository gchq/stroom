package stroom.annotations.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import stroom.annotations.client.AnnotationsIndexPlugin;
import stroom.annotations.client.presenter.AnnotationsIndexExternalPresenter;

public interface AnnotationsIndexGinjector {
    AsyncProvider<AnnotationsIndexPlugin> getAnnotationsIndexPluging();

    AsyncProvider<AnnotationsIndexExternalPresenter> getAnnotationsIndexExternalPresenter();
}
