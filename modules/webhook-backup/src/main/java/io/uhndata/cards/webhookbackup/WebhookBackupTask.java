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

package io.uhndata.cards.webhookbackup;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.httprequests.HttpRequests;

public class WebhookBackupTask implements Runnable
{
    /** Default log. */
    private static final Logger LOGGER = LoggerFactory.getLogger(WebhookBackupTask.class);

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;
    private final String exportRunMode;
    private final LocalDateTime exportLowerBound;
    private final LocalDateTime exportUpperBound;

    WebhookBackupTask(final ResourceResolverFactory resolverFactory, final String exportRunMode)
    {
        this.resolverFactory = resolverFactory;
        this.exportRunMode = exportRunMode;
        this.exportLowerBound = null;
        this.exportUpperBound = null;
    }

    WebhookBackupTask(final ResourceResolverFactory resolverFactory, final String exportRunMode,
        final LocalDateTime exportLowerBound, final LocalDateTime exportUpperBound)
    {
        this.resolverFactory = resolverFactory;
        this.exportRunMode = exportRunMode;
        this.exportLowerBound = exportLowerBound;
        this.exportUpperBound = exportUpperBound;
    }

    @Override
    public void run()
    {
        if ("nightly".equals(this.exportRunMode) || "manualToday".equals(this.exportRunMode)) {
            doNightlyExport();
        } else if ("manualAfter".equals(this.exportRunMode)) {
            doManualExport(this.exportLowerBound, null);
        } else if ("manualBetween".equals(this.exportRunMode)) {
            doManualExport(this.exportLowerBound, this.exportUpperBound);
        }
    }

    public void doManualExport(LocalDateTime lower, LocalDateTime upper)
    {
        LOGGER.info("Executing ManualExport");
        String fileDateString = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String requestDateStringLower = lower.toString();
        String requestDateStringUpper = (upper != null)
            ? upper.toString()
            : null;

        Set<String> subjectList = getUuidNameList("cards:Subject");
        Set<String> formList = getUuidNameList("cards:Form");
        sendStringSet(subjectList, "SubjectListBackup");
        sendStringSet(formList, "FormListBackup");
        Set<String> changedFormList = getChangedFormsBounded(requestDateStringLower, requestDateStringUpper);
        LOGGER.warn("Changed Forms are: {}", changedFormList);
        // TODO: Then iterate through all the changes Forms and back them up
        for (String formPath : changedFormList) {
            // Get a serialized version of this Form
            String formData = getFormAsJson(formPath);
            //LOGGER.warn("{} --> {}", formPath, formData);
            this.output(formData, "/FormBackup" + formPath);
        }

        Set<String> changedSubjectList = getChangedNodesBounded("cards:Subject",
            requestDateStringLower, requestDateStringUpper);

        for (String subjectPath : changedSubjectList) {
            String subjectData = getSubjectAsJson(subjectPath);
            this.output(subjectData, "/SubjectBackup" + subjectPath);
        }

        Set<SubjectIdentifier> changedSubjects = (upper != null)
            ? this.getChangedSubjectsBounded(requestDateStringLower, requestDateStringUpper)
            : this.getChangedSubjects(requestDateStringLower);

        for (SubjectIdentifier identifier : changedSubjects) {
            SubjectContents subjectContents = (upper != null)
                ? getSubjectContentsBounded(identifier.getPath(), requestDateStringLower, requestDateStringUpper)
                : getSubjectContents(identifier.getPath(), requestDateStringLower);
            if (subjectContents != null) {
                String filename = String.format(
                    "%s_formData_%s.json",
                    cleanString(identifier.getParticipantId()),
                    fileDateString);
                this.output(subjectContents, filename);
            }
        }
    }

    public void doNightlyExport()
    {
        LOGGER.info("Executing NightlyExport");
        LocalDateTime today = LocalDateTime.now();
        String fileDateString = today.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String requestDateString = today.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        Set<SubjectIdentifier> changedSubjects = this.getChangedSubjects(requestDateString);

        for (SubjectIdentifier identifier : changedSubjects) {
            SubjectContents subjectContents = getSubjectContents(identifier.getPath(), requestDateString);
            if (subjectContents != null) {
                String filename = String.format(
                    "%s_formData_%s.json",
                    cleanString(identifier.getParticipantId()),
                    fileDateString);
                this.output(subjectContents, filename);
            }
        }
    }

