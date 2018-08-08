import React from 'react';
import PropTypes from 'prop-types';
import { Input, Button, Checkbox, Popup, Grid } from 'semantic-ui-react';
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
      <Popup
        trigger={<Button circular icon="edit" onClick={() => setIsExpression(true)} />}
        content="Switch to using the expression builder. You won't be able to convert back to a text search and keep your expression."
      />
      <Input value={searchString} className="SearchBar__input" />
      <Button>Search</Button>
    </React.Fragment>
  );

  const expressionBuilder = (
    <React.Fragment>
      <Grid>
        <Grid.Column width={1}>
          <Popup
            trigger={<Button circular icon="text cursor" onClick={() => setIsExpression(false)} />}
            content="Switch to using text search. You'll lose the expression you've built here."
          />
        </Grid.Column>
        <Grid.Column width={15}>
          <ExpressionBuilder
            showModeToggle={false}
            editMode
            dataSourceUuid={expressionDataSourceUuid}
            expressionId={expressionId}
          />
        </Grid.Column>
      </Grid>
    </React.Fragment>
  );

  return <div>{isExpression ? expressionBuilder : searchInput}</div>;
};

SearchBar.propTypes = {
  expressionDataSourceUuid: PropTypes.string.isRequired,
  expressionId: PropTypes.string.isRequired,
  searchString: PropTypes.string,
};

export default enhance(SearchBar);
