import { push } from 'react-router-redux';

import { HttpError } from '../../lib/ErrorTypes';

import { actionCreators } from './redux';

const fetch = window.fetch;

export const TrackerSelection = Object.freeze({ first: 'first', last: 'last', none: 'none' });

export const fetchTrackers = trackerSelection => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;

  const rowsToFetch = getRowsPerPage(state.trackerDashboard.selectedTrackerId !== undefined);
  dispatch(actionCreators.updatePageSize(rowsToFetch));

  let url = `${state.config.streamTaskServiceUrl}/?`;
  url += `pageSize=${rowsToFetch}`;
  url += `&offset=${state.trackerDashboard.pageOffset}`;
  if (state.trackerDashboard.sortBy !== undefined) {
    url += `&sortBy=${state.trackerDashboard.sortBy}`;
    url += `&sortDirection=${state.trackerDashboard.sortDirection}`;
  }

  if (
    state.trackerDashboard.searchCriteria !== '' &&
    state.trackerDashboard.searchCriteria !== undefined
  ) {
    url += `&filter=${state.trackerDashboard.searchCriteria}`;
  }

  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method: 'get',
    mode: 'cors',
  })
    .then(handleStatus)
    .then(response => response.json())
    .then((trackers) => {
      dispatch(actionCreators.updateTrackers(trackers.streamTasks, trackers.totalStreamTasks));
      switch (trackerSelection) {
        case TrackerSelection.first:
          dispatch(actionCreators.selectFirst());
          break;
        case TrackerSelection.last:
          dispatch(actionCreators.selectLast());
          break;
        case TrackerSelection.none:
          dispatch(actionCreators.selectNone());
          break;
        default:
          break;
      }
    })
    .catch((error) => {
      // TODO: handle a bad response from the service, i.e. send the use to an error
      dispatch(push('/error'));
    });
};

export const enableToggle = (filterId, isCurrentlyEnabled) => (dispatch, getState) => {
  const state = getState();
  const jwsToken = state.authentication.idToken;
  const url = `${state.config.streamTaskServiceUrl}/${filterId}`;

  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwsToken}`,
    },
    method: 'PATCH',
    mode: 'cors',
    body: JSON.stringify({ op: 'replace', path: 'enabled', value: !isCurrentlyEnabled }),
  })
    .then(handleStatus)
    .then((response) => {
      dispatch(actionCreators.updateEnabled(filterId, !isCurrentlyEnabled));
    })
    .catch((error) => {
      dispatch(push('/error'));
    });
};

const getRowsPerPage = (isDetailsVisible) => {
  const viewport = document.getElementById('table-container');
  let rowsInViewport = 20; // Fallback default
  const headerHeight = 46;
  const footerHeight = 36;
  // const detailsHeight = 295;
  const rowHeight = 30;
  if (viewport) {
    const viewportHeight = viewport.offsetHeight;
    const heightAvailableForRows = viewportHeight - headerHeight - footerHeight;
    // if (isDetailsVisible) {
    // heightAvailableForRows -= detailsHeight;
    // }
    rowsInViewport = Math.floor(heightAvailableForRows / rowHeight);
  }

  // Make sure we always request at least 1 row, even if the viewport is too small
  // to display it without scrolling. Anything less will be rejected by the
  // service for being rediculous.
  if (rowsInViewport <= 0) {
    return 1;
  }
  return rowsInViewport;
};

function handleStatus(response) {
  if (response.status === 200) {
    return Promise.resolve(response);
  }
  return Promise.reject(new HttpError(response.status, response.statusText));
}
