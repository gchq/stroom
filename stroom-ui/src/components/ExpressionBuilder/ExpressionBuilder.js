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

import { compose, withState, branch, renderComponent, withProps, lifecycle } from 'recompose';
import { connect } from 'react-redux';
import { Loader, Checkbox } from 'semantic-ui-react';

import ExpressionOperator from './ExpressionOperator';
import ROExpressionOperator from './ROExpressionOperator';
import { LineContainer } from 'components/LineTo';
import DeleteExpressionItem from './DeleteExpressionItem';

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
    ({ expressionBuilder }, { expressionId }) => ({
      expressionState: expressionBuilder[expressionId],
    }),
    {
      // actions
    },
  ),
  withSetEditableByUser,
  lifecycle({
    componentDidMount() {
      this.props.setEditableByUser(this.props.editMode);
    },
  }),
  branch(
    ({ expressionState }) => !expressionState,
    renderComponent(() => <Loader active>Loading Expression State</Loader>),
  ),
  withProps(({ showModeToggle, dataSource }) => ({
    showModeToggle: showModeToggle && !!dataSource,
  })),
);

const ExpressionBuilder = ({
  expressionId,
  dataSource,
  expressionState: { expression },
  showModeToggle,
  inEditMode,
  setEditableByUser,
}) => (
  <LineContainer
    className="Expression-editor__graph"
    lineContextId={`expression-lines-${expressionId}`}
    lineElementCreators={lineElementCreators}
  >
    <DeleteExpressionItem expressionId={expressionId} />
    {showModeToggle ? (
      <Checkbox
        label="Edit Mode"
        toggle
        checked={inEditMode}
        onChange={() => setEditableByUser(!inEditMode)}
      />
    ) : (
      undefined
    )}
    {inEditMode ? (
      <ExpressionOperator
        dataSource={dataSource}
        expressionId={expressionId}
        isRoot
        isEnabled
        operator={expression}
      />
    ) : (
      <ROExpressionOperator expressionId={expressionId} isEnabled operator={expression} />
    )}
  </LineContainer>
);

const EnhancedExpressionBuilder = enhance(ExpressionBuilder);

EnhancedExpressionBuilder.propTypes = {
  dataSource: PropTypes.object,
  expressionId: PropTypes.string.isRequired,
  showModeToggle: PropTypes.bool.isRequired,
  editMode: PropTypes.bool,
};

EnhancedExpressionBuilder.defaultProps = {
  showModeToggle: false,
};

export default EnhancedExpressionBuilder;
