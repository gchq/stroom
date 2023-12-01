package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.item.client.NavigationModel;
import stroom.query.client.DataSourceClient;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.view.client.AbstractDataProvider;
import com.google.gwt.view.client.AsyncDataProvider;
import com.google.gwt.view.client.HasData;
import com.google.gwt.view.client.Range;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicFieldSelectionListModel implements FieldSelectionListModel {

    private final DataSourceClient dataSourceClient;
    private final AsyncDataProvider<FieldInfoSelectionItem> dataProvider;
    private DocRef dataSourceRef;
    private StringMatch filter;
    private FindFieldInfoCriteria lastCriteria;

    @Inject
    public DynamicFieldSelectionListModel(final DataSourceClient dataSourceClient) {
        this.dataSourceClient = dataSourceClient;
        dataProvider = new AsyncDataProvider<FieldInfoSelectionItem>() {
            @Override
            protected void onRangeChanged(final HasData<FieldInfoSelectionItem> display) {
                refresh(display);
            }
        };
    }

    private void refresh(final HasData<FieldInfoSelectionItem> display) {
        if (dataSourceRef != null) {
            final Range range = display.getVisibleRange();
            final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                    new PageRequest(range.getStart(), range.getLength()),
                    null,
                    dataSourceRef,
                    filter);

            // Only fetch if the request has changed.
            if (!findFieldInfoCriteria.equals(lastCriteria)) {
                lastCriteria = findFieldInfoCriteria;
                dataSourceClient.findFields(findFieldInfoCriteria, response -> {
                    // Only update if the request is still current.
                    if (findFieldInfoCriteria == lastCriteria) {
                        final ResultPage<FieldInfoSelectionItem> resultPage;
                        if (response.getValues().size() > 0) {
                            final List<FieldInfoSelectionItem> items = response
                                    .getValues()
                                    .stream()
                                    .map(this::wrap)
                                    .collect(Collectors.toList());
                            resultPage = new ResultPage<>(items, response.getPageResponse());
                        } else {
                            // Create empty item.
                            final List<FieldInfoSelectionItem> items = Collections.singletonList(
                                    new FieldInfoSelectionItem(null));
                            resultPage = new ResultPage<>(items, new PageResponse(0, 1, 1L, true));
                        }

                        display.setRowData(resultPage.getPageStart(), resultPage.getValues());
                        display.setRowCount(resultPage.getPageSize(), resultPage.isExact());
                    }
                });
            }
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    @Override
    public AbstractDataProvider<FieldInfoSelectionItem> getDataProvider() {
        return dataProvider;
    }

    @Override
    public NavigationModel<FieldInfoSelectionItem> getNavigationModel() {
        return null;
    }

    @Override
    public void reset() {
        filter = StringMatch.any();
        lastCriteria = null;
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
        for (final HasData<FieldInfoSelectionItem> display : dataProvider.getDataDisplays()) {
            refresh(display);
        }
    }

    @Override
    public void findFieldByName(final String fieldName, final Consumer<FieldInfo> consumer) {
        dataSourceClient.findFieldByName(dataSourceRef, fieldName, consumer);
    }

    @Override
    public boolean displayFilter() {
        return true;
    }

    @Override
    public boolean displayPath() {
        return false;
    }

    @Override
    public boolean displayPager() {
        return true;
    }

    @Override
    public FieldInfoSelectionItem wrap(final FieldInfo item) {
        return new FieldInfoSelectionItem(item);
    }

    @Override
    public FieldInfo unwrap(final FieldInfoSelectionItem selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getFieldInfo();
    }
}
