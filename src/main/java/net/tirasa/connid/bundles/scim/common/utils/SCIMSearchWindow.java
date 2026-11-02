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

import org.identityconnectors.framework.common.objects.OperationOptions;

/**
 * The window a ConnId search asks for, derived from the caller's paging options.
 * <ul>
 * <li>start: the 1-based offset the caller sent, else the cookie (the next index) it sent back, else 1;</li>
 * <li>size: the page size the caller sent, else the configured default, else {@link #FALLBACK_PAGE_SIZE};</li>
 * <li>streaming: true only when the caller sent no paging options at all. The connector then loops over all
 * pages itself, so the caller gets every result, as ConnId requires for an unpaged search.</li>
 * </ul>
 * The cookie is the next start index, so the next call needs no state of its own.
 */
public final class SCIMSearchWindow {

    /** Used when neither the caller nor the configuration gives a page size. Always asked for explicitly. */
    public static final int FALLBACK_PAGE_SIZE = 100;

    private final boolean streaming;

    private final int start;

    private final int size;

    private SCIMSearchWindow(final boolean streaming, final int start, final int size) {
        this.streaming = streaming;
        this.start = start;
        this.size = size;
    }

    public static SCIMSearchWindow of(final OperationOptions options, final Integer configuredPageSize) {
        Integer offset = options.getPagedResultsOffset();
        String cookie = options.getPagedResultsCookie();
        Integer requestedSize = options.getPageSize();

        int defaultSize = configuredPageSize == null || configuredPageSize <= 0
                ? FALLBACK_PAGE_SIZE
                : configuredPageSize;
        int size = requestedSize != null && requestedSize > 0 ? requestedSize : defaultSize;

        boolean streaming = offset == null && requestedSize == null && (cookie == null || cookie.isBlank());

        int start;
        if (offset != null) {
            start = offset;
        } else if (cookie != null && !cookie.isBlank()) {
            start = Integer.parseInt(cookie.trim());
        } else {
            start = 1;
        }
        return new SCIMSearchWindow(streaming, start, size);
    }

    public boolean isStreaming() {
        return streaming;
    }

    public int getStart() {
        return start;
    }

    public int getSize() {
        return size;
    }

    /**
     * The cookie for the next page, or null when the page came back short and the search is complete.
     *
     * @param returned the number of results in the page just read
     * @return the next start index as a cookie, or null
     */
    public String nextCookie(final int returned) {
        return returned >= size ? String.valueOf(start + returned) : null;
    }
}
