package stroom.externaldoc.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import stroom.externaldoc.client.ExternalDocRefPlugin;
import stroom.externaldoc.client.presenter.ExternalDocRefPresenter;

public interface ExternalDocRefGinjector {
    AsyncProvider<ExternalDocRefPlugin> getExternalDocRefPlugin();

    AsyncProvider<ExternalDocRefPresenter> getExternalDocRefPresenter();
}
