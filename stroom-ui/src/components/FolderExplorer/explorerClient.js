import { actionCreators as folderExplorerActionCreators } from './redux';
import { actionCreators as docRefTypesActionCreators } from 'components/DocRefTypes/redux';
import { actionCreators as docRefInfoActionCreators } from 'components/DocRefInfoModal';
import { actionCreators as appSearchActionCreators } from 'components/AppSearchBar/redux';
import { wrappedGet, wrappedPut, wrappedPost } from 'lib/fetchTracker.redux';
import { findByUuids, findItem } from 'lib/treeUtils';

const {
  docTreeReceived,
  docRefRenamed,
  docRefsCopied,
  docRefsMoved,
  docRefsDeleted,
  docRefCreated,
} = folderExplorerActionCreators;

const { searchResultsReturned } = appSearchActionCreators;

const { docRefInfoOpened, docRefInfoReceived } = docRefInfoActionCreators;

const { docRefTypesReceived } = docRefTypesActionCreators;

const stripDocRef = docRef => ({
  uuid: docRef.uuid,
  type: docRef.type,
  name: docRef.name,
});

export const searchApp = (pickerId, {term='', docRefType='', pageOffset=0, pageSize=10}) => (dispatch, getState) => {
  const state = getState();
  const params = `searchTerm=${term}&docRefType=${docRefType}&pageOffset=${pageOffset}&pageSize=${pageSize}`;
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/search?${params}`;
  wrappedGet(
    dispatch, 
    state, 
    url,
    r => r.json().then(searchResults => dispatch(searchResultsReturned(pickerId, searchResults))),
    {},
    true);
}

export const fetchDocTree = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/all`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(documentTree => dispatch(docTreeReceived(documentTree))));
};

export const fetchDocRefTypes = () => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/docRefTypes`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(docRefTypes => dispatch(docRefTypesReceived(docRefTypes))));
};

export const fetchDocInfo = docRef => (dispatch, getState) => {
  dispatch(docRefInfoOpened(docRef));
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/info/${docRef.type}/${docRef.uuid}`;
  wrappedGet(dispatch, state, url, response =>
    response.json().then(docRefInfo => dispatch(docRefInfoReceived(docRefInfo))));
};

export const createDocument = (
  docRefType,
  docRefName,
  destinationFolderRef,
  permissionInheritance,
) => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/create`;
  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(updatedTree => dispatch(docRefCreated(updatedTree))),
    {
      body: JSON.stringify({
        docRefType,
        docRefName,
        destinationFolderRef: stripDocRef(destinationFolderRef),
        permissionInheritance,
      }),
    },
  );
};

export const renameDocument = (docRef, name) => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/rename`;

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
    config: { stroomBaseServiceUrl },
    folderExplorer: { documentTree },
  } = state;
  const url = `${stroomBaseServiceUrl}/explorer/v1/copy`;
  const docRefs = findByUuids(documentTree, uuids);
  const destination = findItem(documentTree, destinationUuid);

  wrappedPost(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(updatedTree => dispatch(docRefsCopied(docRefs, destination.node, updatedTree))),
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
    config: { stroomBaseServiceUrl },
    folderExplorer: { documentTree },
  } = state;

  const url = `${stroomBaseServiceUrl}/explorer/v1/move`;
  const docRefs = findByUuids(documentTree, uuids);
  const destination = findItem(documentTree, destinationUuid);
  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response
        .json()
        .then(updatedTree => dispatch(docRefsMoved(docRefs, destination.node, updatedTree))),
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
  const url = `${state.config.stroomBaseServiceUrl}/explorer/v1/delete`;
  const docRefs = findByUuids(state.folderExplorer.documentTree, uuids);
  wrappedPost(
    dispatch,
    state,
    url,
    response => response.json().then(updatedTree => dispatch(docRefsDeleted(docRefs, updatedTree))),
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
