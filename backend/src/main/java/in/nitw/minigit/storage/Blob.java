package in.nitw.minigit.storage;

public class Blob extends StorageObject {
    private final byte[] content;

    public Blob(byte[] content) {
        this.content = content;
    }

    public byte[] getContent() {
        return content;
    }

    @Override
    public String getType() {
        return "blob";
    }

    @Override
    protected byte[] serializeBody() {
        return content;
    }

}
