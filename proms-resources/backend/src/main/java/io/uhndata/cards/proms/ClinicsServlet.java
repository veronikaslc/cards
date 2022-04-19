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
package io.uhndata.cards.proms;

import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingAllMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component(service = { Servlet.class })
@SlingServletResourceTypes(resourceTypes = { "cards/ClinicMappingFolder" }, extensions = {
    "addNew" }, methods = { "POST" })
public class ClinicsServlet extends SlingAllMethodsServlet
{
    private static final long serialVersionUID = -5555906093850253193L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ClinicsServlet.class);

    private static final String IMPORT_FACTORY_PID = "io.uhndata.cards.proms.internal.importer.ImportConfig";

    private static final String DESCRIPTION_FIELD = "description";

    private static final String PRIMARY_TYPE_FIELD = "jcr:primaryType";

    private final ThreadLocal<String> clinicName = new ThreadLocal<>();

    private final ThreadLocal<String> displayName = new ThreadLocal<>();

    private final ThreadLocal<String> sidebarLabel = new ThreadLocal<>();

    private final ThreadLocal<String> surveyID = new ThreadLocal<>();

    private final ThreadLocal<String> emergencyContact = new ThreadLocal<>();

    private final ThreadLocal<String> description = new ThreadLocal<>();

    private final ThreadLocal<String> idHash = new ThreadLocal<>();

    @Reference
    private ConfigurationAdmin configAdmin;

    // Custom exception for us to handle
    public class NoSuchSurveyException extends Exception
    {
        public NoSuchSurveyException(String errorMessage)
        {
            super(errorMessage);
        }
    }

    @Override
    public void doPost(final SlingHttpServletRequest request, final SlingHttpServletResponse response)
        throws IOException
    {
        // Create all of the necessary nodes
        final ResourceResolver resolver = request.getResourceResolver();
        this.getArguments(request);
        try {
            final Session session = resolver.adaptTo(Session.class);
            this.displayName.set(getUniqueDisplayName(resolver, this.displayName.get()));
            this.createClinicMapping(resolver);
            this.createSidebar(resolver);
            this.createDashboardView(resolver);
            this.createDashboardExtension(resolver);
            this.createDashboardViews(resolver);
            session.save();
        } catch (RepositoryException e) {
            this.returnError(response, e.getMessage());
        } catch (NoSuchSurveyException e) {
            this.returnError(response, e.getMessage());
        } catch (NullPointerException e) {
            this.returnError(response, e.getMessage());
        }

        // Grab the configuration to edit
        try {
            Configuration[] configs = this.configAdmin.listConfigurations(
                "(service.factoryPid=" + IMPORT_FACTORY_PID + ")");

            if (configs != null) {
                for (Configuration config : configs) {
                    this.insertNewClinic(config, this.clinicName.get());
                }
            }
        } catch (InvalidSyntaxException e) {
            // This can happen when the filter given to the listConfigurations call above is wrong
            // This shouldn't happen unless a typo was made in the value of IMPORT_FACTORY_PID
            this.returnError(response, "Invalid syntax in config search.");
        } catch (IOException e) {
            // This can happen while updating the properties of a configuration
            // Unknown how to handle this
            this.returnError(response, "Updating clinic configurations failed.");
        }
    }

    /**
     * Parse out the arguments from the SlingHttpServletRequest into threadlocal variables for parsing.
     *
     * @param request servlet request whose arguments we need to parse
     */
    private boolean getArguments(final SlingHttpServletRequest request)
    {
        this.clinicName.set(request.getParameter("clinicName"));
        this.displayName.set(request.getParameter("displayName"));
        this.sidebarLabel.set(request.getParameter("sidebarLabel"));
        this.surveyID.set(request.getParameter("surveyId"));
        this.emergencyContact.set(request.getParameter("emergencyContact"));
        this.description.set(request.getParameter("description"));
        this.idHash.set(Integer.toString(this.clinicName.get().hashCode()));
        return true;
    }

    /**
     * Get a unique display name for a cards:ClinicMapping node, appending numbers to the end if one already exists.
     *
     * @param resolver Resource resolver to use
     * @param displayName Given display name to check for duplicates for
     * @return Unique display name
     */
    public String getUniqueDisplayName(final ResourceResolver resolver, String displayName)
        throws RepositoryException
    {
        // Pre-sanitize the name
        String sanitizedName = displayName.replaceAll("'", "''");

        // Query for similar names
        String query = "SELECT * FROM [cards:ClinicMapping] as c WHERE c.'displayName' LIKE '"
            + sanitizedName + "%'";
        Iterator<Resource> results = resolver.findResources(query, "JCR-SQL2");

        // Determine what names currently exist
        Set<Integer> foundNames = new HashSet<>();
        boolean noNumberValid = true;
        Pattern numberRegex = Pattern.compile(displayName + " ([\\d]+)");
        while (results.hasNext()) {
            String name = results.next().adaptTo(Node.class).getProperty("displayName").getString();

            Matcher match = numberRegex.matcher(name);
            if (match.find()) {
                foundNames.add(Integer.parseInt(match.group(1)));
            } else if (displayName.equals(name)) {
                noNumberValid = false;
            }
        }

        // Determine if we can use the display name as-is
        if (noNumberValid) {
            return displayName;
        }

        // Find the first number i that works if we stick it after the display name
        for (int i = 1; i < foundNames.size(); i++) {
            if (!foundNames.contains(i)) {
                return displayName + " " + String.valueOf(i);
            }
        }
        return displayName + " " + String.valueOf(foundNames.size() + 1);
    }

    /**
     * Return an error code to the POST request, letting the user know that something is wrong.
     *
     * @param response object to send response through
     * @param reason reason to give to user
     */
    private void returnError(final SlingHttpServletResponse response, String reason)
    {
        LOGGER.error(reason);
        try {
            response.setStatus(SlingHttpServletResponse.SC_BAD_REQUEST);
            Writer out = response.getWriter();
            JsonGenerator generator = Json.createGenerator(out);
            generator.writeStartObject();
            generator.write("error", reason);
            generator.writeEnd();
            generator.flush();
        } catch (IOException e) {
            LOGGER.error("Furthermore, IOException occurred while returning response to user: {}", e.getMessage(), e);
        }
    }

    /**
     * Create a cards:ClinicMapping node.
     *
     * @param resolver Resource resolver to use
     */
    private void createClinicMapping(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/Proms/ClinicMapping");

        resolver.create(parentResource, this.idHash.get(), Map.of(
            "clinicName", this.clinicName.get(),
            "displayName", this.displayName.get(),
            "sidebarLabel", this.sidebarLabel.get(),
            "surveyID", this.surveyID.get(),
            "emergencyContact", this.emergencyContact.get(),
            ClinicsServlet.DESCRIPTION_FIELD, this.description.get(),
            ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:ClinicMapping"));
    }

    /**
     * Create a cards:Extension node for the sidebar.
     *
     * @param resolver Resource resolver to use
     */
    private void createSidebar(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/Extensions/Sidebar");
        resolver.create(parentResource, this.idHash.get(), Map.of(
            "cards:extensionPointId", "cards/coreUI/sidebar/entry",
            "cards:extensionName", this.sidebarLabel.get(),
            "cards:targetURL", "/content.html/Dashboard/" + this.idHash.get(),
            "cards:icon", "asset:proms-homepage.pmccIcon.js",
            "cards:defaultOrder", 10,
            ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:Extension"));
    }

    /**
     * Create a dashboard view for the new clinic.
     *
     * @param resolver Resource resolver to use
     */
    private void createDashboardView(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/Extensions/Views");
        resolver.create(parentResource, this.idHash.get() + "Dashboard", Map.of(
            "cards:extensionPointId", "cards/coreUI/view",
            "cards:extensionName", "Dashboard",
            "cards:extensionRenderURL", "asset:proms-homepage.PromsDashboard.js",
            "cards:targetURL", "/content.html/Dashboard",
            "cards:exactURLMatch", false,
            ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:Extension"));
    }

    /**
     * Create a dashboard extension for the new clinic.
     *
     * @param resolver Resource resolver to use
     */
    private void createDashboardExtension(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException
    {
        final Resource parentResource = resolver.getResource("/apps/cards/ExtensionPoints");
        resolver.create(parentResource, "DashboardViews" + this.idHash.get(), Map.of(
            ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:ExtensionPoint",
            "cards:extensionPointId", "proms/dashboard/" + this.idHash.get(),
            "cards:extensionPointName", this.displayName.get() + " questionnaires dashboard",
            "title", this.displayName.get(),
            ClinicsServlet.DESCRIPTION_FIELD, this.description.get(),
            "surveys", this.idHash.get()
            ));
    }

    /**
     * Create a dashboard view for each questionnaire in the survey set specified by the user.
     *
     * @param resolver Resource resolver to use
     */
    private void createDashboardViews(final ResourceResolver resolver)
        throws RepositoryException, PersistenceException, NoSuchSurveyException
    {
        // First, create the folder to hold the dashboard views
        final Resource dashboardViewFolder = resolver.getResource("/Extensions/DashboardViews/");
        Resource folder = resolver.create(dashboardViewFolder, this.idHash.get() + "View", Map.of(
            ClinicsServlet.PRIMARY_TYPE_FIELD, "sling:Folder"));

        // Create a bunch of survey views, one per clinic ID given
        final Resource surveys = resolver.getResource("/Proms/" + this.surveyID.get());
        if (surveys == null) {
            // This call is malformed: the survey ID given does not exist
            throw new NoSuchSurveyException(this.surveyID.get() + " is not an existing survey ID.");
        }
        int i = 0;
        for (final Resource questionnaireRef : surveys.getChildren()) {
            // First, we need to grab the surveys under that resource
            final Node questionnaireNode = questionnaireRef.adaptTo(Node.class)
                .getProperty("questionnaire").getNode();

            // Then, we need to grab the revelant details and make a new View
            resolver.create(folder, questionnaireNode.getProperty("title").getString(), Map.of(
                ClinicsServlet.PRIMARY_TYPE_FIELD, "cards:Extension",
                "cards:extensionPointId", "proms/dashboard/" + this.idHash.get(),
                "cards:extensionName", questionnaireNode.getProperty("title").getString() + " View",
                "cards:extensionRenderURL", "asset:proms-homepage.PromsView.js",
                "cards:defaultOrder", i++,
                "cards:data", questionnaireRef.getPath()
                ));
        }
    }

    /**
     * Update the clinic.names field of the given configuration with a new clinic name.
     *
     * @param config An OSGI config object for an instance of a proms ImportConfig
     * @param newClinicName An new clinic's name to add
     */
    public void insertNewClinic(Configuration config, String newClinicName) throws IOException
    {
        String[] clinicNames = (String[]) config.getProperties().get("clinic.names");
        String[] updatedClinicNames = Arrays.copyOf(clinicNames, clinicNames.length + 1);
        updatedClinicNames[clinicNames.length] = newClinicName;

        // Create a dictionary to contain the update request
        Dictionary<String, Object> updateDictionary = new Hashtable<String, Object>();
        updateDictionary.put("clinic.names", updatedClinicNames);
        config.update(updateDictionary);
    }
}
