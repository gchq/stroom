import React from 'react';
import PropTypes from 'prop-types';

import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';

import ElementCategory from './ElementCategory';
import { getBinItems } from '../pipelineUtils';

const enhance = compose(
  connect(
    (state, props) => ({
      // state
      elementsByCategory: state.elements.byCategory || {},
      elementsByType: state.elements.byType || {},
      pipeline: state.pipelines[props.pipelineId],
    }),
    {
      // actions
    },
  ),
  withProps(({ pipeline, elementsByType }) => ({
    recycleBinItems: getBinItems(pipeline.pipeline, elementsByType),
  })),
);

const ElementPalette = enhance(({ elementsByCategory, recycleBinItems }) => (
  <div className="element-palette">
    <ElementCategory category="Bin" elementsWithData={recycleBinItems} />
    {Object.entries(elementsByCategory).map(k => (
      <ElementCategory
        key={k[0]}
        category={k[0]}
        elementsWithData={k[1].map(e => ({ element: e }))}
      />
    ))}
  </div>
));

ElementPalette.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default ElementPalette;
