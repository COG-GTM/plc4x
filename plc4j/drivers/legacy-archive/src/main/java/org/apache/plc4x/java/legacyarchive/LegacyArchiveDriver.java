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
import org.apache.plc4x.java.api.authentication.PlcAuthentication;
import org.apache.plc4x.java.api.exceptions.PlcConnectionException;
import org.apache.plc4x.java.legacyarchive.configuration.LegacyArchiveConfiguration;
import org.apache.plc4x.java.legacyarchive.connection.LegacyArchiveConnection;
import org.apache.plc4x.java.legacyarchive.tag.LegacyArchiveTag;
import org.apache.plc4x.java.spi.config.Configuration;
import org.apache.plc4x.java.spi.config.ConfigurationFactory;
import org.apache.plc4x.java.spi.drivers.ConnectionBase;
import org.apache.plc4x.java.spi.drivers.DriverBase;
import org.apache.plc4x.java.spi.transports.api.TransportInstance;
import org.apache.plc4x.java.utils.auditlog.api.AuditLog;
import org.apache.plc4x.java.utils.auditlog.api.config.AuditLogConfiguration;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Driver reading the offline artifacts a legacy controller leaves behind — a proprietary binary
 * event log ({@code events.bin}) and a PDF maintenance report ({@code maintenance-report.pdf}) —
 * and serving their content through the regular PLC4X read API.
 *
 * <p>URL schema: {@code legacy-archive:file:///path/to/archive/dir[?pdf=false]}.</p>
 *
 * <p>Tag addresses are {@code event/<tagId>/<value|severity|quality|timestamp>} and
 * {@code maintenance/<assetId|lastService|technician|nextDue|hoursRun>}.</p>
 */
public class LegacyArchiveDriver extends DriverBase {

    @Override
    public String getProtocolCode() {
        return "legacy-archive";
    }

    @Override
    public String getProtocolName() {
        return "Legacy Controller Archive";
    }

    @Override
    protected Class<? extends Configuration> getConfigurationClass() {
        return LegacyArchiveConfiguration.class;
    }

    @Override
    protected boolean canRead() {
        return true;
    }

    @Override
    protected boolean canPing() {
        return true;
    }

    /**
     * The archive lives on the local filesystem and no transport is opened, so
     * {@link #getConnection(String)} parses the {@code legacy-archive:file://...} form directly —
     * the inherited URI parser expects a {@code {protocol}:{transport}://} shape and a registered
     * transport, neither of which apply here.
     */
    @Override
    public PlcConnection getConnection(String connectionString) throws PlcConnectionException {
        String prefix = getProtocolCode() + ":";
        if ((connectionString == null) || !connectionString.startsWith(prefix)) {
            throw new PlcConnectionException(
                "Invalid URL: expected '" + prefix + "file:///path/to/archive/dir'");
        }
        String remainder = connectionString.substring(prefix.length());
        int paramStart = remainder.indexOf('?');
        String paramString = (paramStart >= 0) ? remainder.substring(paramStart + 1) : null;
        String fileUrl = (paramStart >= 0) ? remainder.substring(0, paramStart) : remainder;

        Path archiveDirectory = toPath(fileUrl, connectionString);

        ConfigurationFactory configurationFactory = new ConfigurationFactory();
        LegacyArchiveConfiguration configuration = configurationFactory
            .createConfiguration(LegacyArchiveConfiguration.class, paramString);
        configuration.setArchiveDirectory(archiveDirectory);

        AuditLogConfiguration auditLogConfiguration = configurationFactory
            .createPrefixedConfiguration(AuditLogConfiguration.class, "log", paramString);

        return new LegacyArchiveConnection(archiveDirectory, configuration, AuditLog.builder()
            .withSource(getProtocolCode())
            .withConfiguration(auditLogConfiguration)
            .build());
    }

    private Path toPath(String fileUrl, String connectionString) throws PlcConnectionException {
        final URI uri;
        try {
            uri = new URI(fileUrl);
        } catch (URISyntaxException e) {
            throw new PlcConnectionException("Invalid URL '" + connectionString
                + "': the archive location is not a valid URI.", e);
        }
        if (!"file".equalsIgnoreCase(uri.getScheme())) {
            throw new PlcConnectionException("Invalid URL '" + connectionString
                + "': only the 'file' scheme is supported, got '" + uri.getScheme() + "'.");
        }
        try {
            return Paths.get(uri);
        } catch (IllegalArgumentException | java.nio.file.FileSystemNotFoundException e) {
            throw new PlcConnectionException("Invalid URL '" + connectionString
                + "': the archive location doesn't denote a local directory.", e);
        }
    }

    @Override
    public PlcConnection getConnection(String connectionString, PlcAuthentication authentication)
        throws PlcConnectionException {
        if (authentication != null) {
            throw new PlcConnectionException("Legacy archive driver does not support authentication.");
        }
        return getConnection(connectionString);
    }

    /**
     * Unused — {@link #getConnection(String)} above takes the direct path that doesn't go through
     * {@link DriverBase}'s transport-aware factory. Kept so the abstract contract is satisfied.
     */
    @Override
    protected ConnectionBase<?> getConnection(Configuration configuration,
                                              TransportInstance<?> transportInstance,
                                              AuditLog auditLog) {
        throw new UnsupportedOperationException(
            "Legacy archive driver bypasses the transport-aware connection factory.");
    }

    @Override
    public LegacyArchiveTag prepareTag(String tagAddress) {
        return LegacyArchiveTag.of(tagAddress);
    }

}
