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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationOptionsBuilder;
import org.junit.jupiter.api.Test;

class SCIMSearchWindowTest {

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
    void noPagingOptionsStreamAllPagesFromTheStart() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, null, null), 100);
        assertTrue(window.isStreaming());
        assertEquals(1, window.getStart());
        assertEquals(100, window.getSize());
    }

    @Test
    void noPagingOptionsAndNoConfiguredSizeUseTheFallback() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, null, null), null);
        assertEquals(SCIMSearchWindow.FALLBACK_PAGE_SIZE, window.getSize());
    }

    @Test
    void anOffsetAloneIsAPagedCallFromThatRow() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(501, null, null), 100);
        assertFalse(window.isStreaming());
        assertEquals(501, window.getStart());
        assertEquals(100, window.getSize());
    }

    @Test
    void theCallersPageSizeWinsOverTheConfiguredOne() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(1, null, 25), 100);
        assertFalse(window.isStreaming());
        assertEquals(25, window.getSize());
    }

    @Test
    void theCookieIsTheNextIndexAndResumesThere() {
        SCIMSearchWindow first = SCIMSearchWindow.of(options(null, null, 100), 100);
        assertEquals("101", first.nextCookie(100));
        SCIMSearchWindow second = SCIMSearchWindow.of(options(null, "101", 100), 100);
        assertEquals(101, second.getStart());
        assertEquals("201", second.nextCookie(100));
    }

    @Test
    void aShortPageEndsThePagingWithoutACookie() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(null, "901", 100), 100);
        assertNull(window.nextCookie(37));
    }

    @Test
    void anOffsetWinsOverACookie() {
        SCIMSearchWindow window = SCIMSearchWindow.of(options(50, "901", 100), 100);
        assertEquals(50, window.getStart());
    }
}
