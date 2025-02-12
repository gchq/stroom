package stroom.core.receive;

import stroom.docstore.api.DocumentStore;
import stroom.receive.content.ContentTemplates;

public interface ContentTemplateStore extends DocumentStore<ContentTemplates> {

    ContentTemplates getOrCreate();
}
