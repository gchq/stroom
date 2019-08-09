package stroom.index.impl;

public class UpdateVolumeDTO  extends CreateVolumeDTO {
    private int id;

    public UpdateVolumeDTO() {

    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }
}
