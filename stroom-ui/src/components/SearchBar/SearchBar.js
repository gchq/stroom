import React from 'react';
import PropTypes from 'prop-types';
import { Container, Input, Button, Grid, Message } from 'semantic-ui-react';
import { compose, withState, withProps } from 'recompose';
import { connect } from 'react-redux';

import Tooltip from 'components/Tooltip';
import {
  ExpressionBuilder,
  actionCreators as expressionBuilderActionCreators,
} from 'components/ExpressionBuilder';
import { processSearchString } from './searchBarUtils';

const { expressionChanged } = expressionBuilderActionCreators;

const withExpression = withState('expression', 'setExpression', '');
const withIsExpression = withState('isExpression', 'setIsExpression', false);
const withIsExpressionVisible = withState('isExpressionVisible', 'setIsExpressionVisible', false);
const withSearchString = withState('searchString', 'setSearchString', '');
const withIsSearchStringValid = withState('isSearchStringValid', 'setIsSearchStringValid', true);
const withSearchStringValidationMessages = withState(
  'searchStringValidationMessages',
  'setSearchStringValidationMessages',
  [],
);

const enhance = compose(
  connect(
    ({ expressionBuilder }, { expressionId }) => ({
      expression: expressionBuilder[expressionId],
    }),
    { expressionChanged },
  ),
  withExpression,
  withIsExpression,
  withIsExpressionVisible,
  withSearchString,
  withIsSearchStringValid,
  withSearchStringValidationMessages,
  withProps(({ searchStringValidationMessages, isExpressionVisible }) => ({
    searchIsInvalid: searchStringValidationMessages.length > 0,
    visibilityClass: isExpressionVisible ? 'visible' : '',
    showHideExpressionIcon: isExpressionVisible ? 'angle up' : 'angle down',
  })),
);

const SearchBar = ({
  dataSource,
  expressionId,
  expressionChanged,
  searchString,
  isExpression,
  setIsExpression,
  setSearchString,
  expression,
  setExpression,
  setIsSearchStringValid,
  isSearchStringValid,
  setSearchStringValidationMessages,
  searchStringValidationMessages,
  onSearch,
  searchIsInvalid,
  visibilityClass,
  showHideExpressionIcon,
  isExpressionVisible,
  setIsExpressionVisible,
}) => (
  <div className="SearchBar flat">
    <div className="SearchBar__layoutGrid">
      {isExpression ? (
        <React.Fragment>
          <Tooltip
            trigger={
          <Button
            icon="text cursor"
            className="SearchBar__modeButton raised-low bordered hoverable"
            onClick={() => {
              setIsExpression(false);
              setIsExpressionVisible(false);
            }}
          />
          }
             content="Switch to using text search. You'll lose the expression you've built here."
           />
          <Button
            disabled={searchIsInvalid}
            className="SearchBar__hide-expression-button raised-low bordered hoverable"
            icon={showHideExpressionIcon}
            onClick={() => setIsExpressionVisible(!isExpressionVisible)}
          />
        </React.Fragment>
      ) : (
        <Tooltip
          trigger={
        <Button
          disabled={searchIsInvalid}
          className="SearchBar__modeButton raised-low bordered hoverable"
          icon="edit"
          onClick={() => {
            const parsedExpression = processSearchString(dataSource, searchString);
            expressionChanged(expressionId, parsedExpression.expression);
            setIsExpression(true);
            setIsExpressionVisible(true);
          }}
        />
          }
          content={
            <React.Fragment>
              <p>Switch to using the expression builder.</p>{' '}
              <p>You won't be able to convert back to a text search and keep your expression.</p>
            </React.Fragment>
          }
        />
      )}
      <Input
        placeholder="I.e. field1=value1 field2=value2"
        disabled={isExpression}
        action={
          <Button
            disabled={searchIsInvalid}
            className="icon-button"
            icon="search"
            onClick={() => {
              onSearch(expressionId);
            }}
          />
        }
        value={searchString}
        className="SearchBar__input"
        onChange={(_, data) => {
          const expression = processSearchString(dataSource, data.value);
          const invalidFields = expression.fields.filter(field => !field.conditionIsValid || !field.fieldIsValid || !field.valueIsValid);

          const searchStringValidationMessages = [];
          if (invalidFields.length > 0) {
            invalidFields.forEach((invalidField) => {
              searchStringValidationMessages.push(`'${invalidField.original}' is not a valid search term`);
            });
          }

          setIsSearchStringValid(invalidFields.length === 0);
          setSearchStringValidationMessages(searchStringValidationMessages);
          setSearchString(data.value);

          const parsedExpression = processSearchString(dataSource, searchString);
          expressionChanged(expressionId, parsedExpression.expression);
        }}
      />
      {searchIsInvalid ? (
        <Grid.Row>
          <Grid.Column width={1} />
          <Grid.Column width={15}>
            <Container>
              <Message warning className="SearchBar__validationMessages">
                {searchStringValidationMessages.map((message, i) => (
                  <p key={i}>{message}</p>
                ))}
              </Message>
            </Container>
          </Grid.Column>
        </Grid.Row>
      ) : (
        undefined
      )}
    </div>
    <div className="dropdown SearchBar__expression">
      <div tabIndex={0} className={`dropdown__content ${visibilityClass}`}>
        <ExpressionBuilder
          className="SearchBar__expressionBuilder"
          showModeToggle={false}
          editMode
          dataSource={dataSource}
          expressionId={expressionId}
        />
      </div>
    </div>
  </div>
);

SearchBar.propTypes = {
  dataSource: PropTypes.object.isRequired,
  expressionId: PropTypes.string.isRequired,
  searchString: PropTypes.string,
  onSearch: PropTypes.func.isRequired,
};

export default enhance(SearchBar);
