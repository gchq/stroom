/*
 * Copyright 2017 Crown Copyright
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

import jwtDecode from 'jwt-decode';
import uuidv4 from 'uuid';
import sjcl from 'sjcl';
import { push } from 'react-router-redux';

// import { hasAppPermission } from './authorisation';

export const TOKEN_ID_CHANGE = 'authentication/TOKEN_ID_CHANGE';

const initialState = {
  idToken: '',
};

export const authenticationReducer = (state = initialState, action) => {
  switch (action.type) {
    case TOKEN_ID_CHANGE:
      return Object.assign({}, state, {
        idToken: action.idToken,
      });

    default:
      return state;
  }
};

function changeIdToken(idToken) {
  return {
    type: TOKEN_ID_CHANGE,
    idToken,
  };
}

export const sendAuthenticationRequest = (
  referrer,
  uiUrl,
  appClientId,
  authenticationServiceUrl,
  appPermission,
) => (dispatch, getState) => {
  const redirectUrl = `${uiUrl}/handleAuthenticationResponse`;
  const state = '';

  // Create nonce and store, and create nonce hash
  const nonce = uuidv4();
  const nonceHashBytes = sjcl.hash.sha256.hash(nonce);
  const nonceHash = sjcl.codec.hex.fromBits(nonceHashBytes);
  localStorage.setItem('nonce', nonce);

  // We need to remember where the user was going
  localStorage.setItem('preAuthenticationRequestReferrer', referrer);
  localStorage.setItem('appPermission', appPermission);

  // Compose the new URL
  const authenticationRequestParams = `?scope=openid&response_type=code&client_id=${appClientId}&redirect_url=${redirectUrl}&state=${state}&nonce=${nonceHash}`;
  const authenticationRequestUrl = `${authenticationServiceUrl}/authenticate/${authenticationRequestParams}`;

  // We hand off to the authenticationService.
  window.location.href = authenticationRequestUrl;
};

export const handleAuthenticationResponse = (
  accessCode,
  authenticationServiceUrl,
  authorisationServiceUrl,
) => (dispatch) => {
  const idTokenRequestUrl = `${authenticationServiceUrl}/idToken?accessCode=${accessCode}`;

  // The cookie including the sessionId will be sent along with this request.
  // The 'credentials' key makes this happen.
  fetch(idTokenRequestUrl, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    method: 'get',
    credentials: 'include',
    mode: 'cors',
  })
    .then(response => response.text())
    .then((idToken) => {
      const decodedToken = jwtDecode(idToken);
      const nonce = localStorage.getItem('nonce');
      const nonceHashBytes = sjcl.hash.sha256.hash(nonce);
      const nonceHash = sjcl.codec.hex.fromBits(nonceHashBytes);
      const returnedNonce = decodedToken.nonce;
      const referrer = localStorage.getItem('preAuthenticationRequestReferrer');
      // const appPermission = localStorage.getItem('appPermission');

      if (nonceHash === returnedNonce) {
        localStorage.removeItem('nonce');
        localStorage.removeItem('preAuthenticationRequestReferrer');
        localStorage.removeItem('appPermission');

        // TODO: if the user has the requested appPermission then we change the ID token.
        dispatch(changeIdToken(idToken));
        // dispatch(hasAppPermission(idToken, authorisationServiceUrl, appPermission));
      } else {
        console.error('Nonce does not match.');
        // We fall through and push to the referrer, which will mean we attempt log in again.
        // Possibly we could add an error message here, so the user can understand why they
        // are being asked to log in again.
      }
      dispatch(push(referrer));
    });
};
