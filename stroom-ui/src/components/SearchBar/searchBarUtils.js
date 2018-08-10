import { path } from 'ramda';

/**
 * Takes a search string from the input box and checks that the fields
 * exist in the data source.
 * @param {object} dataSource The data source for the expression
 * @param {string} criteria The search string
 */
export const processSearchString = (dataSource, criteria) => {
  const splitted = split(criteria);

  const validationResults = [];
  splitted.forEach((criterionObj) => {
    let validationResult;
    if (criterionObj.splitCriterion !== undefined) {
      // Field validation
      const field = criterionObj.splitCriterion[0];
      const foundField = dataSource.fields.filter(availableField => availableField.name === field);
      const fieldIsValid = foundField.length > 0;

      // Condition/operator validation
      const operator = criterionObj.splitCriterion[1];
      const foundCondition = dataSource.fields.filter(availableField =>
        availableField.name === field &&
          availableField.conditions.find(condition => condition === operator));
      const conditionIsValid = foundCondition.length > 0;

      // Value validation
      const value = criterionObj.splitCriterion[2];
      const valueIsValid = value != undefined && value != '';

      validationResult = {
        original: criterionObj.criterion,
        parsed: criterionObj.splitCriterion,
        fieldIsValid,
        conditionIsValid,
        valueIsValid,
        term: toTermFromArray(criterionObj.splitCriterion),
      };
    } else {
      // If we don't have a splitCriterion then the term is invalid and we'll return
      // a result with false for everything.
      validationResult = {
        original: criterionObj.criterion,
        parsed: criterionObj.splitCriterion,
        fieldIsValid: false,
        conditionIsValid: false,
        valueIsValid: false,
        term: undefined,
      };
    }
    validationResults.push(validationResult);
  });

  const expression = {
    uuid: 'root',
    type: 'operator',
    op: 'AND',
    children: validationResults
      .filter(validationResult => validationResult.term !== undefined)
      .map(validationResult => validationResult.term),
    enabled: true,
  };

  return { expression, fields: validationResults };
};

/**
 * Replaces the operator with one of the type used by the datasource,
 * e.g. = becomes 'EQUALS'
 * @param {array} criterion The search criterion as an array.
 */
const parse = (criterion) => {
  let split;
  Object.keys(operatorMap).map((key) => {
    if (criterion.includes(key)) {
      split = criterion.split(key);
      split.splice(1, 0, operatorMap[key]);
    }
  });
  return split;
};

/**
 * Takes an input string and parses it into pairs, replacing the boolean
 * operators with ExpressionBuilder conditions.
 * @param {string} criteria The string from the search input box
 */
const split = criteria =>
  criteria
    .split(' ')
    .filter(criterion => criterion !== '' && criterion !== ' ')
    .map(criterion => ({ criterion, splitCriterion: parse(criterion) }));

/**
 * Creates an ExpressionBuilder term from the passed array. The array must look like ['foo', 'EQUALS', 'bar'].
 * @param {array} asArray The term as an array
 */
const toTermFromArray = asArray => toTerm(asArray[0], asArray[1], asArray[2]);

/**
 * Returns an ExpressionBuilder term
 * @param {string} field
 * @param {string} condition
 * @param {string} value
 */
const toTerm = (field, condition, value) => ({
  type: 'term',
  field,
  condition,
  value,
  dictionary: null,
  enabled: true,
});

/**
 * A map of operators to ExpressionBuilder conditions
 */
const operatorMap = {
  '=': 'EQUALS',
  '>': 'GREATER_THAN',
  '<': 'LESS_THAN',
  '>=': 'GREATER_THAN_OR_EQUAL_TO',
  '<=': 'LESS_THAN_OR_EQUAL_TO',
};
