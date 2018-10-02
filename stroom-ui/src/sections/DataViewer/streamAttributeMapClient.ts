import { actionCreators } from "./redux";
import { wrappedGet, wrappedPost } from "../../lib/fetchTracker.redux";
import {
  StreamAttributeMapResult,
  ExpressionOperatorType,
  DataSourceType,
  DataRow,
  ExpressionOperatorWithUuid,
  ExpressionItemWithUuid
} from "../../types";
import { Dispatch } from "redux";
import { GlobalStoreState } from "../../startup/reducers";

export const search = (
  dataViewerId: string,
  pageOffset: number,
  pageSize: number,
  addResults?: boolean
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();

  let url = `${
    state.config.values.stroomBaseServiceUrl
  }/streamattributemap/v1/?`;
  url += `pageSize=${pageSize}`;
  url += `&pageOffset=${pageOffset}`;

  wrappedGet(
    dispatch,
    state,
    url,
    response => {
      response.json().then((data: StreamAttributeMapResult) => {
        if (addResults) {
          dispatch(
            actionCreators.add(
              dataViewerId,
              data.streamAttributeMaps,
              data.pageResponse.total,
              pageSize,
              pageOffset
            )
          );
        } else {
          dispatch(
            actionCreators.updateStreamAttributeMaps(
              dataViewerId,
              data.streamAttributeMaps,
              data.pageResponse.total,
              pageSize,
              pageOffset
            )
          );
        }
      });
    },
    {},
    true
  );
};

export const searchWithExpression = (
  dataViewerId: string,
  pageOffset: number,
  pageSize: number,
  expressionId: string,
  addResults?: boolean
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();
  const expressionState = state.expressionBuilder[expressionId];

  const expression = cleanExpression(expressionState.expression);

  let url = `${
    state.config.values.stroomBaseServiceUrl
  }/streamattributemap/v1/?`;
  url += `pageSize=${pageSize}`;
  url += `&pageOffset=${pageOffset}`;

  wrappedPost(
    dispatch,
    state,
    url,
    response => {
      response.json().then((data: StreamAttributeMapResult) => {
        if (addResults) {
          dispatch(
            actionCreators.add(
              dataViewerId,
              data.streamAttributeMaps,
              data.pageResponse.total,
              pageSize,
              pageOffset
            )
          );
        } else {
          dispatch(
            actionCreators.updateStreamAttributeMaps(
              dataViewerId,
              data.streamAttributeMaps,
              data.pageResponse.total,
              pageSize,
              pageOffset
            )
          );
        }
      });
    },
    {
      body: JSON.stringify(expression)
    }
  );
};

/**
 * TODO: shouldn't actually have to use this -- ideally the ExpressionBuilder would
 * generate JSON compatible with the resource's endpoints. I.e. jackson binding
 * fails if we have these uuids.
 */
const cleanExpression = (
  expression: ExpressionOperatorWithUuid
): ExpressionOperatorType => {
  // UUIDs are not part of Expression
  delete expression.uuid;
  expression.children!.forEach((child: ExpressionItemWithUuid) => {
    delete child.uuid;
  });

  // Occasionally the ExpressionBuilder will put a value on the root, which is wrong.
  // It does this when there's an underscore in the term, e.g. feedName=thing_thing.
  //delete expression.value; // TODO oh rly?
  return expression;
};

export const fetchDataSource = (dataViewerId: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/streamattributemap/v1/dataSource`;

  wrappedGet(
    dispatch,
    state,
    url,
    response => {
      response.json().then((data: DataSourceType) => {
        dispatch(actionCreators.updateDataSource(dataViewerId, data));
      });
    },
    {},
    true
  );
};

export const getDetailsForSelectedRow = (dataViewerId: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const dataView = state.dataViewers[dataViewerId];
  const streamId = dataView.streamAttributeMaps[dataView.selectedRow].data.id;
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/streamattributemap/v1/${streamId}`;

  wrappedGet(
    dispatch,
    state,
    url,
    response => {
      response.json().then((data: DataRow) => {
        dispatch(
          actionCreators.updateDetailsForSelectedRow(dataViewerId, data)
        );
      });
    },
    {},
    true
  );
};
