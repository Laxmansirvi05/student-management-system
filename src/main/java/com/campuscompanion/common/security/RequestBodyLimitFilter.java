package com.campuscompanion.common.security;

import com.campuscompanion.common.config.ApiProtectionProperties;
import com.campuscompanion.common.error.ApiException;
import com.campuscompanion.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Refuses an API request body over 64 KB (docs/09-api-contract.md section 6). No request of this
 * API needs more: the longest text field holds 12,000 characters. File uploads are multipart and
 * have their own limits, so they are not counted here.
 *
 * <p>The one exception is writing a note, which carries the whole document (up to 200,000
 * characters of HTML) and may be 1 MB (ADR 0014). It is a logged-in student's own write, and it is
 * still counted by the session's request limit.
 *
 * <p>A body that announces its length is refused before any of it is read. A body sent in chunks,
 * without a length, is cut off as soon as it passes the limit.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodyLimitFilter extends OncePerRequestFilter {

  static final long NOTE_BODY_BYTES = 1024 * 1024;
  private static final Pattern NOTE_WRITE = Pattern.compile("^/api/v1/notes(/\\d{1,18})?$");

  private final long maxBytes;
  private final ApiSecurityErrorHandler errorHandler;

  public RequestBodyLimitFilter(
      ApiProtectionProperties limits, ApiSecurityErrorHandler errorHandler) {
    this.maxBytes = limits.maxJsonBody().toBytes();
    this.errorHandler = errorHandler;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    String type = request.getContentType();
    boolean multipart =
        type != null
            && type.toLowerCase(Locale.ROOT).startsWith(MediaType.MULTIPART_FORM_DATA_VALUE);
    return !request.getRequestURI().startsWith("/api/") || multipart;
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    long limit = limitFor(request);
    if (request.getContentLengthLong() > limit) {
      errorHandler.write(
          request,
          response,
          new ApiException(
              ErrorCode.REQUEST_TOO_LARGE,
              "The request is larger than " + (limit / 1024) + " KB."));
      return;
    }
    chain.doFilter(new LimitedBodyRequest(request, limit), response);
  }

  /** The limit for this request: larger only for creating or changing a note. */
  private long limitFor(HttpServletRequest request) {
    String method = request.getMethod();
    boolean write = "POST".equals(method) || "PATCH".equals(method) || "PUT".equals(method);
    return write && NOTE_WRITE.matcher(request.getRequestURI()).matches()
        ? Math.max(maxBytes, NOTE_BODY_BYTES)
        : maxBytes;
  }

  /** The request with a body that stops with an error once more than the limit has been read. */
  private static final class LimitedBodyRequest extends HttpServletRequestWrapper {

    private final long maxBytes;
    private ServletInputStream limited;

    LimitedBodyRequest(HttpServletRequest request, long maxBytes) {
      super(request);
      this.maxBytes = maxBytes;
    }

    @Override
    public ServletInputStream getInputStream() throws IOException {
      if (limited == null) {
        limited = new LimitedInputStream(super.getInputStream(), maxBytes);
      }
      return limited;
    }
  }

  private static final class LimitedInputStream extends ServletInputStream {

    private final ServletInputStream body;
    private final long maxBytes;
    private long read;

    LimitedInputStream(ServletInputStream body, long maxBytes) {
      this.body = body;
      this.maxBytes = maxBytes;
    }

    @Override
    public int read() throws IOException {
      int next = body.read();
      if (next >= 0) {
        count(1);
      }
      return next;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
      int count = body.read(buffer, offset, length);
      if (count > 0) {
        count(count);
      }
      return count;
    }

    private void count(int bytes) throws IOException {
      read += bytes;
      if (read > maxBytes) {
        throw new IOException("The request body is over the limit");
      }
    }

    @Override
    public boolean isFinished() {
      return body.isFinished();
    }

    @Override
    public boolean isReady() {
      return body.isReady();
    }

    @Override
    public void setReadListener(ReadListener listener) {
      body.setReadListener(listener);
    }
  }
}
