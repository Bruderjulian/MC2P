package dev.mc2p.plugin.http;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.IOException;

/**
 * Caps the request body size for endpoints without a reliable Content-Length.
 * Used by the
 * MCP auth filter so oversized payloads are rejected before dispatch.
 */
final class LimitedHttpServletRequest extends HttpServletRequestWrapper {

    private final int limit;

    LimitedHttpServletRequest(final HttpServletRequest request, final int limit) {
        super(request);
        this.limit = limit;
    }

    @Override
    public int getContentLength() {
        return Math.min(super.getContentLength(), limit);
    }

    @Override
    public long getContentLengthLong() {
        return Math.min(super.getContentLengthLong(), limit);
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
        final ServletInputStream in = super.getInputStream();
        return new ServletInputStream() {
            private int remaining = limit;

            @Override
            public int read() throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                final int r = in.read();
                if (r != -1) {
                    remaining--;
                }
                return r;
            }

            @Override
            public int read(final byte[] b, final int off, final int len) throws IOException {
                if (remaining <= 0) {
                    return -1;
                }
                final int toRead = Math.min(len, remaining);
                final int r = in.read(b, off, toRead);
                if (r > 0) {
                    remaining -= r;
                }
                return r;
            }

            @Override
            public boolean isFinished() {
                return remaining <= 0 || in.isFinished();
            }

            @Override
            public boolean isReady() {
                return in.isReady();
            }

            @Override
            public void setReadListener(final ReadListener readListener) {
                in.setReadListener(readListener);
            }
        };
    }
}
