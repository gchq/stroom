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
      elementsByCategory: state.pipelineEditor.elements.byCategory || {},
      elementsByType: state.pipelineEditor.elements.byType || {},
      pipeline: state.pipelineEditor.pipelines[props.pipelineId],
    }),
    {
      // actions
    },
  ),
  withProps(({ pipeline, elementsByType }) => ({
    recycleBinItems: pipeline ? getBinItems(pipeline.pipeline, elementsByType) : [],
  })),
);

const ElementPalette = ({ elementsByCategory, recycleBinItems }) => (
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
);

ElementPalette.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(ElementPalette);
