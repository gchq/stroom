import React from 'react';
import PropTypes from 'prop-types';

import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react';
import { compose, lifecycle, withState, branch, renderComponent } from 'recompose';

const runTest = (url, handleResponse) =>
  fetch(url, {
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    method: 'get',
    mode: 'cors',
  })
    .then((response) => {
      if (response.status === 200) {
        return Promise.resolve(response);
      }
      handleResponse({
        status: response.state,
        error: response.statusText,
      });
      return Promise.reject(new HttpError(response.status, response.statusText));
    })
    .then(response => response.json())
    .then((json) => {
      handleResponse(json);
    });

const withResponse = withState('json', 'setJson', undefined);

const enhance = compose(
  withResponse,
  connect(
    (state, props) => ({
      config: state.config,
    }),
    {},
  ),
  branch(
    ({ config }) => !config.isReady,
    renderComponent(() => <Loader active>Awaiting Config</Loader>),
  ),
  lifecycle({
    componentDidMount() {
      const url = `${this.props.config.explorerServiceUrl}/all`;
      runTest(url, this.props.setJson);
    },
  }),
  branch(({ json }) => !json, renderComponent(() => <Loader active>Awaiting JSON</Loader>)),
);

const PollyRedux = enhance(({ config, json, setJson }) => (
  <div>
    Polly Redux Test - {JSON.stringify(json)}
    <br />
    <button onClick={() => runTest(config.explorerServiceUrl, setJson)}>Test</button>
  </div>
));

export default PollyRedux;
