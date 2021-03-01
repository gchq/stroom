package stroom.proxy.app.handler;

import stroom.receive.common.StreamHandler;
import stroom.util.io.StreamUtil;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

class MockStreamHandler implements StreamHandler {

    private final List<String> entryNameList = new ArrayList<>();
    private final List<byte[]> byteArrayList = new ArrayList<>();
    private int handleEntryCount = 0;

    private boolean generateExceptionOnData = false;

    @Override
    public void addEntry(final String entry, final InputStream inputStream) {
        handleEntryCount++;
        entryNameList.add(entry);
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        StreamUtil.streamToStream(inputStream, byteArrayOutputStream);

        if (generateExceptionOnData) {
            throw new RuntimeException("Mock Data Error");
        }

        byteArrayList.add(byteArrayOutputStream.toByteArray());
    }

    public List<String> getEntryNameList() {
        return entryNameList;
    }

    public byte[] getByteArray(String entryName) {
        return byteArrayList.get(entryNameList.indexOf(entryName));
    }

    public int getHandleEntryCount() {
        return handleEntryCount;
    }

    public void setGenerateExceptionOnData(boolean generateExceptionOnData) {
        this.generateExceptionOnData = generateExceptionOnData;
    }
}
