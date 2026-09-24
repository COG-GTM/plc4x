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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLogParserTest {

    private static final long BASE = Instant.parse("2026-03-17T08:00:00Z").toEpochMilli();

    private final EventLogParser parser = new EventLogParser();

    @Test
    void parsesAllRecordsOfTheFixture() throws Exception {
        List<EventRecord> records = parser.parse(FixturePaths.resource("events.bin"));

        assertEquals(6, records.size());
        assertRecord(records.get(0), BASE, 1, EventSeverity.INFO, EventQuality.GOOD, 12.5);
        assertRecord(records.get(1), BASE + 1_000, 2, EventSeverity.WARN, EventQuality.UNCERTAIN, 77.25);
        assertRecord(records.get(2), BASE + 2_000, 3, EventSeverity.ALARM, EventQuality.BAD, -3.5);
        assertRecord(records.get(3), BASE + 3_000, 4, EventSeverity.FAULT, EventQuality.GOOD, 0.0);
        assertRecord(records.get(4), BASE + 4_000, 1, EventSeverity.WARN, EventQuality.GOOD, 13.75);
        assertRecord(records.get(5), BASE + 5_000, 5, EventSeverity.INFO, EventQuality.GOOD, 1000.125);
    }

    @Test
    void exposesTheTimestampAsUtcLocalDateTime() throws Exception {
        List<EventRecord> records = parser.parse(FixturePaths.resource("events.bin"));

        assertEquals(LocalDateTime.ofInstant(Instant.ofEpochMilli(BASE), ZoneOffset.UTC),
            records.get(0).timestamp());
    }

    @Test
    void rejectsWrongMagic() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("events-bad-magic.bin")));

        assertTrue(e.getMessage().contains("invalid magic"), e.getMessage());
    }

    @Test
    void rejectsUnknownVersion() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("events-bad-version.bin")));

        assertTrue(e.getMessage().contains("unsupported version 9"), e.getMessage());
    }

    @Test
    void rejectsRecordCountDisagreeingWithTheFileLength() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("events-count-mismatch.bin")));

        assertTrue(e.getMessage().contains("announces 6 records but the file holds 5"), e.getMessage());
    }

    @Test
    void rejectsNonZeroReservedField() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("events-bad-reserved.bin")));

        assertTrue(e.getMessage().contains("non-zero reserved field"), e.getMessage());
    }

    @Test
    void rejectsTruncatedTrailingRecord() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("events-truncated.bin")));

        assertTrue(e.getMessage().contains("truncated trailing record"), e.getMessage());
    }

    @Test
    void rejectsFileTooShortForTheHeader() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(new byte[]{'E', 'V', 'L', 'G'}, "events.bin"));

        assertTrue(e.getMessage().contains("too short"), e.getMessage());
    }

    @Test
    void namesTheOffendingFileInTheMessage() {
        LegacyArchiveFormatException e = assertThrows(LegacyArchiveFormatException.class,
            () -> parser.parse(FixturePaths.resource("events-bad-magic.bin")));

        assertTrue(e.getMessage().startsWith("events-bad-magic.bin:"), e.getMessage());
    }

    private static void assertRecord(EventRecord record, long timestampMillis, long tagId,
                                     EventSeverity severity, EventQuality quality, double value) {
        assertEquals(timestampMillis, record.timestampMillis());
        assertEquals(tagId, record.tagId());
        assertEquals(severity, record.severity());
        assertEquals(quality, record.quality());
        assertEquals(value, record.value(), 0.0);
    }

}
