import React from 'react';
import PropTypes from 'prop-types';
import { Input, Button, Checkbox, Popup, Grid } from 'semantic-ui-react';
import { compose, withState } from 'recompose';
import { connect } from 'react-redux';

import {
  ExpressionBuilder,
  actionCreators as expressionBuilderActionCreators,
} from 'components/ExpressionBuilder';

import { stringToExpression } from './searchBarUtils';

const { expressionChanged } = expressionBuilderActionCreators;

const withIsExpression = withState('isExpression', 'setIsExpression', false);

const withSearchString = withState('searchString', 'setSearchString', '');
const withExpression = withState('expression', 'setExpression', '');

const enhance = compose(
  connect(
    (state, props) => ({}),
    { expressionChanged },
  ),
  withIsExpression,
  withSearchString,
  withExpression,
);

const SearchBar = ({
  expressionDataSourceUuid,
  expressionId,
  expressionChanged,
  searchString,
  isExpression,
  setIsExpression,
  setSearchString,
  expression,
  setExpression,
}) => {
  const searchInput = (
    <React.Fragment>
      <Popup
        trigger={
          <Button
            circular
            icon="edit"
            onClick={() => {
              const parsedExpression = stringToExpression(searchString);
              if (parsedExpression.errors.length > 0) {
                // TODO alert user somehow
                console.error('errors!');
              } else {
                expressionChanged(expressionId, parsedExpression.expression);

                setIsExpression(true);
              }
            }}
          />
        }
        content="Switch to using the expression builder. You won't be able to convert back to a text search and keep your expression."
      />
      <Input
        value={searchString}
        className="SearchBar__input"
        onChange={(_, data) => setSearchString(data.value)}
      />
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
