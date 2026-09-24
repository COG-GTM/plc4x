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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Reader for the proprietary binary event log ({@code events.bin}) a legacy controller writes
 * when it dumps its archive.
 * <p>
 * The file is little endian throughout and consists of an 8 byte header — ASCII magic
 * {@code EVLG}, uint16 version, uint16 record count — followed by {@code recordCount}
 * fixed-width 24 byte records:
 * <pre>
 * offset size field
 *      0    8 uint64  timestampMillis
 *      8    4 uint32  tagId
 *     12    1 uint8   severity (0=info, 1=warn, 2=alarm, 3=fault)
 *     13    1 uint8   quality  (0=bad, 1=uncertain, 2=good)
 *     14    2 uint16  reserved (must be 0)
 *     16    8 float64 value
 * </pre>
 * Anything that doesn't fit this description is rejected with a {@link LegacyArchiveFormatException}
 * rather than parsed on a best-effort basis: these files are read long after the controller that
 * produced them is gone, so silently accepting a damaged one would publish wrong process values.
 */
public class EventLogParser {

    public static final String EVENT_LOG_FILE_NAME = "events.bin";

    static final byte[] MAGIC = {'E', 'V', 'L', 'G'};
    static final int SUPPORTED_VERSION = 1;
    static final int HEADER_LENGTH = 8;
    static final int RECORD_LENGTH = 24;

    /**
     * Parses the given event log.
     *
     * @param file the {@code events.bin} to read
     * @return the records in file order
     * @throws IOException                   if the file can't be read
     * @throws LegacyArchiveFormatException  if the content doesn't match the format described above
     */
    public List<EventRecord> parse(Path file) throws IOException, LegacyArchiveFormatException {
        return parse(Files.readAllBytes(file), file.getFileName().toString());
    }

    /**
     * Parses an event log held in memory.
     *
     * @param content   the raw file content
     * @param fileName  name used in exception messages to point at the offending artifact
     */
    public List<EventRecord> parse(byte[] content, String fileName) throws LegacyArchiveFormatException {
        if (content.length < HEADER_LENGTH) {
            throw new LegacyArchiveFormatException(fileName + ": file is too short to contain the "
                + HEADER_LENGTH + " byte header (got " + content.length + " bytes)");
        }

        ByteBuffer buffer = ByteBuffer.wrap(content).order(ByteOrder.LITTLE_ENDIAN);

        byte[] magic = new byte[MAGIC.length];
        buffer.get(magic);
        if (!java.util.Arrays.equals(MAGIC, magic)) {
            throw new LegacyArchiveFormatException(fileName + ": invalid magic '"
                + new String(magic, StandardCharsets.ISO_8859_1) + "' (expected '"
                + new String(MAGIC, StandardCharsets.ISO_8859_1) + "')");
        }

        int version = Short.toUnsignedInt(buffer.getShort());
        if (version != SUPPORTED_VERSION) {
            throw new LegacyArchiveFormatException(fileName + ": unsupported version " + version
                + " (this driver reads version " + SUPPORTED_VERSION + ")");
        }

        int recordCount = Short.toUnsignedInt(buffer.getShort());
        int bodyLength = content.length - HEADER_LENGTH;
        if ((bodyLength % RECORD_LENGTH) != 0) {
            throw new LegacyArchiveFormatException(fileName + ": truncated trailing record - the "
                + bodyLength + " bytes after the header are not a multiple of the " + RECORD_LENGTH
                + " byte record size (last record holds " + (bodyLength % RECORD_LENGTH) + " bytes)");
        }
        int actualRecordCount = bodyLength / RECORD_LENGTH;
        if (actualRecordCount != recordCount) {
            throw new LegacyArchiveFormatException(fileName + ": header announces " + recordCount
                + " records but the file holds " + actualRecordCount);
        }

        List<EventRecord> records = new ArrayList<>(recordCount);
        for (int i = 0; i < recordCount; i++) {
            records.add(parseRecord(buffer, fileName, i));
        }
        return Collections.unmodifiableList(records);
    }

    private EventRecord parseRecord(ByteBuffer buffer, String fileName, int index)
        throws LegacyArchiveFormatException {
        long timestampMillis = buffer.getLong();
        long tagId = Integer.toUnsignedLong(buffer.getInt());
        int severityCode = Byte.toUnsignedInt(buffer.get());
        int qualityCode = Byte.toUnsignedInt(buffer.get());
        int reserved = Short.toUnsignedInt(buffer.getShort());
        if (reserved != 0) {
            throw new LegacyArchiveFormatException(fileName + ": record " + index
                + " has a non-zero reserved field (" + reserved + ")");
        }
        double value = buffer.getDouble();
        try {
            return new EventRecord(timestampMillis, tagId, EventSeverity.ofCode(severityCode),
                EventQuality.ofCode(qualityCode), value);
        } catch (LegacyArchiveFormatException e) {
            throw new LegacyArchiveFormatException(fileName + ": record " + index + ": " + e.getMessage(), e);
        }
    }

}
