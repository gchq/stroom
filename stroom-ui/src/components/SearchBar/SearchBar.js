import React from 'react';
import PropTypes from 'prop-types';
import { Input, Button, Checkbox, Popup } from 'semantic-ui-react';
import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import { ExpressionBuilder } from 'components/ExpressionBuilder';

const withIsExpression = withState('isExpression', 'setIsExpression', false);

const enhance = compose(
  connect(
    (state, props) => ({}),
    {},
  ),
  withIsExpression,
);

const SearchBar = ({
  expressionDataSourceUuid,
  expressionId,
  searchString,
  isExpression,
  setIsExpression,
}) => {
  const searchInput = (
    <React.Fragment>
      <Input value={searchString} />
      <Button>Search</Button>
      <Popup
        trigger={<Button circular icon="edit" onClick={() => setIsExpression(true)} />}
        content="Switch to using the expression builder. You won't be able to convert back to a simple query."
      />
    </React.Fragment>
  );

  const expressionBuilder = (
    <ExpressionBuilder
      allowEdit
      dataSourceUuid={expressionDataSourceUuid}
      expressionId={expressionId}
    />
  );

  return <div>{isExpression ? expressionBuilder : searchInput}</div>;
};

SearchBar.propTypes = {
  expressionDataSourceUuid: PropTypes.string.isRequired,
  expressionId: PropTypes.string.isRequired,
  searchString: PropTypes.string,
};

export default enhance(SearchBar);
