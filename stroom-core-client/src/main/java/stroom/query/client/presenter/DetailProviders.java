package stroom.query.client.presenter;

import stroom.query.shared.QueryHelpRow;
import stroom.query.shared.QueryHelpType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class DetailProviders implements DetailProvider {

    private final Map<QueryHelpType, DetailProvider> map = new HashMap<>();

    @Inject
    DetailProviders(final DataSourceDetailProvider dataSourceDetailProvider,
                    final FieldDetailProvider fieldDetailProvider,
                    final FunctionDetailProvider functionDetailProvider,
                    final TitleDetailProvider titleDetailProvider,
                    final StructureDetailProvider structureDetailProvider) {
        map.put(QueryHelpType.DATA_SOURCE, dataSourceDetailProvider);
        map.put(QueryHelpType.FIELD, fieldDetailProvider);
        map.put(QueryHelpType.FUNCTION, functionDetailProvider);
        map.put(QueryHelpType.TITLE, titleDetailProvider);
        map.put(QueryHelpType.STRUCTURE, structureDetailProvider);
    }

    @Override
    public void getDetail(final QueryHelpRow row, final Consumer<Detail> consumer) {
        getProvider(row).getDetail(row, consumer);
    }

    private DetailProvider getProvider(final QueryHelpRow row) {
        return map.get(row.getType());
    }
}
