import { actionCreators } from './redux';
import { wrappedGet, wrappedPut, wrappedPost } from 'lib/fetchTracker.redux';
import { findByUuids, findItem } from 'lib/treeUtils';

const {
  docTreeReceived,
  docRefRenamed,
  docRefsCopied,
  docRefsMoved,
  docRefsDeleted,
  docRefTypesReceived,
  docRefInfoOpened,
  docRefInfoReceived,
  docRefCreated,
} = actionCreators;

const stripDocRef = docRef => ({
  uuid: docRef.uuid,
  type: docRef.type,
  name: docRef.name,
});

export const fetchDocTree = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/all`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(documentTree => dispatch(docTreeReceived(documentTree))));
};

export const fetchDocRefTypes = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/docRefTypes`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(docRefTypes => dispatch(docRefTypesReceived(docRefTypes))));
};

export const fetchDocInfo = docRef => (dispatch, getState) => {
  dispatch(docRefInfoOpened(docRef));
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/info/${docRef.type}/${docRef.uuid}`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(docRefInfo => dispatch(docRefInfoReceived(docRefInfo))));
};

export const createDocument = (
  docRefType,
  docRefName,
  destinationFolderRef,
  permissionInheritance,
) => (dispatch, getState) => {
  console.log('Creating New Doc Ref', {
    docRefType,
    docRefName,
    destinationFolderRef,
    permissionInheritance,
  });
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/create`;
  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(resultDocRef => dispatch(docRefCreated(resultDocRef, destinationFolderRef))),
    {
      body: JSON.stringify({
        docRefType,
        docRefName,
        destinationFolderRef,
        permissionInheritance,
      }),
    },
  );
};

export const renameDocument = (docRef, name) => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/rename`;

  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response.json().then(resultDocRef => dispatch(docRefRenamed(docRef, name, resultDocRef))),
    {
      body: JSON.stringify({
        docRef: stripDocRef(docRef),
        name,
      }),
    },
  );
};

export const copyDocuments = (uuids, destinationUuid, permissionInheritance) => (
  dispatch,
  getState,
) => {
  const state = getState();
  const {
    config: { explorerServiceUrl },
    docExplorer: {
      explorerTree: { documentTree },
    },
  } = state;
  const url = `${explorerServiceUrl}/copy`;
  const docRefs = findByUuids(documentTree, uuids);
  const destination = findItem(documentTree, destinationUuid);

  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(bulkActionResult =>
          dispatch(docRefsCopied(docRefs, destination.node, bulkActionResult))),
    {
      body: JSON.stringify({
        docRefs: docRefs.map(stripDocRef),
        destinationFolderRef: stripDocRef(destination.node),
        permissionInheritance,
      }),
    },
  );
};

export const moveDocuments = (uuids, destinationUuid, permissionInheritance) => (
  dispatch,
  getState,
) => {
  const state = getState();
  const {
    config: { explorerServiceUrl },
    docExplorer: {
      explorerTree: { documentTree },
    },
  } = state;

  const url = `${explorerServiceUrl}/move`;
  const docRefs = findByUuids(documentTree, uuids);
  const destination = findItem(documentTree, destinationUuid);
  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(bulkActionResult =>
          dispatch(docRefsMoved(docRefs, destination.node, bulkActionResult))),
    {
      body: JSON.stringify({
        docRefs: docRefs.map(stripDocRef),
        destinationFolderRef: stripDocRef(destination.node),
        permissionInheritance,
      }),
    },
  );
};

export const deleteDocuments = uuids => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/delete`;
  const docRefs = findByUuids(state.docExplorer.explorerTree.documentTree, uuids);
  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response.json().then(bulkActionResult => dispatch(docRefsDeleted(docRefs, bulkActionResult))),
    {
      method: 'delete',
      body: JSON.stringify(docRefs.map(stripDocRef)),
    },
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
  fetchDocRefTypes,
};

export default explorerClient;
