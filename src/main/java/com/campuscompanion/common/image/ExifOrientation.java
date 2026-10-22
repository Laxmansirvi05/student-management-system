package com.campuscompanion.common.image;

/**
 * Reads the EXIF orientation of a JPEG: how a phone says a picture should be turned. Shared by the
 * profile photo and the pictures in notes. The bytes come from a stranger, so every position is
 * checked before it is read, and anything unexpected simply means "no turn".
 */
public final class ExifOrientation {

  private ExifOrientation() {}

  /**
   * How a JPEG says it should be turned: the value of its EXIF orientation tag, from 1 to 8, or 1
   * when it has none. The bytes come from a stranger, so every position is checked before it is
   * read, and anything unexpected simply means "no turn".
   */
  public static int of(byte[] jpeg) {
    int position = 2;
    while (position + 4 <= jpeg.length && (jpeg[position] & 0xFF) == 0xFF) {
      int marker = jpeg[position + 1] & 0xFF;
      int length = ((jpeg[position + 2] & 0xFF) << 8) | (jpeg[position + 3] & 0xFF);
      if (marker == 0xDA || length < 2) {
        break;
      }
      int end = position + 2 + length;
      if (marker == 0xE1 && end <= jpeg.length && isExif(jpeg, position + 4)) {
        return tagIn(jpeg, position + 10, end);
      }
      position = end;
    }
    return 1;
  }

  private static boolean isExif(byte[] data, int at) {
    return at + 6 <= data.length
        && data[at] == 'E'
        && data[at + 1] == 'x'
        && data[at + 2] == 'i'
        && data[at + 3] == 'f'
        && data[at + 4] == 0
        && data[at + 5] == 0;
  }

  /** Looks for tag 0x0112 in the first directory of the EXIF block between start and end. */
  private static int tagIn(byte[] data, int start, int end) {
    if (start + 8 > end) {
      return 1;
    }
    boolean littleEndian = data[start] == 'I' && data[start + 1] == 'I';
    boolean bigEndian = data[start] == 'M' && data[start + 1] == 'M';
    if (!littleEndian && !bigEndian) {
      return 1;
    }
    long offset = number(data, start + 4, 4, littleEndian);
    if (offset < 8 || offset > end - start - 2) {
      return 1;
    }
    int directory = start + (int) offset;
    int entries = (int) number(data, directory, 2, littleEndian);
    for (int i = 0; i < entries; i++) {
      int entry = directory + 2 + 12 * i;
      if (entry + 12 > end) {
        break;
      }
      if (number(data, entry, 2, littleEndian) == 0x0112) {
        long value = number(data, entry + 8, 2, littleEndian);
        return value >= 1 && value <= 8 ? (int) value : 1;
      }
    }
    return 1;
  }

  private static long number(byte[] data, int at, int bytes, boolean littleEndian) {
    long value = 0;
    for (int i = 0; i < bytes; i++) {
      int next = data[littleEndian ? at + bytes - 1 - i : at + i] & 0xFF;
      value = (value << 8) | next;
    }
    return value;
  }
}
