package com.campuscompanion.ai.parse;

/** Lets tests in other packages reach the package-private escaping. */
public final class FlowchartRendererTestAccess {
  private FlowchartRendererTestAccess() {}

  public static String escape(String label) {
    return FlowchartRenderer.escape(label);
  }
}