    private String cleanString(String input)
    {
        return input.replaceAll("[^A-Za-z0-9]", "");
    }

    private static final class SubjectIdentifier
    {
        private String path;

        private String participantId;

        SubjectIdentifier(String path, String participantId)
        {
            this.path = path;
            this.participantId = participantId;
        }

        public String getPath()
        {
            return this.path;
        }

        public String getParticipantId()
        {
            return this.participantId;
        }

        @Override
        public int hashCode()
        {
            return Objects.hashCode(this.path.hashCode()) + Objects.hashCode(this.participantId.hashCode());
        }

        @Override
        public boolean equals(Object obj)
        {
            if (this == obj) {
                return true;
            }
            if (obj == null || this.getClass() != obj.getClass()) {
                return false;
            }
            SubjectIdentifier other = (SubjectIdentifier) obj;
            return Objects.equals(this.path, other.getPath())
                && Objects.equals(this.participantId, other.getParticipantId());
        }

        @Override
        public String toString()
        {
            return String.format("{path:\"%s\",participantId:\"%s\"}", this.path, this.participantId);
        }
    }

    private static final class SubjectContents
    {
        private String data;

        private String url;

        SubjectContents(String data, String url)
        {
            this.data = data;
            this.url = url;
        }

        public String getData()
        {
            return this.data;
        }

        public String getUrl()
        {
            return this.url;
        }
    }

