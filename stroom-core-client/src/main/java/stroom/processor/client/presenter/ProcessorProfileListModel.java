package stroom.processor.client.presenter;

import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.node.shared.NodeGroup;
import stroom.processor.shared.FindProcessorProfileRequest;
import stroom.task.client.DefaultTaskMonitorFactory;
import stroom.task.client.HasTaskMonitorFactory;
import stroom.task.client.TaskMonitorFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.ResultPage;

import com.google.gwt.event.shared.GwtEvent;
import com.google.gwt.event.shared.HasHandlers;
import com.google.web.bindery.event.shared.EventBus;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ProcessorProfileListModel
        implements
        SelectionListModel<String, SimpleSelectionItemWrapper<String>>,
        HasTaskMonitorFactory,
        HasHandlers {

    private final EventBus eventBus;
    private final ProcessorProfileClient processorProfileClient;
    private FindProcessorProfileRequest lastRequest;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    public ProcessorProfileListModel(final EventBus eventBus,
                                     final ProcessorProfileClient processorProfileClient) {
        this.eventBus = eventBus;
        this.processorProfileClient = processorProfileClient;
    }

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<String> parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<String>>> consumer) {
        final FindProcessorProfileRequest request = new FindProcessorProfileRequest(
                pageRequest,
                FindProcessorProfileRequest.DEFAULT_SORT_LIST,
                filter);

        // Only fetch if the request has changed.
        if (!request.equals(lastRequest)) {
            lastRequest = request;
            processorProfileClient.find(request, response -> {
                // Only update if the request is still current.
                if (request == lastRequest) {
                    final ResultPage<SimpleSelectionItemWrapper<String>> resultPage;
                    final List<SimpleSelectionItemWrapper<String>> items = response
                            .getValues()
                            .stream()
                            .map(processorProfile ->
                                    new SimpleSelectionItemWrapper<>(processorProfile.getName(),
                                            processorProfile.getName()))
                            .collect(Collectors.toList());

                    // Insert null select item.
                    final List<SimpleSelectionItemWrapper<String>> newList = new ArrayList<>();
                    newList.add(new SimpleSelectionItemWrapper<>("[ none ]", null));
                    newList.addAll(items);

                    final PageResponse pageResponse = response.getPageResponse();
                    final PageResponse newPageResponse = new PageResponse(
                            pageResponse.getOffset(),
                            pageResponse.getLength() + 1,
                            pageResponse.getTotal() == null
                                    ? null
                                    : pageResponse.getTotal() + 1,
                            pageResponse.isExact());
                    resultPage = new ResultPage<>(newList, newPageResponse);
                    consumer.accept(resultPage);
                }
            }, null, taskMonitorFactory);
        }
    }

    @Override
    public void reset() {
        lastRequest = null;
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
    public SimpleSelectionItemWrapper<String> wrap(final String item) {
        if (item == null) {
            return null;
        }
        return new SimpleSelectionItemWrapper<>(item, item);
    }

    @Override
    public String unwrap(final SimpleSelectionItemWrapper<String> selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getItem();
    }

    @Override
    public boolean isEmptyItem(final SimpleSelectionItemWrapper<String> selectionItem) {
        return unwrap(selectionItem) == null;
    }

    @Override
    public void setTaskMonitorFactory(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    @Override
    public void fireEvent(final GwtEvent<?> gwtEvent) {
        eventBus.fireEvent(gwtEvent);
    }
}
