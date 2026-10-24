package com.minigit.storage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HashUtilTest {
    @Test
    void emptyInputGivesKnownHash() {
        assertEquals(
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
                HashUtil.sha256Hex(new byte[0]));
    }

    @Test
    void sameInputGivesSameHash() {
        assertEquals(
                HashUtil.sha256Hex("hello".getBytes()),
                HashUtil.sha256Hex("hello".getBytes()));
    }

    @Test
    void differentInputGivesDifferentHash() {
        assertNotEquals(
                HashUtil.sha256Hex("hello".getBytes()),
                HashUtil.sha256Hex("hellp".getBytes()));
    }
}