    private Set<SubjectIdentifier> getChangedSubjects(String requestDateString)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<SubjectIdentifier> subjects = new HashSet<>();
            String query = String.format(
                "SELECT subject.* FROM [cards:Form] AS form INNER JOIN [cards:Subject] AS subject"
                    + " ON form.'subject'=subject.[jcr:uuid]"
                    + " WHERE form.[jcr:lastModified] >= '%s' AND NOT form.[statusFlags] = 'INCOMPLETE'",
                requestDateString
            );

            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource subject = results.next();
                String path = subject.getPath();
                String participantId = subject.getValueMap().get("identifier", String.class);
                subjects.add(new SubjectIdentifier(path, participantId));
            }
            return subjects;
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private Set<SubjectIdentifier> getChangedSubjectsBounded(String requestDateStringLower,
        String requestDateStringUpper)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<SubjectIdentifier> subjects = new HashSet<>();
            String query = String.format(
                "SELECT subject.* FROM [cards:Form] AS form INNER JOIN [cards:Subject] AS subject"
                    + " ON form.'subject'=subject.[jcr:uuid]"
                    + " WHERE form.[jcr:lastModified] >= '%s'"
                    + " AND form.[jcr:lastModified] < '%s'"
                    + " AND NOT form.[statusFlags] = 'INCOMPLETE'",
                requestDateStringLower, requestDateStringUpper
            );

            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource subject = results.next();
                String path = subject.getPath();
                String participantId = subject.getValueMap().get("identifier", String.class);
                subjects.add(new SubjectIdentifier(path, participantId));
            }
            return subjects;
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private Set<String> getChangedFormsBounded(String requestDateStringLower, String requestDateStringUpper)
    {
        return getChangedNodesBounded("cards:Form", requestDateStringLower, requestDateStringUpper);
    }

    private Set<String> getChangedNodesBounded(String cardsType,
        String requestDateStringLower, String requestDateStringUpper)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<String> changedForms = new HashSet<>();
            String query = String.format(
                "SELECT * FROM [" + cardsType + "] AS form"
                    + " WHERE form.[jcr:lastModified] >= '%s'"
                    + " AND form.[jcr:lastModified] < '%s'",
                requestDateStringLower, requestDateStringUpper
            );
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource form = results.next();
                String formPath = form.getPath();
                changedForms.add(formPath);
            }
            return changedForms;
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormsChangedBounded: {}", e);
        }
        return Collections.emptySet();
    }

    private Set<String> getUuidNameList(String cardsType)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Set<String> uuidNames = new HashSet<>();
            String query = "SELECT * FROM [" + cardsType + "] as n order by n.'jcr:created'";
            Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");
            while (results.hasNext()) {
                Resource resource = results.next();
                //String uuidName = resource.getName();
                String uuidName = resource.getPath();
                uuidNames.add(uuidName);
            }
            return uuidNames;
        } catch (LoginException e) {
            LOGGER.warn("Get service session failure: {}", e.getMessage(), e);
        }
        return Collections.emptySet();
    }

    private SubjectContents getSubjectContents(String path, String requestDateString)
    {
        String subjectDataUrl = String.format("%s.data.deep.-labels"
            + ".dataFilter:modifiedAfter=%s.dataFilter:statusNot=INCOMPLETE", path, requestDateString);
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource subjectData = resolver.resolve(subjectDataUrl);
            return new SubjectContents(subjectData.adaptTo(JsonObject.class).toString(), subjectDataUrl);
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
            return null;
        }
    }

    private SubjectContents getSubjectContentsBounded(String path, String requestDateStringLower,
        String requestDateStringUpper)
    {
        String subjectDataUrl = String.format("%s.data.deep.-labels"
            + ".dataFilter:modifiedAfter=%s.dataFilter:modifiedBefore=%s.dataFilter:statusNot=INCOMPLETE",
            path, requestDateStringLower, requestDateStringUpper);
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource subjectData = resolver.resolve(subjectDataUrl);
            return new SubjectContents(subjectData.adaptTo(JsonObject.class).toString(), subjectDataUrl);
        } catch (LoginException e) {
            LOGGER.warn("Failed to get service session: {}", e.getMessage(), e);
            return null;
        }
    }

    private String getFormAsJson(String formPath)
    {
        String formDataUrl = String.format("%s.data.deep", formPath);
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource formData = resolver.resolve(formDataUrl);
            return formData.adaptTo(JsonObject.class).toString();
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormAsJson: {}", e);
            return null;
        }
    }

    private String getSubjectAsJson(String subjectPath)
    {
        try (ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            Resource subjectData = resolver.resolve(subjectPath);
            return subjectData.adaptTo(JsonObject.class).toString();
        } catch (LoginException e) {
            LOGGER.warn("LoginException in getFormAsJson: {}", e);
            return null;
        }
    }

    private void sendStringSet(Set<String> set, String pathname)
    {
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        JsonArrayBuilder jsonSetBuilder = Json.createArrayBuilder();
        Iterator<String> setIterator = set.iterator();
        while (setIterator.hasNext()) {
            jsonSetBuilder.add(setIterator.next());
        }
        try {
            HttpRequests.getPostResponse(
                backupWebhookUrl + "/" + pathname,
                jsonSetBuilder.build().toString(),
                "application/json"
            );
        } catch (IOException e) {
            LOGGER.warn("IOException while in sendStringSet: {}", e);
        }
    }

    private void output(SubjectContents input, String filename)
    {
        //LOGGER.warn("WebhookBackupTask: output --> {}", input.getData());
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        LOGGER.warn("Backing up to {}...", backupWebhookUrl + "/" + filename);
        try {
            HttpRequests.getPostResponse(backupWebhookUrl + "/DataBackup", input.getData(), "application/json");
        } catch (IOException e) {
            LOGGER.warn("Backup failed due to {}", e);
        }
    }

    private void output(String input, String filename)
    {
        //LOGGER.warn("WebhookBackupTask: output --> {}", input.getData());
        final String backupWebhookUrl = System.getenv("BACKUP_WEBHOOK_URL");
        //LOGGER.warn("Backing up to {}...", backupWebhookUrl + "/FormBackup" + filename);
        LOGGER.warn("Backing up to {}...", backupWebhookUrl + filename);
        try {
            HttpRequests.getPostResponse(backupWebhookUrl + filename, input, "application/json");
        } catch (IOException e) {
            LOGGER.warn("Backup failed due to {}", e);
        }
    }
}
