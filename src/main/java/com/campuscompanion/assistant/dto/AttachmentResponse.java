package com.campuscompanion.assistant.dto;

/**
 * What the server really read from an attached file. {@code kind} is "image", "pdf" or "docx".
 * {@code pages} is the number of pages of a PDF (0 for other files) and {@code pagesRead} how many
 * were read. {@code characters} is the amount of text read, {@code pictures} how many pictures will
 * go to the model (a photo, scanned pages, figures), {@code notice} something to tell the student
 * about how much was read (or null), and {@code preview} the first words of the text (or null).
 */
public record AttachmentResponse(
    String id,
    String name,
    String kind,
    int pages,
    int pagesRead,
    int characters,
    int pictures,
    String notice,
    String preview) {}
