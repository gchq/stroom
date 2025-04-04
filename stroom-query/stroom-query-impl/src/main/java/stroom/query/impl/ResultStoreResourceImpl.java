package stroom.query.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.api.FindResultStoreCriteria;
import stroom.query.api.QueryKey;
import stroom.query.shared.DestroyStoreRequest;
import stroom.query.shared.ResultStoreResource;
import stroom.query.shared.ResultStoreResponse;
import stroom.query.shared.UpdateStoreRequest;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged(OperationType.UNLOGGED)
public class ResultStoreResourceImpl implements ResultStoreResource {

    private final Provider<ResultStoreService> resultStoreServiceProvider;

    @Inject
    public ResultStoreResourceImpl(final Provider<ResultStoreService> resultStoreServiceProvider) {
        this.resultStoreServiceProvider = resultStoreServiceProvider;
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public ResultStoreResponse list(final String nodeName) {
        return resultStoreServiceProvider.get().list(nodeName);
    }

    @AutoLogged(OperationType.UNLOGGED) // Called for each node so too noisy to log and of limited benefit
    @Override
    public ResultStoreResponse find(final String nodeName, final FindResultStoreCriteria criteria) {
        return resultStoreServiceProvider.get().find(nodeName, criteria);
    }

    @Override
    public Boolean update(final String nodeName, final UpdateStoreRequest updateStoreRequest) {
        return resultStoreServiceProvider.get().update(nodeName, updateStoreRequest);
    }

    @Override
    public Boolean exists(final String nodeName, final QueryKey queryKey) {
        return resultStoreServiceProvider.get().exists(nodeName, queryKey);
    }

    //    @AutoLogged(value = OperationType.PROCESS, verb = "Terminating",
//            decorator = TerminateDecorator.class)
    @Override
    public Boolean terminate(final String nodeName, final QueryKey queryKey) {
        return resultStoreServiceProvider.get().terminate(nodeName, queryKey);
    }

    @Override
    public Boolean destroy(final String nodeName, final DestroyStoreRequest request) {
        return resultStoreServiceProvider.get().destroy(nodeName, request);
    }
}
