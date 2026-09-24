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

import org.apache.plc4x.java.legacyarchive.FixturePaths;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MaintenanceReportParserTest {

    private final MaintenanceReportParser parser = new MaintenanceReportParser();

    @Test
    void extractsAllFiveFields() throws Exception {
        MaintenanceReport report = parser.parse(FixturePaths.resource("maintenance-report.pdf"));

        assertEquals("PUMP-014", report.assetId());
        assertEquals(LocalDate.of(2026, 3, 17), report.lastService());
        assertEquals("A. Turing", report.technician());
        assertEquals(LocalDate.of(2026, 9, 17), report.nextDue());
        assertEquals(18234.5, report.hoursRun(), 0.0);
    }

    @Test
    void rejectsAPdfWithoutTheLabelledLines() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("maintenance-report-empty.pdf")));

        assertTrue(e.getMessage().contains("no 'Asset ID:' line"), e.getMessage());
    }

    @Test
    void rejectsALabelLeftBlankInsteadOfTakingTheNextLineAsItsValue() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("maintenance-report-blank-asset-id.pdf")));

        assertTrue(e.getMessage().contains("no 'Asset ID:' line"), e.getMessage());
    }

    @Test
    void rejectsANonFiniteHoursRunValue() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("maintenance-report-nan-hours.pdf")));

        assertTrue(e.getMessage().contains("'Hours Run' value 'NaN' is not a finite number"), e.getMessage());
    }

}
