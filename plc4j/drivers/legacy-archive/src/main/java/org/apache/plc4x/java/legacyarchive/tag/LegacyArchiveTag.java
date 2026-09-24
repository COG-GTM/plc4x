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
package org.apache.plc4x.java.legacyarchive.tag;

import org.apache.plc4x.java.api.exceptions.PlcInvalidTagException;
import org.apache.plc4x.java.api.model.PlcTag;
import org.apache.plc4x.java.api.types.PlcValueType;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Address of a value in a legacy archive.
 * <p>
 * Two forms are accepted:
 * <ul>
 *     <li>{@code event/<tagId>/<value|severity|quality|timestamp>} — an attribute of the most
 *     recent event-log record of the given tag id</li>
 *     <li>{@code maintenance/<assetId|lastService|technician|nextDue|hoursRun>} — a field of
 *     the maintenance report</li>
 * </ul>
 */
public class LegacyArchiveTag implements PlcTag {

    private static final Pattern EVENT_PATTERN =
        Pattern.compile("^event/(?<tagId>\\d{1,10})/(?<attribute>value|severity|quality|timestamp)$");
    private static final Pattern MAINTENANCE_PATTERN =
        Pattern.compile("^maintenance/(?<field>assetId|lastService|technician|nextDue|hoursRun)$");

    /** Which of the two archive artifacts the tag addresses. */
    public enum Section {
        EVENT,
        MAINTENANCE
    }

    /** Attributes of an event-log record. */
    public enum EventAttribute {
        VALUE("value", PlcValueType.LREAL),
        SEVERITY("severity", PlcValueType.USINT),
        QUALITY("quality", PlcValueType.USINT),
        TIMESTAMP("timestamp", PlcValueType.DATE_AND_TIME);

        private final String address;
        private final PlcValueType plcValueType;

        EventAttribute(String address, PlcValueType plcValueType) {
            this.address = address;
            this.plcValueType = plcValueType;
        }

        public String getAddress() {
            return address;
        }

        public PlcValueType getPlcValueType() {
            return plcValueType;
        }

        static EventAttribute ofAddress(String address) {
            for (EventAttribute attribute : values()) {
                if (attribute.address.equals(address)) {
                    return attribute;
                }
            }
            throw new PlcInvalidTagException(address);
        }
    }

    /** Fields of the maintenance report. */
    public enum MaintenanceField {
        ASSET_ID("assetId", PlcValueType.STRING),
        LAST_SERVICE("lastService", PlcValueType.DATE),
        TECHNICIAN("technician", PlcValueType.STRING),
        NEXT_DUE("nextDue", PlcValueType.DATE),
        HOURS_RUN("hoursRun", PlcValueType.LREAL);

        private final String address;
        private final PlcValueType plcValueType;

        MaintenanceField(String address, PlcValueType plcValueType) {
            this.address = address;
            this.plcValueType = plcValueType;
        }

        public String getAddress() {
            return address;
        }

        public PlcValueType getPlcValueType() {
            return plcValueType;
        }

        static MaintenanceField ofAddress(String address) {
            for (MaintenanceField field : values()) {
                if (field.address.equals(address)) {
                    return field;
                }
            }
            throw new PlcInvalidTagException(address);
        }
    }

    private final String addressString;
    private final Section section;
    private final long tagId;
    private final EventAttribute eventAttribute;
    private final MaintenanceField maintenanceField;

    private LegacyArchiveTag(String addressString, Section section, long tagId, EventAttribute eventAttribute,
                             MaintenanceField maintenanceField) {
        this.addressString = addressString;
        this.section = section;
        this.tagId = tagId;
        this.eventAttribute = eventAttribute;
        this.maintenanceField = maintenanceField;
    }

    public static boolean matches(String tagAddress) {
        return (tagAddress != null)
            && (EVENT_PATTERN.matcher(tagAddress).matches() || MAINTENANCE_PATTERN.matcher(tagAddress).matches());
    }

    public static LegacyArchiveTag of(String tagAddress) {
        if (tagAddress == null) {
            throw new PlcInvalidTagException("null");
        }
        Matcher eventMatcher = EVENT_PATTERN.matcher(tagAddress);
        if (eventMatcher.matches()) {
            long tagId = Long.parseLong(eventMatcher.group("tagId"));
            if (tagId > 0xFFFFFFFFL) {
                throw new PlcInvalidTagException(tagAddress);
            }
            return new LegacyArchiveTag(tagAddress, Section.EVENT, tagId,
                EventAttribute.ofAddress(eventMatcher.group("attribute")), null);
        }
        Matcher maintenanceMatcher = MAINTENANCE_PATTERN.matcher(tagAddress);
        if (maintenanceMatcher.matches()) {
            return new LegacyArchiveTag(tagAddress, Section.MAINTENANCE, -1, null,
                MaintenanceField.ofAddress(maintenanceMatcher.group("field")));
        }
        throw new PlcInvalidTagException(tagAddress);
    }

    @Override
    public String getAddressString() {
        return addressString;
    }

    @Override
    public PlcValueType getPlcValueType() {
        return (section == Section.EVENT) ? eventAttribute.getPlcValueType() : maintenanceField.getPlcValueType();
    }

    public Section getSection() {
        return section;
    }

    /** The addressed event-log tag id; only meaningful for {@link Section#EVENT} tags. */
    public long getTagId() {
        return tagId;
    }

    public EventAttribute getEventAttribute() {
        return eventAttribute;
    }

    public MaintenanceField getMaintenanceField() {
        return maintenanceField;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LegacyArchiveTag that)) {
            return false;
        }
        return addressString.equals(that.addressString);
    }

    @Override
    public int hashCode() {
        return Objects.hash(addressString);
    }

    @Override
    public String toString() {
        return "LegacyArchiveTag{" + addressString + "}";
    }

}
