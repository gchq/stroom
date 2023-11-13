package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.NavigationModel;
import stroom.item.client.SelectionItem;
import stroom.query.client.DataSourceClient;
import stroom.util.shared.PageRequest;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicFieldSelectionListModel implements FieldSelectionListModel {

    private final DataSourceClient dataSourceClient;
    private final AsyncDataProvider<SelectionItem> dataProvider;
    private final NavigationModel navigationModel = new NavigationModel();
    private DocRef dataSourceRef;
    private StringMatch filter;

    @Inject
    public DynamicFieldSelectionListModel(final DataSourceClient dataSourceClient) {
        this.dataSourceClient = dataSourceClient;
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
            dataSourceClient.findFields(findFieldInfoCriteria, result -> {
                final List<SelectionItem> items = result
                        .getValues()
                        .stream()
                        .map(FieldInfoSelectionItem::new)
                        .collect(Collectors.toList());
                display.setRowData(0, items);
                display.setRowCount(result.getPageResponse().getLength(), true);
            });
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
    public void findFieldByName(final String fieldName, final Consumer<FieldInfo> consumer) {
        dataSourceClient.findFieldByName(dataSourceRef, fieldName, consumer);
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
