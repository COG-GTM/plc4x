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

/**
 * Thrown when an archive artifact doesn't match the format this driver expects — a corrupt or
 * foreign event log, or a maintenance report missing one of its labelled fields. The message
 * always names the artifact and what exactly was wrong with it.
 */
public class LegacyArchiveFormatException extends Exception {

    public LegacyArchiveFormatException(String message) {
        super(message);
    }

    public LegacyArchiveFormatException(String message, Throwable cause) {
        super(message, cause);
    }

}
