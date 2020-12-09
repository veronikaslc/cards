/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package ca.sickkids.ccm.lfs;

import org.apache.sling.commons.scheduler.ScheduleOptions;
import org.apache.sling.commons.scheduler.Scheduler;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class NightlyExport
{
    /** Default log. */
    protected static final Logger LOGGER = LoggerFactory.getLogger(NightlyExport.class);

    /** The scheduler for rescheduling jobs. */
    @Reference
    private Scheduler scheduler;

    protected void activate(ComponentContext componentContext) throws Exception
    {
        LOGGER.error("NightlyExport activating");
        // TODO: Change from every minute to every day at 11:30pm ("30 23 * * * ?")
        ScheduleOptions options = this.scheduler.EXPR("0 * * * * ?");
        options.name("NightlyExport");
        options.canRunConcurrently(true);

        final Runnable exportJob = new NightlyExportTask();

        try {
            this.scheduler.schedule(exportJob, options);
        } catch (Exception e) {
            LOGGER.error("NightlyExport Failed to schedule");
        }
    }
}
