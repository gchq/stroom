package stroom.document.client.gin;

import com.google.gwt.inject.client.AsyncProvider;
import stroom.document.client.NewUiDocumentPlugin;

public interface NewUiGinjector {
    AsyncProvider<NewUiDocumentPlugin> getNewUiDocumentPlugin();
}
