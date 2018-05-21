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

import React, { Component } from 'react';
import { bindActionCreators } from 'redux';
import { connect } from 'react-redux';
import qs from 'qs';
import PropTypes, { object } from 'prop-types';

import './ErrorPage.css';

class ErrorPage extends Component {
  render() {
    const { errorMessage, stackTrace, httpErrorCode } = this.props;
    return (
      <div className="content-floating-without-appbar">
        <div className="ErrorPage-card">
          <h3>There has been an error!</h3>
          {errorMessage ? (
            <div>
              <p>
                <strong>Error message: </strong> <code>{errorMessage}</code>
              </p>
            </div>
          ) : (
            undefined
          )}

          {httpErrorCode ? (
            <div>
              <p>
                <strong>HTTP error code: </strong> <code>{httpErrorCode}</code>
              </p>
            </div>
          ) : (
            undefined
          )}

          {stackTrace ? (
            <span>
              <p>
                <strong>Stack trace: </strong>
              </p>
              <code>{stackTrace}</code>
            </span>
          ) : (
            undefined
          )}
        </div>
      </div>
    );
  }
}

const mapStateToProps = state => ({
  errorMessage: state.errorPage.errorMessage,
  stackTrace: state.errorPage.stackTrace,
  httpErrorCode: state.errorPage.httpErrorCode,
});

const mapDispatchToProps = dispatch => bindActionCreators({}, dispatch);

export default connect(mapStateToProps, mapDispatchToProps)(ErrorPage);
