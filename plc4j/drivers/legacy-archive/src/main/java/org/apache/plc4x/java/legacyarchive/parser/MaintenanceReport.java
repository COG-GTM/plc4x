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

import java.time.LocalDate;

/**
 * The five fields of the legacy maintenance report.
 *
 * @param assetId     identifier of the asset the report was written for
 * @param lastService date of the last service
 * @param technician  name of the technician who performed it
 * @param nextDue     date the next service is due
 * @param hoursRun    operating hours at the time of the report
 */
public record MaintenanceReport(String assetId, LocalDate lastService, String technician, LocalDate nextDue,
                                double hoursRun) {
}
