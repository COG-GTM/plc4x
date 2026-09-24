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
package org.apache.plc4x.java.legacyarchive.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reader for the one-page PDF maintenance report of the legacy archive.
 * <p>
 * The report body carries one labelled line per field:
 * <pre>
 * Asset ID:     PUMP-014
 * Last Service: 2026-03-17
 * Technician:   A. Turing
 * Next Due:     2026-09-17
 * Hours Run:    18234.5
 * </pre>
 * The text is extracted with Apache PDFBox and each label is matched line-wise, so additional
 * headings, footers or field order changes don't affect the result. A missing or unparseable
 * field is an error — an absent value would otherwise be indistinguishable from a real one.
 */
public class MaintenanceReportParser {

    public static final String MAINTENANCE_REPORT_FILE_NAME = "maintenance-report.pdf";

    private static final String ASSET_ID_LABEL = "Asset ID";
    private static final String LAST_SERVICE_LABEL = "Last Service";
    private static final String TECHNICIAN_LABEL = "Technician";
    private static final String NEXT_DUE_LABEL = "Next Due";
    private static final String HOURS_RUN_LABEL = "Hours Run";

    public MaintenanceReport parse(Path file) throws IOException, LegacyArchiveFormatException {
        String text;
        try (PDDocument document = Loader.loadPDF(file.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            text = stripper.getText(document);
        }
        String fileName = file.getFileName().toString();
        return new MaintenanceReport(
            extract(text, ASSET_ID_LABEL, fileName),
            parseDate(extract(text, LAST_SERVICE_LABEL, fileName), LAST_SERVICE_LABEL, fileName),
            extract(text, TECHNICIAN_LABEL, fileName),
            parseDate(extract(text, NEXT_DUE_LABEL, fileName), NEXT_DUE_LABEL, fileName),
            parseDouble(extract(text, HOURS_RUN_LABEL, fileName), HOURS_RUN_LABEL, fileName));
    }

    /**
     * Matches the label and its value on one and the same line: only horizontal whitespace is
     * allowed around the colon, so a label left blank in the report fails here instead of
     * swallowing the next labelled line as its value.
     */
    private String extract(String text, String label, String fileName) throws LegacyArchiveFormatException {
        Pattern pattern = Pattern.compile("^[ \\t]*" + Pattern.quote(label) + "[ \\t]*:[ \\t]*(\\S.*?)[ \\t]*$",
            Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new LegacyArchiveFormatException(fileName + ": no '" + label + ":' line with a value found"
                + " in the report");
        }
        return matcher.group(1);
    }

    private LocalDate parseDate(String value, String label, String fileName) throws LegacyArchiveFormatException {
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw new LegacyArchiveFormatException(fileName + ": '" + label + "' value '" + value
                + "' is not an ISO date (yyyy-MM-dd)", e);
        }
    }

    private double parseDouble(String value, String label, String fileName) throws LegacyArchiveFormatException {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new LegacyArchiveFormatException(fileName + ": '" + label + "' value '" + value
                + "' is not a number", e);
        }
    }

}
