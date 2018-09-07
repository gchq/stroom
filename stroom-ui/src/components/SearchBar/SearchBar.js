import React from 'react';
import PropTypes from 'prop-types';
import { Container, Input, Button, Grid, Message } from 'semantic-ui-react';
import { compose, withState, withProps, lifecycle } from 'recompose';
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
  lifecycle({
    componentDidMount() {
      // We need to set up an expression so we've got something to search with,
      // even though it'll be empty.
      const { expressionChanged, expressionId, dataSource } = this.props;
      const parsedExpression = processSearchString(dataSource, '');
      expressionChanged(expressionId, parsedExpression.expression);

      // if (!selectedRow) {
      const { onSearch } = this.props;
      console.log({ onSearch });
      // console.log({dataViewerId, pageOffset, pageSize, onSearch});
      onSearch(expressionId);
      // search(dataViewerId, pageOffset, pageSize);
      // }
    },
  }),
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
  <div className="search-bar__dropdown search-bar borderless">
    <div className="search-bar__header">
      <Input
        placeholder="I.e. field1=value1 field2=value2"
        value={searchString}
        className="search-bar__input"
        onFocus={() => setIsExpressionVisible(true)}
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
          setIsExpressionVisible(false);
        }}
      />
    </div>
    <div
      tabIndex={0}
      className={`search-bar__dropdown__content search-bar__content ${visibilityClass}`}
    >
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
              setIsExpressionVisible(false);
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
              const parsedExpression = processSearchString(dataSource, searchString);
              expressionChanged(expressionId, parsedExpression.expression);
              setIsExpression(true);
              setIsExpressionVisible(true);
            }}
          />
        </Button.Group>
        <Button
          icon="close"
          size="mini"
          onClick={() => setIsExpressionVisible(false)}
          className="raised-low bordered hoverable search-bar__close-button"
        />
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
        <div>TODO: Help text for string search, or prompts</div>
      )}
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
