import * as React from "react";
import styled from "styled-components";
import { Popconfirm, Button, Card } from "antd";
import "antd/dist/antd.css";
import FsVolume from "./types/FsVolume";
import MinimalInput from "components/MinimalInput";
import { Radio, Progress, Tooltip } from "antd";

// import { VolumeUseStatus } from "./types/VolumeUseStatus";
import { RadioChangeEvent } from "antd/lib/radio";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

interface Props {
  volume: FsVolume;
  onDelete: (volume: FsVolume) => void;
  onChange: (volume: FsVolume) => void;
}

const Field = styled.div`
  height: 2.3em;
  display: flex;
  flex-direction: row;
  text-align: right;
`;

const Label = styled.label`
  width: 5.8em;
  margin-right: 0.5em;
  padding-top: 0.2em;
`;

const Contents = styled.div`
  display: flex;
  flex-direction: row;
`;

const PathInput = styled(MinimalInput)`
  height: 1.8em;
  width: 20em;
`;

const ByteLimitInput = styled(MinimalInput)`
  height: 1.8em;
  width: 12em;
`;

const FieldDiv = styled.div`
  margin-left: 3em;
`;

const StatsDiv = styled.div`
  margin: 0.75em;
`;

const IconDiv = styled.div`
  display: flex;
  flex-direction: column;
`;

const SaveIcon = styled(FontAwesomeIcon)`
  margin: 0.2em;
  color: red;
`;

const DataVolumeCard: React.FunctionComponent<Props> = ({
  volume,
  onDelete,
  onChange,
}) => {
  const StyledCard = styled(Card)`
    margin: 0.5em;
    width: 40.75em;
    margin-bottom: 1em;
  `;

  const [disabled, setDisabled] = React.useState(false);
  const { bytesFree, bytesUsed, bytesTotal } = volume.volumeState;
  const percent = Math.round((bytesUsed / bytesTotal) * 100);
  const status = percent < 95 ? "normal" : "exception";
  let tooltip = `Using ${bytesUsed} of ${bytesTotal} bytes, leaving ${bytesFree} bytes free.`;
  if (isNaN(percent)) {
    tooltip = `We don't yet have any information about this volume.`;
  }
  const progressFormat = isNaN(percent) ? "?" : `${percent}%`;
  return (
    <StyledCard size="small" type="inner">
      <Contents>
        <StatsDiv>
          <Tooltip title={tooltip}>
            <Progress
              type="circle"
              percent={percent}
              format={() => progressFormat}
              width={80}
              status={status}
            />
          </Tooltip>
        </StatsDiv>

        <FieldDiv>
          <Field>
            <Label>Path: </Label>
            <PathInput
              disabled={disabled}
              defaultValue={volume.path}
              onBlur={(event: React.ChangeEvent<HTMLInputElement>) => {
                volume.path = event.target.value;
                setDisabled(true);
                onChange(volume);
              }}
            />
          </Field>

          <Field>
            <Label>Status:</Label>
            <Radio.Group
              disabled={disabled}
              defaultValue="0"
              value={volume.status}
              buttonStyle="solid"
              size="small"
              onChange={(event: RadioChangeEvent) => {
                volume.status = event.target.value;
                setDisabled(true);
                onChange(volume);
              }}
            >
              <Radio.Button value="ACTIVE">Active</Radio.Button>
              <Radio.Button value="INACTIVE">Inactive</Radio.Button>
              <Radio.Button value="CLOSED">Closed</Radio.Button>
            </Radio.Group>
          </Field>

          <Field>
            <Label>Byte limit:</Label>
            <ByteLimitInput
              disabled={disabled}
              type="number"
              defaultValue={volume.byteLimit}
              onBlur={(event: React.ChangeEvent<HTMLInputElement>) => {
                volume.byteLimit = parseInt(event.target.value);
                setDisabled(true);
                onChange(volume);
              }}
            />
          </Field>
        </FieldDiv>
        <IconDiv>
          <Popconfirm
            title="Delete this data volume?"
            onConfirm={() => onDelete(volume)}
            okText="Yes"
            cancelText="No"
            placement="left"
          >
            <Button
              ghost
              // type="danger"
              shape="circle"
              icon="delete"
              size="small"
            />
          </Popconfirm>
          {disabled ? <SaveIcon size="lg" icon="save" /> : undefined}
        </IconDiv>
      </Contents>
    </StyledCard>
  );
};

export default DataVolumeCard;
