import React from 'react';
import PropTypes from 'prop-types';
import { Button } from 'semantic-ui-react';
import { compose, withState, withProps, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import {
  ExpressionBuilder,
  expressionToString,
  actionCreators as expressionBuilderActionCreators,
} from 'components/ExpressionBuilder';
import { processSearchString } from './expressionSearchBarUtils';

const { expressionChanged } = expressionBuilderActionCreators;

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
      expressionState: expressionBuilder[expressionId],
    }),
    { expressionChanged },
  ),
  withIsExpression,
  withIsExpressionVisible,
  withSearchString,
  withIsSearchStringValid,
  withSearchStringValidationMessages,
  lifecycle({
    componentDidMount() {
      // We need to set up an expression so we've got something to search with,
      // even though it'll be empty.
      const { expressionChanged, expressionId, dataSource } = this.props;
      const parsedExpression = processSearchString(dataSource, '');
      expressionChanged(expressionId, parsedExpression.expression);

      const { onSearch } = this.props;
      onSearch(expressionId);
    },
  }),
  withProps(({
    searchStringValidationMessages,
    isExpression,
    expression,
    setSearchString,
    isExpressionVisible,
  }) => ({
    searchIsInvalid: searchStringValidationMessages.length > 0,
  })),
);

const ExpressionSearchBar = ({
  dataSource,
  expressionId,
  expressionChanged,
  searchString,
  isExpression,
  setIsExpression,
  setSearchString,
  expression,
  expressionState,
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
  <div className="dropdown search-bar borderless">
    <div className="search-bar__header">
      <input
        placeholder="I.e. field1=value1 field2=value2"
        value={isExpression ? expressionState.expressionAsString : searchString}
        className="search-bar__input"
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
      <Button
        disabled={searchIsInvalid}
        className="icon-button"
        icon="search"
        onClick={() => {
          onSearch(expressionId);
        }}
      />
    </div>
    <div tabIndex={0} className={`dropdown__content search-bar__content ${visibilityClass}`}>
      <div className="search-bar__content__header">
        <Button.Group size="mini">
          <Button
            content="Text search"
            size="mini"
            positive={!isExpression}
            icon="text cursor"
            className="search-bar__modeButton raised-low bordered hoverable"
            onClick={() => {
              setIsExpression(false);
            }}
          />
          <Button.Or />
          <Button
            content="Expression search"
            size="mini"
            positive={isExpression}
            disabled={searchIsInvalid}
            className="search-bar__modeButton raised-low bordered hoverable"
            icon="edit"
            onClick={() => {
              if (!isExpression) {
                const parsedExpression = processSearchString(dataSource, searchString);
                expressionChanged(expressionId, parsedExpression.expression);
                setIsExpression(true);
              }
            }}
          />
        </Button.Group>
      </div>
      {isExpression ? (
        <ExpressionBuilder
          className="search-bar__expressionBuilder"
          showModeToggle={false}
          editMode
          dataSource={dataSource}
          expressionId={expressionId}
        />
      ) : (
        <div>{searchStringValidationMessages}</div>
      )}
    </div>
  </div>
);

ExpressionSearchBar.propTypes = {
  dataSource: PropTypes.object.isRequired,
  expressionId: PropTypes.string.isRequired,
  searchString: PropTypes.string,
  onSearch: PropTypes.func.isRequired,
};

export default enhance(ExpressionSearchBar);
