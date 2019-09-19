/*
 * (C) Copyright 2019 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Nour AL KOTOB
 */
package org.nuxeo.ecm.platform.csv.export.io;

import java.io.IOException;

import org.apache.commons.csv.CSVPrinter;
import org.nuxeo.ecm.core.io.marshallers.csv.AbstractCSVWriter;
import org.nuxeo.ecm.platform.audit.api.LogEntry;

/**
 * 
 * @since TODO
 */
public class LogEntryCSVWriter extends AbstractCSVWriter<LogEntry> {

    @Override
    protected void write(LogEntry entity, CSVPrinter printer) throws IOException {
        // TODO Auto-generated method stub
        // 
        throw new UnsupportedOperationException();
    }

    @Override
    protected void writeHeader(LogEntry entity, CSVPrinter printer) throws IOException {
        // TODO Auto-generated method stub
        // 
        throw new UnsupportedOperationException();
    }

}
