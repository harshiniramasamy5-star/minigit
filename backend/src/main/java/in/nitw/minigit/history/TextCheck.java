package in.nitw.minigit.history;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/** Is this file text we can show and diff? (valid UTF-8 and no NUL bytes) */
public final class TextCheck {

    private TextCheck() {}

    public static boolean isText(byte[] data) {
        for (byte b : data) {
            if (b == 0) return false;
        }
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
