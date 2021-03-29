import * as React from "react";
import styled from "styled-components";
import { Popconfirm, Button, Card } from "antd";
import DocRefImage from "../DocRefImage";
import { IndexVolume } from "./indexVolumeApi";
import MinimalInput from "components/MinimalInput";

interface Props {
  indexVolume: IndexVolume;
  onDelete: (volumeId: string) => void;
  onChange: (indexVolume: IndexVolume) => void;
}

const TopRightButtons = styled.div`
  float: right;
  visibility: hidden;
`;

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

const IconColumn = styled.div`
  padding-right: 1em;
  justify-content: center;
  align-items: center;
  display: flex;
  flex-direction: column;
`;

const Contents = styled.div`
  display: flex;
  flex-direction: row;
`;

const StyledMinimalInput = styled(MinimalInput)`
  height: 1.8em;
  width: 13em;
`;

/**
 * This has to be a class component instead of a functional component
 * because we need to use a 'ref' for BeautifulDnd to be able to handle drops.
 */
const DraggableIndexVolumeCard: React.FunctionComponent<Props> = ({
  indexVolume,
  onDelete,
  onChange,
}) => {
  const StyledCard = styled(Card)`
    margin: 0.5em;
  `;
  return (
    <StyledCard
      size="small"
      type="inner"
      title="Index volume"
      extra={
        <Popconfirm
          title="Delete this index volume?"
          onConfirm={() => onDelete(indexVolume.id)}
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
      }
    >
      <TopRightButtons>
        <Popconfirm
          title="Delete this index volume?"
          onConfirm={() => onDelete(indexVolume.id)}
          okText="Yes"
          cancelText="No"
          placement="left"
        >
          <Button
            // type="danger"
            shape="circle"
            icon="delete"
            size="small"
          />
        </Popconfirm>
      </TopRightButtons>
      <Contents>
        <IconColumn>
          <DocRefImage docRefType="Index" size="lg" />
        </IconColumn>
        <div>
          <Field>
            <Label>Node name: </Label>
            <StyledMinimalInput
              defaultValue={indexVolume.nodeName}
              onBlur={(event: React.ChangeEvent<HTMLInputElement>) => {
                indexVolume.nodeName = event.target.value;
                onChange(indexVolume);
              }}
            />
          </Field>
          <Field>
            <Label>Path: </Label>
            <StyledMinimalInput
              defaultValue={indexVolume.path}
              onBlur={(event: React.ChangeEvent<HTMLInputElement>) => {
                indexVolume.path = event.target.value;
                onChange(indexVolume);
              }}
            />
          </Field>
        </div>
      </Contents>
    </StyledCard>
  );
};

export default DraggableIndexVolumeCard;
