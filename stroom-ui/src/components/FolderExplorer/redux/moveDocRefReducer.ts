/*
 * Moveright 2018 Crown Moveright
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
import { Action, ActionCreator } from "redux";

import {
  prepareReducerById,
  StateById,
  ActionId
} from "../../../lib/redux-actions-ts";
import { DOC_REFS_COPIED, DocRefsMoved } from "./documentTree";

const PREPARE_DOC_REF_MOVE = "PREPARE_DOC_REF_MOVE";
const COMPLETE_DOC_REF_MOVE = "COMPLETE_DOC_REF_MOVE";

export interface PrepareDocRefMove
  extends ActionId,
    Action<"PREPARE_DOC_REF_MOVE"> {
  uuids: Array<string>;
  destinationUuid: string;
}

export interface CompleteDocRefMove
  extends ActionId,
    Action<"COMPLETE_DOC_REF_MOVE"> {}

export interface ActionCreators {
  prepareDocRefMove: ActionCreator<PrepareDocRefMove>;
  completeDocRefMove: ActionCreator<CompleteDocRefMove>;
}

export const actionCreators: ActionCreators = {
  prepareDocRefMove: (id, uuids, destinationUuid) => ({
    type: PREPARE_DOC_REF_MOVE,
    id,
    uuids,
    destinationUuid
  }),
  completeDocRefMove: id => ({ type: COMPLETE_DOC_REF_MOVE, id })
};

export interface StoreStatePerId {
  isMoveing: boolean;
  uuids: Array<string>;
  destinationUuid?: string;
}

export interface StoreState extends StateById<StoreStatePerId> {}

// The state will contain a map of arrays.
// Keyed on explorer ID, the arrays will contain the doc refs being moved
export const defaultStatePerId: StoreStatePerId = {
  isMoveing: false,
  uuids: [],
  destinationUuid: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PrepareDocRefMove>(
    PREPARE_DOC_REF_MOVE,
    (state, { uuids, destinationUuid }: PrepareDocRefMove) => ({
      isMoveing: uuids.length > 0,
      uuids,
      destinationUuid
    })
  )
  .handleAction<CompleteDocRefMove>(
    COMPLETE_DOC_REF_MOVE,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefsMoved>(DOC_REFS_COPIED, () => defaultStatePerId)
  .getReducer();
