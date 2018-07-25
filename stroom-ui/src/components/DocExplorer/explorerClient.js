import { actionCreators } from './redux';
import { wrappedGet, wrappedPut, wrappedPost } from 'lib/fetchTracker.redux';

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

export const createDocument = (docRefType, docRefName, destinationFolderRef, permissionInheritance) => (
  dispatch,
  getState,
) => {
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

export const copyDocuments = (docRefs, destinationFolderRef, permissionInheritance) => (
  dispatch,
  getState,
) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/copy`;
  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(bulkActionResult =>
          dispatch(docRefsCopied(docRefs, destinationFolderRef, bulkActionResult))),
    {
      body: JSON.stringify({
        docRefs: docRefs.map(stripDocRef),
        destinationFolderRef: stripDocRef(destinationFolderRef),
        permissionInheritance,
      }),
    },
  );
};

export const moveDocuments = (docRefs, destinationFolderRef, permissionInheritance) => (
  dispatch,
  getState,
) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/move`;
  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(bulkActionResult =>
          dispatch(docRefsMoved(docRefs, destinationFolderRef, bulkActionResult))),
    {
      body: JSON.stringify({
        docRefs: docRefs.map(stripDocRef),
        destinationFolderRef: stripDocRef(destinationFolderRef),
        permissionInheritance,
      }),
    },
  );
};

export const deleteDocuments = docRefs => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/delete`;
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
