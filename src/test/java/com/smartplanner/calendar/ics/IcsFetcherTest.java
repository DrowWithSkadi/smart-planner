package com.smartplanner.calendar.ics;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class IcsFetcherTest {

    @Test
    void webcalBecomesHttps() throws IcsException {
        assertEquals("https://p01-calendars.icloud.com/published/2/abc",
                IcsFetcher.normalize("webcal://p01-calendars.icloud.com/published/2/abc").toString());
    }

    @Test
    void httpsIsKept() throws IcsException {
        assertEquals("https://example.com/cal.ics", IcsFetcher.normalize("  https://example.com/cal.ics ").toString());
    }

    @Test
    void otherSchemesAreRejected() {
        assertThrows(IcsException.class, () -> IcsFetcher.normalize("file:///etc/passwd"));
        assertThrows(IcsException.class, () -> IcsFetcher.normalize("ftp://example.com/cal.ics"));
        assertThrows(IcsException.class, () -> IcsFetcher.normalize(""));
    }
}
