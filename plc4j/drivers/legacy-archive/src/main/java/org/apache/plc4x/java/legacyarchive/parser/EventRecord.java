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

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One 24 byte entry of the legacy event log.
 *
 * @param timestampMillis milliseconds since the epoch, as recorded by the controller (UTC)
 * @param tagId           unsigned 32 bit identifier of the signal the entry belongs to
 * @param severity        severity of the entry
 * @param quality         quality the controller assigned to {@code value}
 * @param value           the recorded process value
 */
public record EventRecord(long timestampMillis, long tagId, EventSeverity severity, EventQuality quality,
                          double value) {

    /** The timestamp as a UTC {@link LocalDateTime}, the shape PlcDATE_AND_TIME works with. */
    public LocalDateTime timestamp() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMillis), ZoneOffset.UTC);
    }

}
