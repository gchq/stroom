package stroom.index.client.gin;

import stroom.index.client.IndexPlugin;
import stroom.index.client.presenter.IndexFieldEditPresenter;
import stroom.index.client.presenter.IndexPresenter;
import stroom.index.client.presenter.IndexSettingsPresenter;

import com.google.gwt.inject.client.AsyncProvider;

public interface IndexGinjector {

    AsyncProvider<IndexPlugin> getIndexPlugin();

    AsyncProvider<IndexPresenter> getIndexPresenter();

    AsyncProvider<IndexSettingsPresenter> getIndexSettingsPresenter();

    AsyncProvider<IndexFieldEditPresenter> getIndexFieldEditPresenter();
}
