package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.datasource.shared.DataSourceResource;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.NavigationModel;
import stroom.item.client.SelectionItem;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.gwt.core.client.GWT;
import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicFieldSelectionListModel implements FieldSelectionListModel {

    private static final DataSourceResource DATA_SOURCE_RESOURCE = GWT.create(DataSourceResource.class);

    private final RestFactory restFactory;
    private final AsyncDataProvider<SelectionItem> dataProvider;
    private final NavigationModel navigationModel = new NavigationModel();
    private DocRef dataSourceRef;
    private StringMatch filter;

    @Inject
    public DynamicFieldSelectionListModel(final RestFactory restFactory) {
        this.restFactory = restFactory;

        dataProvider = new AsyncDataProvider<SelectionItem>() {
            @Override
            protected void onRangeChanged(final HasData<SelectionItem> display) {
                refresh(display);
            }
        };
    }

    private void refresh(final HasData<SelectionItem> display) {
        if (dataSourceRef != null) {
            final Range range = display.getVisibleRange();
            final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                    new PageRequest(range.getStart(), range.getLength()),
                    null,
                    dataSourceRef,
                    FieldInfo.FIELDS_PARENT,
                    filter);
            final Rest<ResultPage<FieldInfo>> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        final List<SelectionItem> items = result
                                .getValues()
                                .stream()
                                .map(FieldInfoSelectionItem::new)
                                .collect(Collectors.toList());
                        display.setRowData(0, items);
                        display.setRowCount(result.getPageResponse().getLength(), true);
                    })
                    .call(DATA_SOURCE_RESOURCE)
                    .findFields(findFieldInfoCriteria);
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    @Override
    public AbstractDataProvider<SelectionItem> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel getNavigationModel() {
        return navigationModel;
    }

    @Override
    public void setFilter(final String filter) {
        if (filter == null) {
            this.filter = StringMatch.any();
            refresh();
        } else {
            this.filter = StringMatch.contains(filter);
            refresh();
        }
    }

    @Override
    public void refresh() {
        for (final HasData<SelectionItem> display : dataProvider.getDataDisplays()) {
            refresh(display);
        }
    }

    @Override
    public void fetchFieldByName(final String fieldName, final Consumer<FieldInfo> consumer) {
        if (dataSourceRef != null) {
            final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                    new PageRequest(0, 1),
                    null,
                    dataSourceRef,
                    FieldInfo.FIELDS_PARENT,
                    StringMatch.contains(fieldName));
            final Rest<ResultPage<FieldInfo>> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        if (result.getValues().size() > 0) {
                            consumer.accept(result.getFirst());
                        }
                    })
                    .call(DATA_SOURCE_RESOURCE)
                    .findFields(findFieldInfoCriteria);
        }
    }

    @Override
    public boolean displayPath() {
        return true;
    }

    @Override
    public boolean displayPager() {
        return true;
    }
}
