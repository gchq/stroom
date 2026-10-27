package stroom.sqlstore.client.component;

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.Column;
import stroom.query.client.component.AbstractQueryDataController;
import stroom.query.client.component.QueryDataPane;
import stroom.query.client.component.QueryResultTableController;
import stroom.query.client.presenter.DateTimeSettingsFactory;
import stroom.query.client.presenter.ResultStoreModel;
import stroom.sqlstore.shared.SqlTemporalStoreDoc;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

import java.util.Arrays;
import java.util.List;

/// A SQL temporal store's Data tab: a StroomQL query of its keys and values.
public class SqlTemporalStoreDataController
        extends AbstractQueryDataController<SqlTemporalStoreDoc> {


    private final Column timeCol = Column.builder()
            .id("EffectiveTime")
            .name("Effective Time")
            .expression("EffectiveTime")
            .build();
    private final Column keyCol = Column.builder()
            .id("Key")
            .name("Key")
            .expression("Key")
            .build();
    private final Column valueCol = Column.builder()
            .id("Value")
            .name("Value")
            .expression("substring(Value, 1, 100)")
            .build();

    /// Made by GIN, with its content and its parts.
    @Inject
    public SqlTemporalStoreDataController(final EventBus eventBus,
                                          final QueryDataPane view,
                                          final QueryResultTableController tablePresenter,
                                          final RestFactory restFactory,
                                          final DateTimeSettingsFactory dateTimeSettingsFactory,
                                          final ResultStoreModel resultStoreModel) {
        super(eventBus, view, tablePresenter, restFactory, dateTimeSettingsFactory, resultStoreModel);
    }

    @Override
    protected String getDefaultQuery(final DocRef docRef, final SqlTemporalStoreDoc doc) {
        return "from \"" + docRef.getName() + "\" limit 100 select EffectiveTime as \"Effective Time\", Key, Value";
    }

    @Override
    protected List<Column> getPreferredColumns(final SqlTemporalStoreDoc doc) {
        return Arrays.asList(timeCol, keyCol, valueCol);
    }
}
