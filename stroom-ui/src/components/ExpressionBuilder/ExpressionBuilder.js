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

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import ExpressionOperator from './ExpressionOperator';
import ROExpressionOperator from './ROExpressionOperator';
import { LineContainer } from 'components/LineTo';

import { withDataSource } from './DataSource';
import { withExpression } from './withExpression';

import { Checkbox } from 'semantic-ui-react';

import { actionCreators } from './redux';

import lineElementCreators from './expressionLineCreators';

const withSetEditableByUser = withState('editableByUser', 'setEditableByUser', false);

const ExpressionBuilder = ({
  expressionId,
  dataSource,
  expression,
  isEditableSystemSet,

  // withSetEditableByUser
  editableByUser,
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

  let theComponent;
  if (isEditableSystemSet) {
    theComponent = (
      <div>
        <Checkbox
          label="Edit Mode"
          toggle
          checked={editableByUser}
          onChange={() => setEditableByUser(!editableByUser)}
        />
        {editableByUser ? editOperator : roOperator}
      </div>
    );
  } else {
    theComponent = roOperator;
  }

  return (
    <LineContainer
      lineContextId={`expression-lines-${expressionId}`}
      lineElementCreators={lineElementCreators}
    >
      {theComponent}
    </LineContainer>
  );
};

ExpressionBuilder.propTypes = {
  // Set by container
  expressionId: PropTypes.string.isRequired,
  isEditableSystemSet: PropTypes.bool.isRequired,

  // withDataSource
  dataSource: PropTypes.object.isRequired,

  // withExpression
  expression: PropTypes.object.isRequired,

  // withSetEditableByUser
  setEditableByUser: PropTypes.func.isRequired,
  editableByUser: PropTypes.bool.isRequired,
};

ExpressionBuilder.defaultProps = {
  isEditableSystemSet: false,
};

export default compose(withDataSource(), withExpression(), withSetEditableByUser)(ExpressionBuilder);
