package com.minigit.storage;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BlobTest {
    @Test
    void sameContentSameHash() {
        Blob a = new Blob("hello world".getBytes());
        Blob b = new Blob("hello world".getBytes());
        assertEquals(a.getHash(), b.getHash());
    }

    @Test
    void oneByteChangedChangesHash() {
        Blob a = new Blob("hello world".getBytes());
        Blob b = new Blob("hello worle".getBytes());
        assertNotEquals(a.getHash(), b.getHash());
    }
}
