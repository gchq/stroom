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
import { func } from "prop-types";

import { sendAuthenticationRequest } from "./authentication";

export interface Props {
  referrer: string;
  uiUrl: string;
  appClientId: string;
  authenticationServiceUrl: string;
  appPermission?: string;
}

class AuthenticationRequest extends Component<Props, object> {
  static contextTypes = {
    store: func.isRequired
  };

  componentDidMount() {
    this.context.store.dispatch(
      sendAuthenticationRequest(
        this.props.referrer,
        this.props.uiUrl,
        this.props.appClientId,
        this.props.authenticationServiceUrl,
        this.props.appPermission
      )
    );
  }

  render() {
    return null;
  }
}

export default AuthenticationRequest;
