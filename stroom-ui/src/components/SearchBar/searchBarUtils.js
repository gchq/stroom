import { split } from 'ramda';

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

  criteria
    .split(' ')
    .filter(criterion => criterion !== '' && criterion !== ' ')
    .map((criterion) => {
      const splitCriterion = parse(criterion);
      if (!splitCriterion) {
        errors.push(criterion);
      } else {
        const term = toTermFromArray(splitCriterion);
        expression.children.push(term);
      }
    });

  return { expression, errors };
};

const parse = (criterion) => {
  let split;
  Object.keys(dict).map((key) => {
    if (criterion.includes(key)) {
      split = criterion.split(key);
      split.splice(1, 0, key);
    }
  });
  return split;
};

const toTermFromArray = asArray => toTerm(asArray[0], asArray[1], asArray[2]);

const toTerm = (field, condition, value) => ({
  type: 'term',
  field,
  condition: dict[condition],
  value,
  dictionary: null,
  enabled: true,
});

const dict = {
  '=': 'EQUALS',
  '>': 'GREATER_THAN',
  '<': 'LESS_THAN',
  '>=': 'GREATER_THAN_OR_EQUAL_TO',
  '<=': 'LESS_THAN_OR_EQUAL_TO',
};
