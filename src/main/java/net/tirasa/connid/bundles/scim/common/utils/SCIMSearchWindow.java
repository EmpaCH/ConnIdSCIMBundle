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

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import org.identityconnectors.framework.common.objects.OperationOptions;

/**
 * The window a ConnId search asks for, derived from the caller's paging options. Immutable: a window never changes,
 * and each step returns the next one.
 * <ul>
 * <li>start: the 1-based offset the caller sent, else the cookie (the next index) it sent back, else 1;</li>
 * <li>size: the page size the caller sent, else the configured default, else {@link #FALLBACK_PAGE_SIZE};</li>
 * <li>streaming: true only when the caller sent no paging options at all. Then {@link #read} fetches every page,
 * so the caller gets every result, as ConnId requires for an unpaged search.</li>
 * </ul>
 * The cookie is the next start index, so the caller needs no state of its own between calls.
 */
public final class SCIMSearchWindow {

    /** Used when neither the caller nor the configuration gives a page size. Always asked for explicitly. */
    public static final int FALLBACK_PAGE_SIZE = 100;

    /** Fetches one page: the rows starting at a 1-based index, at most {@code size} of them. */
    @FunctionalInterface
    public interface PageReader<T> {
        List<T> read(int start, int size);
    }

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
        boolean hasCookie = cookie != null && !cookie.isBlank();

        int defaultSize = configuredPageSize == null || configuredPageSize <= 0
                ? FALLBACK_PAGE_SIZE
                : configuredPageSize;
        int size = requestedSize != null && requestedSize > 0 ? requestedSize : defaultSize;
        boolean streaming = offset == null && requestedSize == null && !hasCookie;

        int start;
        if (offset != null) {
            start = offset;
        } else if (hasCookie) {
            start = Integer.parseInt(cookie.trim());
        } else {
            start = 1;
        }
        return new SCIMSearchWindow(streaming, start, size);
    }

    /**
     * Reads the window's rows, passing each to the handler, and returns the cookie for the caller's next call.
     * A streaming window reads every page and returns no cookie. A paged window reads one page.
     *
     * @param window the window the caller asked for
     * @param reader fetches one page
     * @param handler receives each row
     * @return the cookie for the next page, if the caller is paging and more rows may follow
     */
    public static <T> Optional<String> read(
            final SCIMSearchWindow window,
            final PageReader<T> reader,
            final Consumer<T> handler) {

        Optional<SCIMSearchWindow> page = Optional.of(window);
        Optional<String> cookie = Optional.empty();
        while (page.isPresent()) {
            SCIMSearchWindow current = page.get();
            List<T> rows = reader.read(current.start, current.size);
            rows.forEach(handler);
            cookie = current.cookieAfter(rows.size());
            page = window.streaming ? current.pageAfter(rows.size()) : Optional.empty();
        }
        return cookie;
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
     * The window that follows this one, or nothing when the search is over. The search ends on an empty page, not a
     * short one: a server may cap its pages below the size asked for, and a short page would then end it early.
     */
    public Optional<SCIMSearchWindow> pageAfter(final int returned) {
        return returned > 0
                ? Optional.of(new SCIMSearchWindow(streaming, start + returned, size))
                : Optional.empty();
    }

    /** The cookie for the caller's next call, when it is paging and rows came back. An empty next page ends it. */
    public Optional<String> cookieAfter(final int returned) {
        return !streaming && returned > 0
                ? Optional.of(String.valueOf(start + returned))
                : Optional.empty();
    }
}
