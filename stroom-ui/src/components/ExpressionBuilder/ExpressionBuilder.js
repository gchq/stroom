/*
 * Copyright 2018 Crown Copyright
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
import React from 'react';
import PropTypes from 'prop-types';

import { compose, withState, branch, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Loader } from 'semantic-ui-react';

import ExpressionOperator from './ExpressionOperator';
import ROExpressionOperator from './ROExpressionOperator';
import { LineContainer } from 'components/LineTo';

import { Checkbox } from 'semantic-ui-react';

import lineElementCreators from './expressionLineCreators';

const withSetEditableByUser = withState('inEditMode', 'setEditableByUser', false);

const ROExpressionBuilder = ({ expressionId, expression }) => (
  <LineContainer
    lineContextId={`expression-lines-${expressionId}`}
    lineElementCreators={lineElementCreators}
  >
    <ROExpressionOperator expressionId={expressionId} isEnabled operator={expression} />
  </LineContainer>
);

ROExpressionBuilder.propTypes = {
  expressionId: PropTypes.string.isRequired,
  expression: PropTypes.object.isRequired,
};

const enhance = compose(
  connect(
    (state, props) => ({
      dataSource: state.dataSources[props.dataSourceUuid],
      expression: state.expressions[props.expressionId],
    }),
    {
      // actions
    },
  ),
  withSetEditableByUser,
  branch(
    ({ expression }) => !expression,
    renderComponent(() => <Loader active>Loading Expression</Loader>),
  ),
  withProps(({ allowEdit, dataSource }) => ({
    allowEdit: allowEdit && !!dataSource,
  })),
  branch(({ allowEdit }) => !allowEdit, renderComponent(ROExpressionBuilder)),
);

const ExpressionBuilder = ({
  expressionId,
  dataSource,
  expression,
  inEditMode,
  setEditableByUser,
}) => {
  const roOperator = (
    <ROExpressionOperator expressionId={expressionId} isEnabled operator={expression} />
  );

  const editOperator = (
    <ExpressionOperator
      dataSource={dataSource}
      expressionId={expressionId}
      isRoot
      isEnabled
      operator={expression}
    />
  );

  return (
    <LineContainer
      lineContextId={`expression-lines-${expressionId}`}
      lineElementCreators={lineElementCreators}
    >
      <Checkbox
        label="Edit Mode"
        toggle
        checked={inEditMode}
        onChange={() => setEditableByUser(!inEditMode)}
      />
      {inEditMode ? editOperator : roOperator}
    </LineContainer>
  );
};

ExpressionBuilder.propTypes = {
  dataSourceUuid: PropTypes.string, // if not set, the expression will be read only
  expressionId: PropTypes.string.isRequired,
  allowEdit: PropTypes.bool.isRequired,
};

ExpressionBuilder.defaultProps = {
  allowEdit: false,
};

export default enhance(ExpressionBuilder);
