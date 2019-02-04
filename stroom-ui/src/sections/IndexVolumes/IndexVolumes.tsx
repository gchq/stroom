import * as React from "react";

import { compose } from "recompose";

export interface Props {}

interface EnhancedProps extends Props {}

const enhance = compose<Props, EnhancedProps>();

const IndexVolumes = (props: EnhancedProps) => <div>Index Volumes</div>;

export default enhance(IndexVolumes);
