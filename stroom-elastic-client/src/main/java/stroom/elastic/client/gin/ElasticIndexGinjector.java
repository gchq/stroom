package stroom.elastic.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import stroom.elastic.client.ElasticIndexPlugin;
import stroom.elastic.client.presenter.ElasticIndexPresenter;

public interface ElasticIndexGinjector {
    AsyncProvider<ElasticIndexPlugin> getElasticIndexPluging();

    AsyncProvider<ElasticIndexPresenter> getElasticIndexPresenter();
}
