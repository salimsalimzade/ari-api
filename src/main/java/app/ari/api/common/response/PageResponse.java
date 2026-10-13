package app.ari.api.common.response;

import java.util.List;

/**
 * One page of a list. {@code nextCursor} is opaque: send it back as {@code cursor} to get the next
 * page. It is left out on the last page.
 */
public record PageResponse<T>(List<T> items, String nextCursor) {}
