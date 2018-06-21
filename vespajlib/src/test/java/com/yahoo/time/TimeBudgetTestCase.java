// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.time;

import org.junit.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class TimeBudgetTestCase {
    private final Clock clock = mock(Clock.class);

    @Test
    public void testBasics() {
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(0));
        TimeBudget timeBudget = TimeBudget.fromNow(clock, Duration.ofSeconds(10));

        when(clock.instant()).thenReturn(Instant.ofEpochSecond(7));
        assertEquals(Duration.ofSeconds(3), timeBudget.timeLeftOrThrow());

        // Verify that toMillis() of >=1 is fine, but 0 is not.

        when(clock.instant()).thenReturn(Instant.ofEpochSecond(9, 999000000));
        assertEquals(1, timeBudget.timeLeftOrThrow().toMillis());
        when(clock.instant()).thenReturn(Instant.ofEpochSecond(9, 999000001));
        try {
            timeBudget.timeLeftOrThrow();
            fail();
        } catch (UncheckedTimeoutException e) {
            // OK
        }
    }
}