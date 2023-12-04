package stroom.query.client.presenter;

import stroom.datasource.api.v2.FieldInfo;
import stroom.datasource.api.v2.FindFieldInfoCriteria;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.query.client.DataSourceClient;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import javax.inject.Inject;

public class DynamicFieldSelectionListModel implements FieldSelectionListModel {

    private final DataSourceClient dataSourceClient;
    private DocRef dataSourceRef;
    private FindFieldInfoCriteria lastCriteria;

    @Inject
    public DynamicFieldSelectionListModel(final DataSourceClient dataSourceClient) {
        this.dataSourceClient = dataSourceClient;
    }

    @Override
    public void onRangeChange(final FieldInfoSelectionItem parent,
                              final String filter,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<FieldInfoSelectionItem>> consumer) {
        if (dataSourceRef != null) {
            final StringMatch stringMatch = StringMatch.contains(filter);
            final FindFieldInfoCriteria findFieldInfoCriteria = new FindFieldInfoCriteria(
                    pageRequest,
                    null,
                    dataSourceRef,
                    stringMatch);

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

                        consumer.accept(resultPage);
                    }
                });
            }
        }
    }

    public void setDataSourceRef(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    @Override
    public void reset() {
        lastCriteria = null;
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
