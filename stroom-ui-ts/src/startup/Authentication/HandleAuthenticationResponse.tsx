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

import { Component } from "react";

import * as queryString from "qs";

import { handleAuthenticationResponse } from "./authentication";

export interface Props {
  authenticationServiceUrl: string;
  authorisationServiceUrl: string;
}

class HandleAuthenticationResponse extends Component<Props, {}> {
  componentDidMount() {
    const accessCode = queryString.parse(
      this.context.router.route.location.search
    ).accessCode;
    this.context.store.dispatch(
      handleAuthenticationResponse(
        accessCode,
        this.props.authenticationServiceUrl,
        this.props.authorisationServiceUrl
      )
    );
  }

  render() {
    return null;
  }
}

// HandleAuthenticationResponse.contextTypes = {
//   store: PropTypes.object.isRequired,
//   router: PropTypes.shape({
//     history: object.isRequired
//   })
// };

export default HandleAuthenticationResponse;
