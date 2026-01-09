package stroom.credentials.client.presenter;

import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.item.client.SelectionListModel;
import stroom.item.client.SimpleSelectionItemWrapper;
import stroom.security.shared.DocumentPermission;
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
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CredentialListModel
        implements
        SelectionListModel<Credential, SimpleSelectionItemWrapper<Credential>>,
        HasTaskMonitorFactory,
        HasHandlers {

    private final EventBus eventBus;
    private final CredentialClient credentialClient;
    private final Set<CredentialType> credentialTypes;
    private FindCredentialRequest lastCriteria;
    private TaskMonitorFactory taskMonitorFactory = new DefaultTaskMonitorFactory(this);

    public CredentialListModel(final EventBus eventBus,
                               final CredentialClient credentialClient,
                               final Set<CredentialType> credentialTypes) {
        this.eventBus = eventBus;
        this.credentialClient = credentialClient;
        this.credentialTypes = credentialTypes;
    }

    @Override
    public void onRangeChange(final SimpleSelectionItemWrapper<Credential> parent,
                              final String filter,
                              final boolean filterChange,
                              final PageRequest pageRequest,
                              final Consumer<ResultPage<SimpleSelectionItemWrapper<Credential>>> consumer) {
        final FindCredentialRequest findFieldInfoCriteria = new FindCredentialRequest(
                pageRequest,
                FindCredentialRequest.DEFAULT_SORT_LIST,
                filter,
                credentialTypes,
                DocumentPermission.VIEW);

        // Only fetch if the request has changed.
        if (!findFieldInfoCriteria.equals(lastCriteria)) {
            lastCriteria = findFieldInfoCriteria;
            credentialClient.findCredentials(findFieldInfoCriteria, response -> {
                // Only update if the request is still current.
                if (findFieldInfoCriteria == lastCriteria) {
                    final ResultPage<SimpleSelectionItemWrapper<Credential>> resultPage;
                    final List<SimpleSelectionItemWrapper<Credential>> items = response
                            .getValues()
                            .stream()
                            .map(this::wrap)
                            .collect(Collectors.toList());

                    // Insert null select item.
                    final List<SimpleSelectionItemWrapper<Credential>> newList = new ArrayList<>();
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
        lastCriteria = null;
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
    public SimpleSelectionItemWrapper<Credential> wrap(final Credential item) {
        if (item == null) {
            return null;
        }
        return new SimpleSelectionItemWrapper<>(item.getName(), item);
    }

    @Override
    public Credential unwrap(final SimpleSelectionItemWrapper<Credential> selectionItem) {
        if (selectionItem == null) {
            return null;
        }
        return selectionItem.getItem();
    }

    @Override
    public boolean isEmptyItem(final SimpleSelectionItemWrapper<Credential> selectionItem) {
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
