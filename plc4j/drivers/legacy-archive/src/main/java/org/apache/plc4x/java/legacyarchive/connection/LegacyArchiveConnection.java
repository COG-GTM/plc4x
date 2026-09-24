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
package org.apache.plc4x.java.legacyarchive.connection;

import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.api.exceptions.PlcRuntimeException;
import org.apache.plc4x.java.api.messages.PlcPingRequest;
import org.apache.plc4x.java.api.messages.PlcPingResponse;
import org.apache.plc4x.java.api.messages.PlcReadRequest;
import org.apache.plc4x.java.api.messages.PlcReadResponse;
import org.apache.plc4x.java.api.types.ConnectionStateChangeType;
import org.apache.plc4x.java.api.types.PlcResponseCode;
import org.apache.plc4x.java.api.value.PlcValue;
import org.apache.plc4x.java.legacyarchive.configuration.LegacyArchiveConfiguration;
import org.apache.plc4x.java.legacyarchive.parser.EventLogParser;
import org.apache.plc4x.java.legacyarchive.parser.EventRecord;
import org.apache.plc4x.java.legacyarchive.parser.LegacyArchiveFormatException;
import org.apache.plc4x.java.legacyarchive.parser.MaintenanceReport;
import org.apache.plc4x.java.legacyarchive.parser.MaintenanceReportParser;
import org.apache.plc4x.java.legacyarchive.tag.LegacyArchiveTag;
import org.apache.plc4x.java.legacyarchive.tag.LegacyArchiveTagHandler;
import org.apache.plc4x.java.spi.drivers.ConnectionBase;
import org.apache.plc4x.java.spi.drivers.messages.DefaultPlcPingResponse;
import org.apache.plc4x.java.spi.drivers.messages.DefaultPlcReadResponse;
import org.apache.plc4x.java.spi.drivers.messages.items.DefaultPlcResponseItem;
import org.apache.plc4x.java.spi.drivers.messages.items.PlcResponseItem;
import org.apache.plc4x.java.spi.drivers.tags.PlcTagHandler;
import org.apache.plc4x.java.spi.transports.api.TransportInstance;
import org.apache.plc4x.java.spi.values.DefaultPlcValueHandler;
import org.apache.plc4x.java.spi.values.PlcDATE;
import org.apache.plc4x.java.spi.values.PlcDATE_AND_TIME;
import org.apache.plc4x.java.spi.values.PlcLREAL;
import org.apache.plc4x.java.spi.values.PlcSTRING;
import org.apache.plc4x.java.spi.values.PlcUSINT;
import org.apache.plc4x.java.spi.values.PlcValueHandler;
import org.apache.plc4x.java.utils.auditlog.api.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Connection to a directory holding the offline artifacts of a legacy controller.
 * <p>
 * Both artifacts are read once, during {@link #connect()}: the archive is a finished dump, so
 * there is nothing to poll and no transport involved — the transport instance handed to
 * {@link ConnectionBase} is {@code null}, which is safe because only the receive-loop code
 * paths touch it and this connection never starts one.
 * <p>
 * An event tag resolves against the <em>most recent</em> record of its tag id, which is what a
 * consumer reading "the current value" expects from a log that may hold several entries per signal.
 */
public class LegacyArchiveConnection extends ConnectionBase<LegacyArchiveConfiguration> {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyArchiveConnection.class);

    private final Path archiveDirectory;

    private boolean connected = false;
    private List<EventRecord> eventRecords = Collections.emptyList();
    private Map<Long, EventRecord> latestEventByTagId = Collections.emptyMap();
    private MaintenanceReport maintenanceReport;

    public LegacyArchiveConnection(Path archiveDirectory, LegacyArchiveConfiguration configuration,
                                   AuditLog auditLog) {
        super(configuration, (TransportInstance<?>) null, auditLog);
        this.archiveDirectory = Objects.requireNonNull(archiveDirectory, "archiveDirectory");
    }

    @Override
    protected PlcTagHandler getTagHandler() {
        return new LegacyArchiveTagHandler();
    }

    @Override
    protected PlcValueHandler getValueHandler() {
        return new DefaultPlcValueHandler();
    }

    @Override
    protected void onConnect() throws PlcConnectionException {
        if (!Files.isDirectory(archiveDirectory)) {
            throw new PlcConnectionException("Archive directory '" + archiveDirectory + "' doesn't exist.");
        }
        Path eventLog = archiveDirectory.resolve(EventLogParser.EVENT_LOG_FILE_NAME);
        if (!Files.isRegularFile(eventLog)) {
            throw new PlcConnectionException("Archive directory '" + archiveDirectory + "' contains no '"
                + EventLogParser.EVENT_LOG_FILE_NAME + "'.");
        }
        try {
            eventRecords = new EventLogParser().parse(eventLog);
        } catch (IOException | LegacyArchiveFormatException e) {
            throw new PlcConnectionException("Unable to read the event log of archive '"
                + archiveDirectory + "'.", e);
        }
        Map<Long, EventRecord> latest = new LinkedHashMap<>();
        for (EventRecord record : eventRecords) {
            latest.merge(record.tagId(), record,
                (existing, candidate) -> (candidate.timestampMillis() >= existing.timestampMillis())
                    ? candidate : existing);
        }
        latestEventByTagId = Collections.unmodifiableMap(latest);

        if (getConfiguration().isPdf()) {
            Path report = archiveDirectory.resolve(MaintenanceReportParser.MAINTENANCE_REPORT_FILE_NAME);
            if (!Files.isRegularFile(report)) {
                throw new PlcConnectionException("Archive directory '" + archiveDirectory + "' contains no '"
                    + MaintenanceReportParser.MAINTENANCE_REPORT_FILE_NAME
                    + "'. Pass 'pdf=false' to read an archive without a maintenance report.");
            }
            try {
                maintenanceReport = new MaintenanceReportParser().parse(report);
            } catch (IOException | LegacyArchiveFormatException e) {
                throw new PlcConnectionException("Unable to read the maintenance report of archive '"
                    + archiveDirectory + "'.", e);
            }
        }

        LOGGER.debug("Opened legacy archive '{}' with {} event record(s), maintenance report {}",
            archiveDirectory, eventRecords.size(), (maintenanceReport != null) ? "read" : "skipped");
        connected = true;
        fireConnectionStateChanged(ConnectionStateChangeType.CONNECTED, null);
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    /**
     * {@link ConnectionBase} learns its identity from a package-private setter the transport-aware
     * factory calls; this driver builds its connection directly, so the identity is stated here.
     */
    @Override
    public String getProtocolCode() {
        return "legacy-archive";
    }

    @Override
    public String getProtocolName() {
        return "Legacy Controller Archive";
    }

    @Override
    public String getTransportCode() {
        return "file";
    }

    @Override
    public String getTransportName() {
        return "Filesystem";
    }

    @Override
    public void close() {
        connected = false;
        fireConnectionStateChanged(ConnectionStateChangeType.DISCONNECTED, null);
    }

    /** The records of the event log, in file order. Empty until the connection is opened. */
    public List<EventRecord> getEventRecords() {
        return eventRecords;
    }

    /** The maintenance report, or empty when reading it is disabled. */
    public Optional<MaintenanceReport> getMaintenanceReport() {
        return Optional.ofNullable(maintenanceReport);
    }

    @Override
    public String toString() {
        return String.format("legacy-archive:file://%s", archiveDirectory);
    }

    @Override
    protected CompletableFuture<PlcPingResponse> onPing(PlcPingRequest pingRequest) {
        if (!connected) {
            return CompletableFuture.completedFuture(
                new DefaultPlcPingResponse(pingRequest, PlcResponseCode.INVALID_ADDRESS));
        }
        return CompletableFuture.completedFuture(new DefaultPlcPingResponse(pingRequest,
            Files.isDirectory(archiveDirectory) ? PlcResponseCode.OK : PlcResponseCode.NOT_FOUND));
    }

    @Override
    protected CompletableFuture<PlcReadResponse> onRead(PlcReadRequest readRequest) {
        if (!connected) {
            CompletableFuture<PlcReadResponse> failed = new CompletableFuture<>();
            failed.completeExceptionally(new PlcRuntimeException(
                "The connection to archive '" + archiveDirectory + "' is closed."));
            return failed;
        }
        Map<String, PlcResponseItem<PlcValue>> tags = new HashMap<>();
        for (String tagName : readRequest.getTagNames()) {
            // A tag the builder couldn't parse stays in the request with its error code and a
            // null tag, so echo that code instead of dereferencing the tag.
            PlcResponseCode requestCode = readRequest.getTagResponseCode(tagName);
            if (requestCode != PlcResponseCode.OK) {
                tags.put(tagName, new DefaultPlcResponseItem<>(requestCode, null));
                continue;
            }
            LegacyArchiveTag tag = (LegacyArchiveTag) readRequest.getTag(tagName);
            Optional<PlcValue> value = read(tag);
            tags.put(tagName, value
                .map(v -> new DefaultPlcResponseItem<>(PlcResponseCode.OK, v))
                .orElseGet(() -> new DefaultPlcResponseItem<>(PlcResponseCode.NOT_FOUND, null)));
        }
        return CompletableFuture.completedFuture(new DefaultPlcReadResponse(readRequest, tags));
    }

    private Optional<PlcValue> read(LegacyArchiveTag tag) {
        if (tag.getSection() == LegacyArchiveTag.Section.EVENT) {
            EventRecord record = latestEventByTagId.get(tag.getTagId());
            if (record == null) {
                return Optional.empty();
            }
            return Optional.of(switch (tag.getEventAttribute()) {
                case VALUE -> new PlcLREAL(record.value());
                case SEVERITY -> new PlcUSINT((short) record.severity().getCode());
                case QUALITY -> new PlcUSINT((short) record.quality().getCode());
                case TIMESTAMP -> new PlcDATE_AND_TIME(record.timestamp());
            });
        }
        if (maintenanceReport == null) {
            return Optional.empty();
        }
        return Optional.of(switch (tag.getMaintenanceField()) {
            case ASSET_ID -> new PlcSTRING(maintenanceReport.assetId());
            case LAST_SERVICE -> new PlcDATE(maintenanceReport.lastService());
            case TECHNICIAN -> new PlcSTRING(maintenanceReport.technician());
            case NEXT_DUE -> new PlcDATE(maintenanceReport.nextDue());
            case HOURS_RUN -> new PlcLREAL(maintenanceReport.hoursRun());
        });
    }

}
