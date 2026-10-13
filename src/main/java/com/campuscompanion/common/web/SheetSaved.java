package com.campuscompanion.common.web;

/** The result of saving a sheet: how many rows it held, and how many of them changed. */
public record SheetSaved(int saved, int changed) {}
