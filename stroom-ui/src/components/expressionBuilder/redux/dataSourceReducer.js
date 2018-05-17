import { createAction, handleActions, combineActions } from 'redux-actions';

// Data Sources
const receiveDataSource = createAction('RECEIVE_DATA_SOURCE',
    (uuid, dataSource) => ({uuid, dataSource}));

// data source definitions, keyed on doc ref UUID
const defaultDataSourceState = {};

const dataSourceReducer = handleActions({
        [receiveDataSource]:
        (state, action) => ({
                ...state,
                [action.payload.uuid] : action.payload.dataSource
        }),
    },
    defaultDataSourceState
);

export {
    receiveDataSource,
    dataSourceReducer
}