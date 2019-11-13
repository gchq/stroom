package stroom.explorer.api;

import stroom.query.api.v2.DocRef;

import java.util.List;

public interface ExplorerDecorator {
    List<DocRef> list();
}
