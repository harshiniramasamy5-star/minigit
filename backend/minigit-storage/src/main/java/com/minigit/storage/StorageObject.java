package com.minigit.storage;
import java.nio.charset.StandardCharsets;

public abstract class StorageObject {
    /** "blob", "tree" or "commit" */
    public abstract String getType();

    /** The content part, without the header. */
    protected abstract byte[] serializeBody();

    /** Header ("type size\0") followed by the body. This is what gets hashed and stored. */
    public byte[] serialize() {
        byte[] body = serializeBody();
        byte[] header = (getType() + " " + body.length + "\0").getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[header.length + body.length];
        System.arraycopy(header, 0, all, 0, header.length);
        System.arraycopy(body, 0, all, header.length, body.length);
        return all;
}

    public String getHash() {
        return HashUtil.sha256Hex(serialize());
    }
}
