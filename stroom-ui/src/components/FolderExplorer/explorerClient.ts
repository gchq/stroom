import { Dispatch, Action } from "redux";

import { actionCreators as folderExplorerActionCreators } from "./redux";
import { actionCreators as docRefTypesActionCreators } from "../DocRefTypes/redux";
import { actionCreators as docRefInfoActionCreators } from "../DocRefInfoModal";
import { actionCreators as appSearchActionCreators } from "../AppSearchBar/redux";
import {
  wrappedGet,
  wrappedPut,
  wrappedPost
} from "../../lib/fetchTracker.redux";
import { findByUuids, findItem } from "../../lib/treeUtils";
import { DocRefType, DocRefTree, DocRefInfoType } from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const {
  docTreeReceived,
  docRefRenamed,
  docRefsCopied,
  docRefsMoved,
  docRefsDeleted,
  docRefCreated
} = folderExplorerActionCreators;

const { searchResultsReturned } = appSearchActionCreators;

const { docRefInfoOpened, docRefInfoReceived } = docRefInfoActionCreators;

const { docRefTypesReceived } = docRefTypesActionCreators;

const stripDocRef = (docRef: DocRefType) => ({
  uuid: docRef.uuid,
  type: docRef.type,
  name: docRef.name
});

export const searchApp = (
  pickerId: string,
  { term = "", docRefType = "", pageOffset = 0, pageSize = 10 }
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();
  const params = `searchTerm=${term}&docRefType=${docRefType}&pageOffset=${pageOffset}&pageSize=${pageSize}`;
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/explorer/v1/search?${params}`;
  wrappedGet(
    dispatch,
    state,
    url,
    r =>
      r
        .json()
        .then((searchResults: Array<DocRefType>) =>
          dispatch(searchResultsReturned(pickerId, searchResults))
        ),
    {},
    true
  );
};

export const fetchDocTree = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/explorer/v1/all`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then((documentTree: DocRefTree) =>
        dispatch(docTreeReceived(documentTree))
      )
  );
};

export const fetchDocRefTypes = () => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${
    state.config.values.stroomBaseServiceUrl
  }/explorer/v1/docRefTypes`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then((docRefTypes: Array<string>) =>
        dispatch(docRefTypesReceived(docRefTypes))
      )
  );
};

export const fetchDocInfo = (docRef: DocRefType) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  dispatch(docRefInfoOpened(docRef));
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/explorer/v1/info/${
    docRef.type
  }/${docRef.uuid}`;
  wrappedGet(dispatch, state, url, response =>
    response
      .json()
      .then((docRefInfo: DocRefInfoType) =>
        dispatch(docRefInfoReceived(docRefInfo))
      )
  );
};

export const createDocument = (
  docRefType: string,
  docRefName: string,
  destinationFolderRef: DocRefType,
  permissionInheritance: string
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/explorer/v1/create`;
  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then((updatedTree: DocRefTree) =>
          dispatch(docRefCreated(updatedTree))
        ),
    {
      body: JSON.stringify({
        docRefType,
        docRefName,
        destinationFolderRef: stripDocRef(destinationFolderRef),
        permissionInheritance
      })
    }
  );
};

export const renameDocument = (docRef: DocRefType, name: string) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/explorer/v1/rename`;

  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then((resultDocRef: DocRefType) =>
          dispatch(docRefRenamed(docRef, name, resultDocRef))
        ),
    {
      body: JSON.stringify({
        docRef: stripDocRef(docRef),
        name
      })
    }
  );
};

export const copyDocuments = (
  uuids: Array<string>,
  destinationUuid: string,
  permissionInheritance: string
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();
  const {
    config: {
      values: { stroomBaseServiceUrl }
    },
    folderExplorer: { documentTree }
  } = state;
  const url = `${stroomBaseServiceUrl}/explorer/v1/copy`;
  const docRefs = findByUuids(documentTree, uuids);
  const destination = findItem(documentTree, destinationUuid)!;

  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then((updatedTree: DocRefTree) =>
          dispatch(docRefsCopied(docRefs, destination.node, updatedTree))
        ),
    {
      body: JSON.stringify({
        docRefs: docRefs.map(stripDocRef),
        destinationFolderRef: stripDocRef(destination.node),
        permissionInheritance
      })
    }
  );
};

export const moveDocuments = (
  uuids: Array<string>,
  destinationUuid: string,
  permissionInheritance: string
) => (dispatch: Dispatch, getState: () => GlobalStoreState) => {
  const state = getState();
  const {
    config: {
      values: { stroomBaseServiceUrl }
    },
    folderExplorer: { documentTree }
  } = state;

  const url = `${stroomBaseServiceUrl}/explorer/v1/move`;
  const docRefs = findByUuids(documentTree, uuids);
  const destination = findItem(documentTree, destinationUuid)!;
  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then((updatedTree: DocRefTree) =>
          dispatch(docRefsMoved(docRefs, destination.node, updatedTree))
        ),
    {
      body: JSON.stringify({
        docRefs: docRefs.map(stripDocRef),
        destinationFolderRef: stripDocRef(destination.node),
        permissionInheritance
      })
    }
  );
};

export const deleteDocuments = (uuids: Array<string>) => (
  dispatch: Dispatch,
  getState: () => GlobalStoreState
) => {
  const state = getState();
  const url = `${state.config.values.stroomBaseServiceUrl}/explorer/v1/delete`;
  const docRefs = findByUuids(state.folderExplorer.documentTree, uuids);
  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then((updatedTree: DocRefTree) =>
          dispatch(docRefsDeleted(docRefs, updatedTree))
        ),
    {
      method: "delete",
      body: JSON.stringify(docRefs.map(stripDocRef))
    }
  );
};

const explorerClient = {
  createDocument,
  copyDocuments,
  renameDocument,
  moveDocuments,
  deleteDocuments,
  fetchDocTree,
  fetchDocInfo,
  fetchDocRefTypes
};

export default explorerClient;
