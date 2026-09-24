/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.plc4x.java.legacyarchive.fixtures;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Writes the test fixtures under {@code src/test/resources}. The generated files are committed,
 * so this class is a tool rather than a test — see {@code src/test/resources/README.adoc} for
 * how to run it.
 *
 * <p>Everything it writes is fixed: timestamps are derived from a constant epoch, and the PDF's
 * creation date and document id are pinned, so re-running it reproduces the committed bytes.</p>
 */
public final class FixtureGenerator {

    /** 2026-03-17T08:00:00Z — the base timestamp all event records are offset from. */
    static final long BASE_TIMESTAMP_MILLIS = Instant.parse("2026-03-17T08:00:00Z").toEpochMilli();

    static final String ASSET_ID = "PUMP-014";
    static final String LAST_SERVICE = "2026-03-17";
    static final String TECHNICIAN = "A. Turing";
    static final String NEXT_DUE = "2026-09-17";
    static final double HOURS_RUN = 18234.5;

    private static final int HEADER_LENGTH = 8;
    private static final int RECORD_LENGTH = 24;

    /** The six records of the well-formed event log, in file order. */
    static final List<Record> RECORDS = List.of(
        new Record(BASE_TIMESTAMP_MILLIS, 1, 0, 2, 12.5),
        new Record(BASE_TIMESTAMP_MILLIS + 1_000, 2, 1, 1, 77.25),
        new Record(BASE_TIMESTAMP_MILLIS + 2_000, 3, 2, 0, -3.5),
        new Record(BASE_TIMESTAMP_MILLIS + 3_000, 4, 3, 2, 0.0),
        new Record(BASE_TIMESTAMP_MILLIS + 4_000, 1, 1, 2, 13.75),
        new Record(BASE_TIMESTAMP_MILLIS + 5_000, 5, 0, 2, 1000.125));

    record Record(long timestampMillis, long tagId, int severity, int quality, double value) {
    }

    private FixtureGenerator() {
    }

    public static void main(String[] args) throws IOException {
        Path targetDirectory = Paths.get((args.length > 0) ? args[0] : "src/test/resources");
        Files.createDirectories(targetDirectory);

        byte[] eventLog = eventLog(RECORDS.size(), RECORDS);
        Files.write(targetDirectory.resolve("events.bin"), eventLog);
        Files.write(targetDirectory.resolve("events-bad-magic.bin"), badMagic(eventLog));
        Files.write(targetDirectory.resolve("events-bad-version.bin"), badVersion(eventLog));
        Files.write(targetDirectory.resolve("events-truncated.bin"), truncated(eventLog));
        Files.write(targetDirectory.resolve("events-count-mismatch.bin"), countMismatch());
        Files.write(targetDirectory.resolve("events-bad-reserved.bin"), badReserved());
        writeMaintenanceReport(targetDirectory.resolve("maintenance-report.pdf"), Variant.COMPLETE);
        writeMaintenanceReport(targetDirectory.resolve("maintenance-report-empty.pdf"), Variant.NO_FIELDS);
        writeMaintenanceReport(targetDirectory.resolve("maintenance-report-blank-asset-id.pdf"),
            Variant.BLANK_ASSET_ID);

        System.out.println("Fixtures written to " + targetDirectory.toAbsolutePath());
    }

    static byte[] eventLog(int announcedRecordCount, List<Record> records) {
        return eventLog(announcedRecordCount, records, 0);
    }

    private static byte[] eventLog(int announcedRecordCount, List<Record> records, int reserved) {
        ByteBuffer buffer = ByteBuffer
            .allocate(HEADER_LENGTH + (records.size() * RECORD_LENGTH))
            .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("EVLG".getBytes(StandardCharsets.US_ASCII));
        buffer.putShort((short) 1);
        buffer.putShort((short) announcedRecordCount);
        for (Record record : records) {
            buffer.putLong(record.timestampMillis());
            buffer.putInt((int) record.tagId());
            buffer.put((byte) record.severity());
            buffer.put((byte) record.quality());
            buffer.putShort((short) reserved);
            buffer.putDouble(record.value());
        }
        return buffer.array();
    }

    private static byte[] badMagic(byte[] eventLog) {
        byte[] copy = eventLog.clone();
        copy[0] = 'X';
        return copy;
    }

    private static byte[] badVersion(byte[] eventLog) {
        byte[] copy = eventLog.clone();
        copy[4] = 9;
        return copy;
    }

    /** The last record loses its final eight bytes, so the body is no longer a multiple of 24. */
    private static byte[] truncated(byte[] eventLog) {
        byte[] copy = new byte[eventLog.length - 8];
        System.arraycopy(eventLog, 0, copy, 0, copy.length);
        return copy;
    }

    /** Header announces all six records while only five are present. */
    private static byte[] countMismatch() {
        return eventLog(RECORDS.size(), RECORDS.subList(0, RECORDS.size() - 1));
    }

    /** Well-formed apart from the reserved field of every record being non-zero. */
    private static byte[] badReserved() {
        return eventLog(RECORDS.size(), RECORDS, 1);
    }

    /** Which report body to write: all five fields, none of them, or an 'Asset ID:' left blank. */
    enum Variant {
        COMPLETE,
        NO_FIELDS,
        BLANK_ASSET_ID
    }

    private static void writeMaintenanceReport(Path file, Variant variant) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(font, 12);
                content.setLeading(18);
                content.newLineAtOffset(60, 760);
                content.showText("Legacy Controller Maintenance Report");
                content.newLine();
                content.newLine();
                if (variant != Variant.NO_FIELDS) {
                    content.showText((variant == Variant.BLANK_ASSET_ID)
                        ? "Asset ID:" : ("Asset ID: " + ASSET_ID));
                    content.newLine();
                    content.showText("Last Service: " + LAST_SERVICE);
                    content.newLine();
                    content.showText("Technician: " + TECHNICIAN);
                    content.newLine();
                    content.showText("Next Due: " + NEXT_DUE);
                    content.newLine();
                    content.showText("Hours Run: " + HOURS_RUN);
                } else {
                    content.showText("This report carries none of the labelled fields.");
                }
                content.endText();
            }
            // Pin everything PDFBox would otherwise derive from the current time, so the
            // generated bytes stay identical between runs.
            Calendar creationDate = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            creationDate.setTimeInMillis(BASE_TIMESTAMP_MILLIS);
            document.getDocumentInformation().setCreationDate(creationDate);
            document.getDocumentInformation().setModificationDate(creationDate);
            document.getDocumentInformation().setProducer("PLC4X legacy-archive fixture generator");
            document.getDocumentInformation().setTitle("Maintenance Report " + ASSET_ID);
            document.save(file.toFile());
        }
    }

}
