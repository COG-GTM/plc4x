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
package org.apache.plc4x.java.legacyarchive.configuration;

import org.apache.plc4x.java.spi.config.Configuration;
import org.apache.plc4x.java.spi.config.annotations.ConfigurationParameter;
import org.apache.plc4x.java.spi.config.annotations.Description;
import org.apache.plc4x.java.spi.config.annotations.defaults.BooleanDefaultValue;

import java.nio.file.Path;

/**
 * Configuration of a legacy-archive connection: the directory holding the archive — taken from
 * the {@code file://} part of the connection string — and whether the PDF maintenance report is
 * read at all.
 */
public class LegacyArchiveConfiguration implements Configuration {

    @ConfigurationParameter("pdf")
    @BooleanDefaultValue(true)
    @Description("Whether the PDF maintenance report is read. Set to false for archives that only "
        + "contain an event log; maintenance tags then answer with NOT_FOUND.")
    private boolean pdf;

    /**
     * Not a connection-string parameter: this comes from the URL itself
     * ({@code legacy-archive:file:///path/to/archive/dir}).
     */
    private Path archiveDirectory;

    public boolean isPdf() {
        return pdf;
    }

    public void setPdf(boolean pdf) {
        this.pdf = pdf;
    }

    public Path getArchiveDirectory() {
        return archiveDirectory;
    }

    public void setArchiveDirectory(Path archiveDirectory) {
        this.archiveDirectory = archiveDirectory;
    }

}
