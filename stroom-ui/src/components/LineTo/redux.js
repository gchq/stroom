/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { createActions, handleActions } from 'redux-actions';

import { deleteItemFromObject } from 'lib/treeUtils';

const actionCreators = createActions({
  LINE_CONTAINER_CREATED: lineContextId => ({
    lineContextId,
  }),
  LINE_CONTAINER_DESTROYED: lineContextId => ({
    lineContextId,
  }),
  LINE_CREATED: (lineContextId, lineId, lineType, fromId, toId) => ({
    lineContextId,
    lineId,
    lineType,
    fromId,
    toId,
  }),
  LINE_DESTROYED: (lineContextId, lineId) => ({
    lineContextId,
    lineId,
  }),
});

// State will be an object
const defaultState = {};

const reducer = handleActions(
  {
    LINE_CONTAINER_CREATED: (state, { payload: { lineContextId } }) => ({
      ...state,
      [lineContextId]: {},
    }),

    LINE_CONTAINER_DESTROYED: (state, { payload: { lineContextId } }) =>
      deleteItemFromObject(state, lineContextId),

    LINE_CREATED: (state, {
      payload: {
        lineContextId, lineId, lineType, fromId, toId,
      },
    }) => ({
      ...state,
      [lineContextId]: {
        ...state[lineContextId],
        [lineId]: {
          lineType,
          fromId,
          toId,
        },
      },
    }),

    LINE_DESTROYED: (state, { payload: { lineContextId, lineId } }) => {
      if (state[lineContextId]) {
        return {
          ...state,
          [lineContextId]: deleteItemFromObject(state[lineContextId], lineId),
        };
      }
      // May have already deleted the container
      return state;
    },
  },
  defaultState,
);

export { reducer, actionCreators };
