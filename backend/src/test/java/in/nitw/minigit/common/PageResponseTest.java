package in.nitw.minigit.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PageResponseTest {

    @Test void missingOrNegativePageBecomesZero() {
        assertEquals(0, PageResponse.clampPage(null));
        assertEquals(0, PageResponse.clampPage(-3));
        assertEquals(4, PageResponse.clampPage(4));
    }

    @Test void sizeDefaultsAndIsCapped() {
        assertEquals(20, PageResponse.clampSize(null));
        assertEquals(20, PageResponse.clampSize(0));
        assertEquals(20, PageResponse.clampSize(-5));
        assertEquals(7, PageResponse.clampSize(7));
        assertEquals(100, PageResponse.clampSize(100_000));
    }

    @Test void offsetDoesNotOverflow() {
        assertEquals(40, PageResponse.offset(2, 20));
        assertEquals((long) Integer.MAX_VALUE * 100, PageResponse.offset(Integer.MAX_VALUE, 100));
    }
}
