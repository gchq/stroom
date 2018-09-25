import { Action, ActionCreator } from "redux";

import {
  prepareReducerById,
  ActionId,
  StateById
} from "../../../lib/redux-actions-ts";

import { DOC_REF_CREATED, DocRefCreated } from "./documentTree";
import { DocRefType } from "../../../types";

export const PREPARE_DOC_REF_CREATION = "PREPARE_DOC_REF_CREATION";
export const COMPLETE_DOC_REF_CREATION = "COMPLETE_DOC_REF_CREATION";

export interface PrepareDocRefCreation
  extends ActionId,
    Action<"PREPARE_DOC_REF_CREATION"> {
  destination: DocRefType;
}

export interface CompleteDocRefCreation
  extends ActionId,
    Action<"COMPLETE_DOC_REF_CREATION"> {}

export interface ActionCreators {
  prepareDocRefCreation: ActionCreator<PrepareDocRefCreation>;
  completeDocRefCreation: ActionCreator<CompleteDocRefCreation>;
}

export const actionCreators: ActionCreators = {
  prepareDocRefCreation: (id, destination) => ({
    type: PREPARE_DOC_REF_CREATION,
    id,
    destination
  }),
  completeDocRefCreation: id => ({ type: COMPLETE_DOC_REF_CREATION, id })
};

export interface StoreStatePerId {
  isOpen: boolean;
  destination?: DocRefType;
}

export interface StoreState extends StateById<StoreStatePerId> {}

export const defaultStatePerId: StoreStatePerId = {
  isOpen: false,
  destination: undefined
};

export const reducer = prepareReducerById(defaultStatePerId)
  .handleAction<PrepareDocRefCreation>(
    PREPARE_DOC_REF_CREATION,
    (_, { destination }) => ({
      isOpen: true,
      destination
    })
  )
  .handleAction<CompleteDocRefCreation>(
    COMPLETE_DOC_REF_CREATION,
    () => defaultStatePerId
  )
  .handleForeignAction<DocRefCreated>(DOC_REF_CREATED, () => defaultStatePerId)
  .getReducer();
