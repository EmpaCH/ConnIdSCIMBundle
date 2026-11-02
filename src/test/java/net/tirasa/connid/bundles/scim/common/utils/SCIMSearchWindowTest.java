/*
 * Copyright (C) 2026 Empa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tirasa.connid.bundles.scim.common.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.junit.jupiter.api.Test;

class SCIMSearchWindowTest {

    /** A server with {@code total} rows, numbered from 1, answering pages of at most {@code cap} rows. */
    private static SCIMSearchWindow.PageReader<Integer> server(final int total, final int cap) {
        return (start, size) -> {
            List<Integer> rows = new ArrayList<>();
            for (int i = start; i < start + Math.min(size, cap) && i <= total; i++) {
                rows.add(i);
            }
            return rows;
        };
    }

    private static OperationOptions options(final Integer offset, final String cookie, final Integer pageSize) {
        OperationOptionsBuilder builder = new OperationOptionsBuilder();
        if (offset != null) {
            builder.setPagedResultsOffset(offset);
        }
        if (cookie != null) {
            builder.setPagedResultsCookie(cookie);
        }
        if (pageSize != null) {
            builder.setPageSize(pageSize);
        }
        return builder.build();
    }

    @Test
    void noPagingOptionsStreamEveryRow() {
        List<Integer> seen = new ArrayList<>();
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, null, null), 100);
        assertTrue(window.isStreaming());

        Optional<String> cookie = SCIMSearchWindow.read(window, server(250, 1000), seen::add);

        assertEquals(250, seen.size(), "an unpaged search returns every row, not the first page");
        assertEquals(Optional.empty(), cookie);
    }

    @Test
    void streamingDoesNotDependOnTheServersOwnCap() {
        List<Integer> seen = new ArrayList<>();
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, null, null), 100);
        // A server that caps pages at 30 rows still gives every row, because each page is asked for explicitly.
        SCIMSearchWindow.read(window, server(250, 30), seen::add);
        assertEquals(250, seen.size());
    }

    @Test
    void aPagedCallReturnsOneWindowAndTheCookieForTheNext() {
        List<Integer> seen = new ArrayList<>();
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, null, 100), 100);
        assertFalse(window.isStreaming());

        Optional<String> cookie = SCIMSearchWindow.read(window, server(250, 1000), seen::add);

        assertEquals(100, seen.size());
        assertEquals(Optional.of("101"), cookie);
    }

    @Test
    void theCookieResumesTheNextWindow() {
        List<Integer> seen = new ArrayList<>();
        SCIMSearchWindow next = SCIMSearchWindow.of(options(null, "101", 100), 100);
        assertEquals(101, next.getStart());

        Optional<String> cookie = SCIMSearchWindow.read(next, server(250, 1000), seen::add);

        assertEquals(101, seen.get(0));
        assertEquals(Optional.of("201"), cookie);
    }

    @Test
    void aShortPageStillLeavesACookieUntilAnEmptyPageEndsTheSearch() {
        List<Integer> seen = new ArrayList<>();
        SCIMSearchWindow last = SCIMSearchWindow.of(options(null, "201", 100), 100);

        Optional<String> cookie = SCIMSearchWindow.read(last, server(250, 1000), seen::add);

        assertEquals(50, seen.size());
        assertEquals(Optional.of("251"), cookie, "the caller asks once more and gets an empty page");
        assertEquals(Optional.empty(), SCIMSearchWindow.read(
                SCIMSearchWindow.of(options(null, "251", 100), 100), server(250, 1000), seen::add));
    }

    @Test
    void anOffsetWinsOverACookieAndTheCallersSizeOverTheDefault() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(50, "901", 25), 100);
        assertEquals(50, window.getStart());
        assertEquals(25, window.getSize());
    }

    @Test
    void theWindowIsNeverChangedByReading() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, null, null), 100);
        SCIMSearchWindow.read(window, server(250, 1000), row -> { });
        assertEquals(1, window.getStart());
    }
}
