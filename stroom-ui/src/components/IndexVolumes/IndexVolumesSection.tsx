import { Button, Empty } from "antd";
import DocRefIconHeader from "components/DocRefIconHeader";
import * as React from "react";
import { DragDropContext, Droppable, DropResult } from "react-beautiful-dnd";
import styled from "styled-components";
import { IndexVolume } from "./indexVolumeApi";
import { IndexVolumeGroup } from "./indexVolumeGroupApi";
import IndexVolumeGroupCard from "./IndexVolumeGroupCard";

interface Props {
  indexVolumes: IndexVolume[];
  indexVolumeGroups: IndexVolumeGroup[];
  onGroupDelete: (id: string) => void;
  onGroupAdd: () => void;
  onGroupChange: (indexVolumeGroup: IndexVolumeGroup) => void;
  onVolumeAdd: (indexVolumeGroupName: string) => void;
  onVolumeChange: (indexVolume: IndexVolume) => void;
  onVolumeDelete: (indexVolumeId: string) => void;
}

const IndexVolumesSection: React.FunctionComponent<Props> = ({
  indexVolumes,
  indexVolumeGroups,
  onGroupDelete,
  onGroupAdd,
  onGroupChange,
  onVolumeAdd,
  onVolumeChange,
  onVolumeDelete,
}) => {
  if (!indexVolumes) {
    indexVolumes = [];
  }
  if (!indexVolumeGroups) {
    indexVolumeGroups = [];
  }
  const Page = styled.div``;

  const Body = styled.div`
    padding: 1em;
  `;

  const onDragEnd = (result: DropResult) => {
    if (!result.destination) {
      return;
    }

    if (result.destination.droppableId === result.source.droppableId) {
      return;
    }

    const removePrefix = (prefixedId: string) =>
      prefixedId.slice(prefixedId.indexOf("_") + 1, prefixedId.length);

    const idToMove: string = removePrefix(result.draggableId);
    let indexVolumeBeingMoved = indexVolumes.find(i => +i.id === +idToMove);
    indexVolumeBeingMoved.indexVolumeGroupName = removePrefix(
      result.destination.droppableId,
    );
    onVolumeChange(indexVolumeBeingMoved);
  };

  return (
    <Page className="page">
      <div className="page__header">
        <DocRefIconHeader text="Index Volumes" docRefType="Index" />
        <div className="page__buttons">
          <Button icon="plus" onClick={() => onGroupAdd()}>
            Add index volume group
          </Button>
        </div>
      </div>
      <Body className="page__body">
        <DragDropContext onDragEnd={onDragEnd}>
          {indexVolumeGroups.length === 0 ? (
            <Empty
              description="No index volumes groups"
              image={Empty.PRESENTED_IMAGE_SIMPLE}
            >
              <Button icon="plus" size="small" onClick={() => onGroupAdd()}>
                Add index volume group
              </Button>
            </Empty>
          ) : (
            undefined
          )}
          {indexVolumeGroups.map(indexVolumeGroup => {
            return (
              <Droppable
                key={"droppable_" + indexVolumeGroup.name}
                droppableId={"droppable_" + indexVolumeGroup.name}
                direction="horizontal"
              >
                {(provided, snapshot) => (
                  <div {...provided.droppableProps} ref={provided.innerRef}>
                    <IndexVolumeGroupCard
                      indexVolumeGroup={indexVolumeGroup}
                      indexVolumes={indexVolumes}
                      onGroupDelete={onGroupDelete}
                      onGroupChange={onGroupChange}
                      onVolumeAdd={onVolumeAdd}
                      onVolumeDelete={onVolumeDelete}
                      onVolumeChange={onVolumeChange}
                      provided={provided}
                      isDraggingOver={snapshot.isDraggingOver}
                    />
                  </div>
                )}
              </Droppable>
            );
          })}
        </DragDropContext>
      </Body>
    </Page>
  );
};

export default IndexVolumesSection;
