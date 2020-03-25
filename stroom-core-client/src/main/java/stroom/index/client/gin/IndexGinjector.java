package stroom.index.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import stroom.index.client.IndexPlugin;
import stroom.index.client.presenter.IndexFieldEditPresenter;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.client.presenter.IndexSettingsPresenter;

public interface IndexGinjector {
    AsyncProvider<IndexPlugin> getIndexPlugin();

    AsyncProvider<IndexPresenter> getIndexPresenter();

    AsyncProvider<IndexSettingsPresenter> getIndexSettingsPresenter();

    AsyncProvider<IndexFieldEditPresenter> getIndexFieldEditPresenter();
}
