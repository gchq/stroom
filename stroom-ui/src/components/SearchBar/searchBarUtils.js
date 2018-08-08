import { path } from 'ramda';

// TODO: clean up here.

// TODO: scrap this once the test are migrated
/**
 * Converts an input string to an expression, ready for the ExpressionBuilder to use.
 * Returns an object with an array of errors (if any), and the expression.
 * @param {string} criteria The string from the search input box
 */
export const stringToExpression = (criteria) => {
  criteria = criteria.trim();
  const expression = {
    uuid: 'root',
    type: 'operator',
    op: 'AND',
    children: [],
    enabled: true,
  };

  const errors = [];

  split(criteria).map((data) => {
    if (!data.splitCriterion) {
      errors.push(data.criterion);
    } else {
      const term = toTermFromArray(data.splitCriterion);
      expression.children.push(term);
    }
  });

  return { expression, errors };
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
 * Takes a search string from the input box and checks that the fields
 * exist in the data source.
 * @param {object} dataSource The data source for the expression
 * @param {string} criteria The search string
 */
export const processSearchString = (dataSource, criteria) => {
  const splitted = split(criteria);
  // console.log({ splitted });
  // console.log({ blagh: splitted[1].splitCriterion });

  const validationResults = [];
  // console.log({ ARGH5: splitted.length });
  splitted.forEach((criterionObj) => {
    const field = criterionObj.splitCriterion[0];
    const operator = criterionObj.splitCriterion[1];
    const value = criterionObj.splitCriterion[2];
    // console.log({ field, operator, value });

    const foundField = dataSource.fields.filter(availableField => availableField.name === field);
    // console.log({ foundField });
    const foundCondition = dataSource.fields.filter(availableField =>
      availableField.name === field &&
        availableField.conditions.find(condition => condition === operator));
    // console.log({ foundCondition });

    const validationResult = {
      original: criterionObj.criterion,
      parsed: criterionObj.splitCriterion,
      fieldIsValid: foundField.length > 0,
      conditionIsValid: foundCondition.length > 0,
      term: toTermFromArray(criterionObj.splitCriterion),
    };
    validationResults.push(validationResult);
  });

  const expression = {
    uuid: 'root',
    type: 'operator',
    op: 'AND',
    children: validationResults.map(validationResult => validationResult.term),
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
      // console.log({ ARGH1: criterion });
      split = criterion.split(key);
      // console.log({ ARGH2: split });
      split.splice(1, 0, operatorMap[key]);
      // console.log({ ARGH3: split });
    }
  });
  return split;
};

/**
 * Creates an ExpressionBuilder term from the passed array. The array must look like ['foo', 'EQUALS', 'bar'].
 * @param {array} asArray The term as an array
 */
const toTermFromArray = asArray => toTerm(asArray[0], asArray[1], asArray[2]);

const toTerm = (field, condition, value) => ({
  type: 'term',
  field,
  condition,
  value,
  dictionary: null,
  enabled: true,
});

const operatorMap = {
  '=': 'EQUALS',
  '>': 'GREATER_THAN',
  '<': 'LESS_THAN',
  '>=': 'GREATER_THAN_OR_EQUAL_TO',
  '<=': 'LESS_THAN_OR_EQUAL_TO',
};
