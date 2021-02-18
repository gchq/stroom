package stroom.document.client.gin;

import stroom.document.client.NewUiDocumentPlugin;

import com.google.gwt.inject.client.AsyncProvider;

public interface NewUiGinjector {

    AsyncProvider<NewUiDocumentPlugin> getNewUiDocumentPlugin();
}
