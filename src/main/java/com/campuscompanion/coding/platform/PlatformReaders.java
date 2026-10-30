package com.campuscompanion.coding.platform;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * One reader per coding site. Tests replace this bean with fake readers, so no test goes online.
 */
public class PlatformReaders {

  private final Map<CodingPlatform, PlatformReader> readers = new EnumMap<>(CodingPlatform.class);

  public PlatformReaders(List<PlatformReader> all) {
    for (PlatformReader reader : all) {
      readers.put(reader.platform(), reader);
    }
    if (readers.size() != CodingPlatform.values().length) {
      throw new IllegalStateException("Every coding site needs exactly one reader.");
    }
  }

  public PlatformReader of(CodingPlatform platform) {
    return readers.get(platform);
  }
}
