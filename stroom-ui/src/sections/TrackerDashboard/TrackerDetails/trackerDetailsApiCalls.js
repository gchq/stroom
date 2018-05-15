import { push } from 'react-router-redux';
import { HttpError } from '../../../ErrorTypes';

import { actionCreators } from '../trackerDashboardData';

export const enableToggle = (filterId: string, isCurrentlyEnabled: boolean): ThunkAction => (
  dispatch,
  getState,
) => {
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

function handleStatus(response) {
  if (response.status === 200) {
    return Promise.resolve(response);
  }
  return Promise.reject(new HttpError(response.status, response.statusText));
}
