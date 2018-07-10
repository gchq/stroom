import { actionCreators } from './redux';
import { wrappedGet, wrappedPut, wrappedPost } from 'lib/fetchTracker.redux';

const {
  docTreeReceived,
  docRefRenamed,
  docRefsDeleted,
  docRefTypesReceived,
  docRefInfoOpened,
  docRefInfoReceived,
  completeDocRefCopy,
  completeDocRefDelete,
  completeDocRefRename,
  completeDocRefMove,
} = actionCreators;

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

export const renameDocument = (docRef, name) => (dispatch, getState) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/rename`;
  wrappedPut(
    dispatch,
    state,
    url,
    response =>
      response.text().then(() => {
        dispatch(completeDocRefRename());
        dispatch(docRefRenamed(docRef, name));
      }),
    {
      body: JSON.stringify({
        docRef: {
          uuid: docRef.uuid,
          type: docRef.type,
          name: docRef.name,
        },
        name,
      }),
    },
  );
};

export const copyDocument = (docRefs, destinationFolderRef, permissionInheritance) => (
  dispatch,
  getState,
) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/copy`;
  wrappedPost(
    dispatch,
    state,
    url,
    response => response.json().then(docRefInfo => dispatch(completeDocRefCopy(docRefInfo))),
    {
      body: JSON.stringify({
        docRefs,
        destinationFolderRef,
        permissionInheritance,
      }),
    },
  );
};

export const moveDocument = (docRefs, destinationFolderRef, permissionInheritance) => (
  dispatch,
  getState,
) => {
  const state = getState();
  const url = `${state.config.explorerServiceUrl}/move`;
  wrappedPost(
    dispatch,
    state,
    url,
    response => response.json().then(docRefInfo => dispatch(completeDocRefMove(docRefInfo))),
    {
      body: JSON.stringify({
        docRefs,
        destinationFolderRef,
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
      response.text().then(() => {
        dispatch(completeDocRefDelete());
        dispatch(docRefsDeleted(docRefs));
      }),
    {
      method: 'delete',
      body: JSON.stringify(docRefs.map(d => ({
        uuid: d.uuid,
        type: d.type,
        name: d.name,
      }))),
    },
  );
};
