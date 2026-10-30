package com.campuscompanion.coding;

import com.campuscompanion.coding.platform.PlatformDetails;
import com.campuscompanion.coding.platform.PlatformDetails.Badge;
import com.campuscompanion.coding.platform.PlatformException;
import com.campuscompanion.coding.platform.PlatformHttp;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Keeps this application's copy of the badge pictures a read names (ADR 0017, addendum 2). A
 * picture is downloaded only from a fixed list of the sites' image hosts, over HTTPS, at most once
 * per address; it must be a small PNG, GIF, WebP, JPEG or a plain SVG with no script, event
 * handler, embedded page or outside link. A picture that fails any of this is left out, and the
 * badge is drawn without it. It runs outside a transaction; each copy is kept in its own.
 */
@Component
public class BadgePictures {

  private static final Logger log = LoggerFactory.getLogger(BadgePictures.class);

  static final Set<String> HOSTS =
      Set.of("leetcode.com", "assets.leetcode.com", "cdn.codechef.com");
  static final int MAX_BYTES = 1_500_000;

  /** New pictures fetched per read, so one read stays short. */
  private static final int MAX_NEW_PER_READ = 12;

  private static final Pattern PATH =
      Pattern.compile("^/[A-Za-z0-9_./-]{1,200}\\.(?:png|gif|webp|jpe?g|svg)$");
  private static final Pattern UNSAFE_SVG =
      Pattern.compile(
          "<script|<foreignobject|<iframe|<object|<embed|\\son[a-z]+\\s*=|javascript:|href\\s*=\\s*['\"]\\s*(?!#|data:image/(?:png|jpeg|gif|webp);)|@import|url\\(\\s*['\"]?\\s*(?!#)",
          Pattern.CASE_INSENSITIVE);

  private final PlatformHttp http;
  private final CodingBadgeImageRepository images;
  private final TransactionTemplate transactions;

  public BadgePictures(
      PlatformHttp http, CodingBadgeImageRepository images, TransactionTemplate transactions) {
    this.http = http;
    this.images = images;
    this.transactions = transactions;
  }

  /** The details with each badge's picture key filled in where a copy is kept or could be made. */
  public PlatformDetails attach(PlatformDetails details) {
    List<Badge> badges = new ArrayList<>();
    int fetched = 0;
    for (Badge badge : details.badges()) {
      String source = allowedSource(badge.source());
      if (source == null) {
        badges.add(badge.withImage(null));
        continue;
      }
      String key = keyOf(source);
      boolean kept = Boolean.TRUE.equals(transactions.execute(status -> images.existsById(key)));
      if (!kept && fetched < MAX_NEW_PER_READ) {
        fetched++;
        kept = copy(key, source);
      }
      badges.add(badge.withImage(kept ? key : null));
    }
    return details.withBadges(badges);
  }

  private boolean copy(String key, String source) {
    PlatformHttp.Image image;
    try {
      image = http.getImage(source, MAX_BYTES);
    } catch (PlatformException unavailable) {
      return false;
    }
    String type = typeOf(image.bytes());
    if (type == null) {
      log.info("Badge picture refused: not an allowed image ({})", source);
      return false;
    }
    try {
      transactions.executeWithoutResult(
          status -> images.saveAndFlush(new CodingBadgeImage(key, source, type, image.bytes())));
    } catch (DataIntegrityViolationException alreadyKept) {
      // Another read kept the same picture at the same moment: the copy exists either way.
      return images.existsById(key);
    }
    return true;
  }

  /** The address when it is an HTTPS address on an allowed host with a plain picture path. */
  static String allowedSource(String source) {
    if (source == null || source.length() > 300) {
      return null;
    }
    try {
      URI uri = new URI(source);
      boolean ok =
          "https".equals(uri.getScheme())
              && uri.getHost() != null
              && HOSTS.contains(uri.getHost().toLowerCase(Locale.ROOT))
              && uri.getPort() == -1
              && uri.getRawQuery() == null
              && uri.getRawFragment() == null
              && uri.getRawUserInfo() == null
              && uri.getRawPath() != null
              && PATH.matcher(uri.getRawPath()).matches()
              && !uri.getRawPath().contains("..");
      return ok ? source : null;
    } catch (java.net.URISyntaxException notAnAddress) {
      return null;
    }
  }

  /** The picture's real type from its first bytes, or null when it is not one this keeps. */
  static String typeOf(byte[] data) {
    if (data.length == 0) {
      return null;
    }
    if (starts(data, 0x89, 'P', 'N', 'G')) {
      return "image/png";
    }
    if (starts(data, 'G', 'I', 'F', '8')) {
      return "image/gif";
    }
    if (starts(data, 0xFF, 0xD8, 0xFF)) {
      return "image/jpeg";
    }
    if (data.length > 12
        && starts(data, 'R', 'I', 'F', 'F')
        && data[8] == 'W'
        && data[9] == 'E'
        && data[10] == 'B'
        && data[11] == 'P') {
      return "image/webp";
    }
    String text = new String(data, StandardCharsets.UTF_8).strip();
    boolean svg =
        (text.startsWith("<svg") || text.startsWith("<?xml"))
            && text.contains("<svg")
            && text.endsWith("</svg>");
    return svg && !UNSAFE_SVG.matcher(text).find() ? "image/svg+xml" : null;
  }

  private static boolean starts(byte[] data, int... prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if ((data[i] & 0xFF) != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  static String keyOf(String source) {
    try {
      MessageDigest sha = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(sha.digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException("SHA-256 is always available", impossible);
    }
  }
}
