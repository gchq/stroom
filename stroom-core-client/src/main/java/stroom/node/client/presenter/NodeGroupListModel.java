package stroom.node.client.presenter;

import stroom.credentials.shared.Credential;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.node.client.NodeGroupClient;
import stroom.node.shared.FindNodeGroupRequest;
import stroom.node.shared.NodeGroup;
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

public class NodeGroupListModel
        implements
        SelectionListModel<NodeGroup, SimpleSelectionItemWrapper<NodeGroup>>,
        HasTaskMonitorFactory,
        HasHandlers {

    private final EventBus eventBus;
    private final NodeGroupClient nodeGroupClient;
    private FindNodeGroupRequest lastRequest;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    public NodeGroupListModel(final EventBus eventBus,
                              final NodeGroupClient nodeGroupClient) {
        this.eventBus = eventBus;
        this.nodeGroupClient = nodeGroupClient;
    }

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<NodeGroup> parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<NodeGroup>>> consumer) {
        final FindNodeGroupRequest request = new FindNodeGroupRequest(
                pageRequest,
                FindNodeGroupRequest.DEFAULT_SORT_LIST,
                filter);

        // Only fetch if the request has changed.
        if (!request.equals(lastRequest)) {
            lastRequest = request;
            nodeGroupClient.find(request, response -> {
                // Only update if the request is still current.
                if (request == lastRequest) {
                    final ResultPage<SimpleSelectionItemWrapper<NodeGroup>> resultPage;
                    final List<SimpleSelectionItemWrapper<NodeGroup>> items = response
                            .getValues()
                            .stream()
                            .map(this::wrap)
                            .collect(Collectors.toList());

                    // Insert null select item.
                    final List<SimpleSelectionItemWrapper<NodeGroup>> newList = new ArrayList<>();
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
    public SimpleSelectionItemWrapper<NodeGroup> wrap(final NodeGroup item) {
        if (item == null) {
            return null;
        }
        return new SimpleSelectionItemWrapper<>(item.getName(), item);
    }

    @Override
    public NodeGroup unwrap(final SimpleSelectionItemWrapper<NodeGroup> selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getItem();
    }

    @Override
    public boolean isEmptyItem(final SimpleSelectionItemWrapper<NodeGroup> selectionItem) {
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
