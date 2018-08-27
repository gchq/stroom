import { actionCreators } from './redux';
import { wrappedGet, wrappedPost } from 'lib/fetchTracker.redux';

export const search = (dataViewerId, pageOffset, pageSize) => (dispatch, getState) => {
  const state = getState();

  let url = `${state.config.streamAttributeMapServiceUrl}/?`;
  url += `pageSize=${pageSize}`;
  url += `&pageOffset=${pageOffset}`;

  wrappedGet(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateStreamAttributeMaps(
          dataViewerId,
          data.streamAttributeMaps,
          data.pageResponse.total,
          pageSize,
          pageOffset,
        ));
      });
    },
    null,
    true,
  );
};

export const searchWithExpression = (dataViewerId, pageOffset, pageSize, expressionId) => (
  dispatch,
  getState,
) => {
  const state = getState();
  let expression = state.expressionBuilder.expressions[expressionId];

  expression = cleanExpression(expression);

  let url = `${state.config.streamAttributeMapServiceUrl}/?`;
  url += `pageSize=${pageSize}`;
  url += `&pageOffset=${pageOffset}`;

  wrappedPost(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateStreamAttributeMaps(
          dataViewerId,
          data.streamAttributeMaps,
          data.pageResponse.total,
          pageSize,
          pageOffset,
        ));
      });
    },
    {
      body: JSON.stringify(expression),
    },
    true,
  );
};

/**
 * TODO: shouldn't actually have to use this -- ideally the ExpressionBuilder would
 * generate Jackson-compatible JSON.
 */
const cleanExpression = (expression) => {
  // UUIDs are not part of Expression
  delete expression.uuid;
  expression.children.forEach((child) => {
    delete child.uuid;
  });

  // Occasionally the ExpressionBuilder will put a value on the root, which is wrong.
  // It does this when there's an underscore in the term, e.g. feedName=thing_thing.
  delete expression.value;
  return expression;
};

export const fetchDataSource = dataViewerId => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.streamAttributeMapServiceUrl}/dataSource`;

  wrappedGet(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateDataSource(dataViewerId, data));
      });
    },
    null,
    true,
  );
};

export const getDetailsForSelectedRow = dataViewerId => (dispatch, getState) => {
  const state = getState();
  const dataView = state.dataViewers[dataViewerId];
  const streamId = dataView.streamAttributeMaps[dataView.selectedRow].stream.id;
  const url = `${state.config.streamAttributeMapServiceUrl}/${streamId}`;

  wrappedGet(
    dispatch,
    state,
    url,
    (response) => {
      response.json().then((data) => {
        dispatch(actionCreators.updateDetailsForSelectedRow(dataViewerId, data));
      });
    },
    null,
    true,
  );
};
