package stroom.index.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import stroom.index.client.IndexPlugin;

public interface IndexGinjector {
    AsyncProvider<IndexPlugin> getIndexPlugin();
}
