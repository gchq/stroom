import * as React from "react";
import styled from "styled-components";
import { Popconfirm, Button, Card, Empty } from "antd";
import "antd/dist/antd.css";
import { Draggable, DroppableProvided } from "react-beautiful-dnd";
import { IndexVolume } from "./indexVolumeApi";
import IndexVolumeCard from "./IndexVolumeCard";
import MinimalInput from "components/MinimalInput";
import { IndexVolumeGroup } from "./indexVolumeGroupApi";

interface Props {
  indexVolumeGroup: IndexVolumeGroup;
  indexVolumes: IndexVolume[];
  provided: DroppableProvided;
  isDraggingOver: boolean;
  onGroupChange: (indexVolumeGroup: IndexVolumeGroup) => void;
  onGroupDelete: (id: string) => void;
  onVolumeAdd: (indexVolumeGroupName: string) => void;
  onVolumeChange: (indexVolume: IndexVolume) => void;
  onVolumeDelete: (indexVolumeId: string) => void;
}

const StyledMinimalInput = styled(MinimalInput)`
  margin-bottom: 0.5em;
  margin-right: 1em;
  font-size: 1.25em;
  height: 2em;
`;
const List = styled.div`
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
`;

const StyledCard = styled(Card)`
  margin-bottom: 1em;
`;

const HeaderButtons = styled.span`
  margin-left: 1em;
`;

const HeaderButtonSpan = styled.span`
  margin-right: 1em;
`;

const CardContainer = styled.div`
  display: flex;
  flex-direction: row;
`;

const getListStyle = (isDraggingOver: boolean) => {
  return {
    background: isDraggingOver ? "rgba(144,202,249,0.3)" : "white",
    width: "100%",
  };
};

const getItemStyle = (isDragging: boolean, draggableStyle: any) => ({
  userSelect: "none",
  background: isDragging ? "rgba(255, 143, 0, 0.2)" : "white",
  border: isDragging ? "1 solid lightgrey" : "1 solid blue",
  ...draggableStyle,
});

const IndexVolumeGroupCard: React.FunctionComponent<Props> = ({
  indexVolumeGroup,
  indexVolumes,
  provided,
  onGroupChange,
  onGroupDelete,
  onVolumeAdd,
  onVolumeChange,
  onVolumeDelete,
  isDraggingOver,
}) => {
  const indexVolumesInThisGroup = indexVolumes.filter(
    indexVolume => indexVolume.indexVolumeGroupName === indexVolumeGroup.name,
  );

  const StyledEmpty = styled(Empty)`
    margin: 0;
  `;
  return (
    <StyledCard
      title={
        <StyledMinimalInput
          defaultValue={indexVolumeGroup.name}
          onBlur={(event: React.ChangeEvent<HTMLInputElement>) => {
            indexVolumeGroup.name = event.target.value;
            onGroupChange(indexVolumeGroup);
          }}
        />
      }
      size="small"
      extra={
        <HeaderButtons>
          <HeaderButtonSpan>
            <Button
              icon="plus"
              size="small"
              onClick={() => onVolumeAdd(indexVolumeGroup.name)}
            >
              Add index volume
            </Button>
          </HeaderButtonSpan>

          <Popconfirm
            title="Delete this index volume group and all its index volumes?"
            onConfirm={() => onGroupDelete(indexVolumeGroup.id)}
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
        </HeaderButtons>
      }
    >
      <List style={getListStyle(isDraggingOver)}>
        {indexVolumesInThisGroup.length === 0 ? (
          <StyledEmpty
            description="No index volumes"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
          >
            <Button
              icon="plus"
              size="small"
              onClick={() => onVolumeAdd(indexVolumeGroup.name)}
            >
              Add index volume
            </Button>
          </StyledEmpty>
        ) : (
          undefined
        )}
        {indexVolumesInThisGroup.map((indexVolume, index) => {
          return (
            <CardContainer key={"draggable_" + indexVolume.id}>
              <Draggable
                key={"draggable_" + indexVolume.id}
                draggableId={"draggable_" + indexVolume.id}
                index={index}
              >
                {(provided, snapshot) => (
                  <div
                    ref={provided.innerRef}
                    {...provided.draggableProps}
                    {...provided.dragHandleProps}
                    style={getItemStyle(
                      snapshot.isDragging,
                      provided.draggableProps.style,
                    )}
                  >
                    <IndexVolumeCard
                      indexVolume={indexVolume}
                      onDelete={onVolumeDelete}
                      onChange={onVolumeChange}
                    />
                  </div>
                )}
              </Draggable>
            </CardContainer>
          );
        })}
        {provided.placeholder}
      </List>
    </StyledCard>
  );
};

export default IndexVolumeGroupCard;
