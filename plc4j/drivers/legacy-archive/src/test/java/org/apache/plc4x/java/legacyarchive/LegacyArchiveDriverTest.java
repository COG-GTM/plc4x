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
package org.apache.plc4x.java.legacyarchive;

import org.apache.plc4x.java.api.PlcConnection;
import org.apache.plc4x.java.api.PlcDriver;
import org.apache.plc4x.java.api.PlcDriverManager;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.types.PlcValueType;
import org.apache.plc4x.java.api.value.PlcValue;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyArchiveDriverTest {

    private static final long BASE = Instant.parse("2026-03-17T08:00:00Z").toEpochMilli();

    @Test
    void driverManagerResolvesTheProtocolCodeThroughTheServiceLoader() throws Exception {
        PlcDriverManager driverManager = PlcDriverManager.getDefault();

        assertTrue(driverManager.getProtocolCodes().contains("legacy-archive"));
        PlcDriver driver = driverManager.getDriver("legacy-archive");
        assertInstanceOf(LegacyArchiveDriver.class, driver);
        assertEquals("Legacy Controller Archive", driver.getProtocolName());
    }

    @Test
    void readsEventAndMaintenanceTagsInOneRequest() throws Exception {
        try (PlcConnection connection = openFixtureArchive("")) {
            assertTrue(connection.isConnected());
            assertTrue(connection.getMetadata().isReadSupported());

            PlcReadRequest request = connection.readRequestBuilder()
                .addTagAddress("value", "event/1/value")
                .addTagAddress("severity", "event/1/severity")
                .addTagAddress("quality", "event/3/quality")
                .addTagAddress("timestamp", "event/5/timestamp")
                .addTagAddress("assetId", "maintenance/assetId")
                .addTagAddress("lastService", "maintenance/lastService")
                .addTagAddress("technician", "maintenance/technician")
                .addTagAddress("nextDue", "maintenance/nextDue")
                .addTagAddress("hoursRun", "maintenance/hoursRun")
                .build();

            PlcReadResponse response = request.execute().get();

            // The event log holds two records for tag id 1; the most recent one wins.
            assertValue(response, "value", PlcValueType.LREAL, 13.75);
            assertValue(response, "severity", PlcValueType.USINT, (short) 1);
            assertValue(response, "quality", PlcValueType.USINT, (short) 0);
            assertValue(response, "timestamp", PlcValueType.DATE_AND_TIME,
                LocalDateTime.ofInstant(Instant.ofEpochMilli(BASE + 5_000), ZoneOffset.UTC));
            assertValue(response, "assetId", PlcValueType.STRING, "PUMP-014");
            assertValue(response, "lastService", PlcValueType.DATE, LocalDate.of(2026, 3, 17));
            assertValue(response, "technician", PlcValueType.STRING, "A. Turing");
            assertValue(response, "nextDue", PlcValueType.DATE, LocalDate.of(2026, 9, 17));
            assertValue(response, "hoursRun", PlcValueType.LREAL, 18234.5);
        }
    }

    @Test
    void answersUnknownTagIdsWithNotFound() throws Exception {
        try (PlcConnection connection = openFixtureArchive("")) {
            PlcReadResponse response = connection.readRequestBuilder()
                .addTagAddress("missing", "event/4711/value")
                .build()
                .execute()
                .get();

            assertEquals(PlcResponseCode.NOT_FOUND, response.getResponseCode("missing"));
        }
    }

    @Test
    void skipsTheMaintenanceReportWhenPdfIsDisabled() throws Exception {
        try (PlcConnection connection = openFixtureArchive("?pdf=false")) {
            PlcReadResponse response = connection.readRequestBuilder()
                .addTagAddress("assetId", "maintenance/assetId")
                .addTagAddress("value", "event/2/value")
                .build()
                .execute()
                .get();

            assertEquals(PlcResponseCode.NOT_FOUND, response.getResponseCode("assetId"));
            assertValue(response, "value", PlcValueType.LREAL, 77.25);
        }
    }

    @Test
    void rejectsConnectionStringsWithoutAFileUrl() {
        PlcDriver driver = new LegacyArchiveDriver();

        assertThrows(PlcConnectionException.class, () -> driver.getConnection("legacy-archive:/tmp/archive"));
        assertThrows(PlcConnectionException.class,
            () -> driver.getConnection("legacy-archive:http://example.com/archive"));
        assertThrows(PlcConnectionException.class, () -> driver.getConnection("simulated:foo"));
    }

    @Test
    void failsToConnectWhenTheDirectoryHoldsNoEventLog(@org.junit.jupiter.api.io.TempDir Path emptyDir) {
        PlcDriver driver = new LegacyArchiveDriver();

        PlcConnectionException e = assertThrows(PlcConnectionException.class, () -> {
            try (PlcConnection connection = driver.getConnection(
                "legacy-archive:" + emptyDir.toUri())) {
                connection.connect();
            }
        });

        assertTrue(e.getMessage().contains("events.bin"), e.getMessage());
    }

    @Test
    void closesTheConnection() throws Exception {
        PlcConnection connection = openFixtureArchive("");
        connection.close();

        assertFalse(connection.isConnected());
    }

    private static PlcConnection openFixtureArchive(String params) throws Exception {
        String url = "legacy-archive:" + FixturePaths.archiveDirectory().toUri() + params;
        // The connection factory resolves the driver through the ServiceLoader and connects.
        return PlcDriverManager.getDefault().getConnectionFactory().getConnection(url);
    }

    private static void assertValue(PlcReadResponse response, String tagName, PlcValueType expectedType,
                                    Object expectedValue) {
        assertEquals(PlcResponseCode.OK, response.getResponseCode(tagName), tagName);
        PlcValue value = response.getPlcValue(tagName);
        assertEquals(expectedType, value.getPlcValueType(), tagName);
        assertEquals(expectedValue, value.getObject(), tagName);
    }

}
