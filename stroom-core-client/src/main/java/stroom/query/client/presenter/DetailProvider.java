package stroom.query.client.presenter;

import stroom.query.shared.QueryHelpRow;

import java.util.function.Consumer;

public interface DetailProvider {

    void getDetail(QueryHelpRow row,
                   Consumer<Detail> consumer);
}
