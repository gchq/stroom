/*
 * Copyright 2020 Crown Copyright
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
// import * as jwtDecode from "jwt-decode";
// import * as uuidv4 from "uuid";
// import * as sjcl from "sjcl";
import {History, Location} from "history";
// import * as queryString from "query-string";

// export const sendAuthenticationRequest = (
//     referrer: string,
//     uiUrl: string,
//     loginUrl: string,
// ) => {
//     // We need to remember where the user was going
//     // localStorage.setItem("preAuthenticationRequestReferrer", window.location.href);
//     localStorage.setItem("preAuthenticationRequestReferrer", referrer);
//
//     // Perform a login
//     const redirectUri = `${uiUrl}/s/handleAuthenticationResponse`;
//     const authenticationRequestParams = `/login?redirectUri=${redirectUri}`;
//     const loginRequestUrl = `${loginUrl}${authenticationRequestParams}`;
//     window.location.href = loginRequestUrl;
//
//
//
//
//
//
//
//     // const redirectUri = `${uiUrl}/s/handleAuthenticationResponse`;
//     //
//     // getOpenIdConfiguration(openIdConfigUrl, openIdConfiguration => {
//     //     console.log(openIdConfiguration.issuer);
//     //     console.log(openIdConfiguration.authorization_endpoint);
//     //
//     //     const authorizationEndpoint = openIdConfiguration.authorization_endpoint;
//     //
//     //     // Create state and store
//     //     const state = createUniqueString();
//     //     localStorage.setItem("state", state);
//     //
//     //     // Create nonce and store
//     //     const nonce = createUniqueString();
//     //     localStorage.setItem("nonce", nonce);
//     //
//     //     // We need to remember where the user was going
//     //     // localStorage.setItem("preAuthenticationRequestReferrer", window.location.href);
//     //     localStorage.setItem("preAuthenticationRequestReferrer", referrer);
//     //
//     //     // Compose the new URL
//     //     const authenticationRequestParams = `?scope=openid&response_type=id_token&client_id=${clientId}&redirect_uri=${redirectUri}&state=${state}&nonce=${nonce}`;
//     //     const authenticationRequestUrl = `${authorizationEndpoint}${authenticationRequestParams}`;
//     //
//     //     // We hand off to the authenticationService.
//     //     console.log(authenticationRequestUrl);
//     //     window.location.href = authenticationRequestUrl;
//     // });
// };

export const handleAuthenticationResponse = (
    location: Location,
    openIdConfigUrl: string,
    clientId: string,
    tokenIdChange: (idToken: string) => void,
    history: History,
) => {
    redirectToReferrer(history);

    // // Get the openid configuration.
    // getOpenIdConfiguration(openIdConfigUrl, openIdConfiguration => {
    //     let query;
    //     if (location.search) {
    //         query = queryString.parse(location.search);
    //     } else {
    //         query = queryString.parse(location.hash);
    //     }
    //
    //     const state = query.state;
    //     // const access_token = query.access_token as string;
    //     // const token_type = query.token_type;
    //     // const expires_in = query.expires_in;
    //     // const scope = query.scope;
    //     const id_token = query.id_token as string;
    //     // const authuser = query.authuser;
    //     // const session_state = query.session_state;
    //     // const prompt = query.prompt;
    //
    //     const localState = localStorage.getItem("state");
    //     const localNonce = localStorage.getItem("nonce");
    //     localStorage.removeItem("nonce");
    //     localStorage.removeItem("state");
    //
    //     if (localState !== state) {
    //         console.error("State does not match");
    //
    //     } else {
    //         const decodedIdToken = jwtDecode<{ iss: string, nonce: string }>(id_token);
    //         if (localNonce !== decodedIdToken.nonce) {
    //             console.error("Nonce does not match.");
    //         } else if (openIdConfiguration.issuer !== decodedIdToken.iss) {
    //             console.error("Unexpected issuer");
    //         } else {
    //             tokenIdChange(id_token);
    //         }
    //     }
    //     redirectToReferrer(history);
    // });


};

// const createUniqueString = () => {
//     const uuid = uuidv4();
//     const uuidHashBytes = sjcl.hash.sha256.hash(uuid);
//     return sjcl.codec.hex.fromBits(uuidHashBytes);
// };

const redirectToReferrer = (
    history: History
) => {
    const referrer = localStorage.getItem("preAuthenticationRequestReferrer") as string;
    if (referrer) {
        localStorage.removeItem("preAuthenticationRequestReferrer");
        history.push(referrer);
    } else {
        console.error("Unable to redirect to referrer as it is undefined.");
    }
};

// const getOpenIdConfiguration = (
//     url: string,
//     onLoad: (json: { issuer: string, authorization_endpoint: string }) => void
// ) => {
//     const openIdConfiguration = localStorage.getItem("openid-configuration");
//     if (openIdConfiguration) {
//         const body = JSON.parse(openIdConfiguration);
//         onLoad(body);
//     } else {
//         fetch(url)
//             .then(response => response.text())
//             .then(text => {
//                 // Store the openid configuration.
//                 localStorage.setItem("openid-configuration", text);
//                 const body = JSON.parse(openIdConfiguration);
//                 onLoad(body);
//             });
//     }
// };
