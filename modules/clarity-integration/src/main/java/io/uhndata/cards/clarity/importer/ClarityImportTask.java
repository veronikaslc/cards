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

package io.uhndata.cards.clarity.importer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;
import javax.jcr.version.VersionManager;

import org.apache.commons.lang3.StringUtils;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.PersistenceException;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.ValueMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.utils.ThreadResourceResolverProvider;

/**
 * Query the Clarity server every so often to obtain all of the visits & patients that have appeared throughout the day.
 * This will patch over patient & visit information forms.
 *
 * @version $Id$
 */
@SuppressWarnings("checkstyle:ClassFanOutComplexity")
public class ClarityImportTask implements Runnable
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClarityImportTask.class);

    private static final String MAPPING_CONFIG = "/apps/cards/clarityImport";

    private static final String SUBJECT_TYPE_PROP = "subjectType";

    private static final String QUESTION_PROP = "question";

    private static final String QUESTIONNAIRE_PROP = "questionnaire";

    private static final String DATA_TYPE_PROP = "dataType";

    private static final String PRIMARY_TYPE_PROP = "jcr:primaryType";

    private static final String VALUE_PROP = "value";

    private final ThreadLocal<Map<String, String>> sqlColumnToDataType = ThreadLocal.withInitial(HashMap::new);

    private final ThreadLocal<List<String>> nodesToCheckin = ThreadLocal.withInitial(LinkedList::new);

    private final ThreadLocal<VersionManager> versionManager = new ThreadLocal<>();

    private final ThreadLocal<ClaritySubjectMapping> clarityImportConfiguration =
        ThreadLocal.withInitial(ClaritySubjectMapping::new);

    private final ThreadResourceResolverProvider rrp;

    // Helper classes

    private enum QuestionType
    {
        DATE,
        STRING,
        BOOLEAN,
        CLINIC
    }

    private static final class ClaritySubjectMapping
    {
        private final String name;

        private final String path;

        private final String subjectIdColumn;

        private final String subjectType;

        private final List<ClaritySubjectMapping> childSubjects;

        private final List<ClarityQuestionnaireMapping> questionnaires;

        /**
         * Constructor used for the root of the mapping tree, an empty mapping with the only purpose of holding child
         * subject mappings.
         */
        ClaritySubjectMapping()
        {
            this("", "", "", "");
        }

        ClaritySubjectMapping(String name, String subjectIdColumn, String subjectType, String path)
        {
            this.name = name;
            this.path = path;
            this.subjectIdColumn = subjectIdColumn;
            this.subjectType = subjectType;
            this.childSubjects = new LinkedList<>();
            this.questionnaires = new LinkedList<>();
        }

        private void addChildSubject(ClaritySubjectMapping mapping)
        {
            this.childSubjects.add(mapping);
        }

        private void addQuestionnaire(ClarityQuestionnaireMapping mapping)
        {
            this.questionnaires.add(mapping);
        }
    }

    private static final class ClarityQuestionnaireMapping
    {
        private final String name;

        private final boolean updatesExisting;

        private final List<ClarityQuestionMapping> questions;

        ClarityQuestionnaireMapping(final String name, final boolean updatesExisting)
        {
            this.name = name;
            this.updatesExisting = updatesExisting;
            this.questions = new LinkedList<>();
        }

        private void addQuestion(final ClarityQuestionMapping mapping)
        {
            this.questions.add(mapping);
        }

        private Resource getQuestionnaireResource(final ResourceResolver resolver)
        {
            return resolver.resolve("/Questionnaires/" + this.name);
        }
    }

    private static final class ClarityQuestionMapping
    {
        private final String name;

        private final String question;

        private final String column;

        private final QuestionType questionType;

        private final boolean computed;

        ClarityQuestionMapping(final String name, final String question, final String column,
            final QuestionType questionType, final boolean computed)
        {
            this.name = name;
            this.question = question;
            this.column = column;
            this.questionType = questionType;
            this.computed = computed;
        }
    }

    /** Provides access to resources. */
    private final ResourceResolverFactory resolverFactory;

    ClarityImportTask(final ResourceResolverFactory resolverFactory, final ThreadResourceResolverProvider rrp)
    {
        this.resolverFactory = resolverFactory;
        this.rrp = rrp;
    }

    // The entry point for running an import

    @Override
    public void run()
    {
        LOGGER.info("Running ClarityImportTask");

        String connectionUrl =
            "jdbc:sqlserver://" + System.getenv("CLARITY_SQL_SERVER") + ";"
                + "user=" + System.getenv("CLARITY_SQL_USERNAME") + ";"
                + "password=" + System.getenv("CLARITY_SQL_PASSWORD") + ";"
                + "encrypt=" + System.getenv("CLARITY_SQL_ENCRYPT") + ";";

        // Connect via SQL to the server
        boolean mustPopResolver = false;
        try (Connection connection = DriverManager.getConnection(connectionUrl);
            ResourceResolver resolver = this.resolverFactory.getServiceResourceResolver(null)) {
            this.rrp.push(resolver);
            mustPopResolver = true;

            final Session session = resolver.adaptTo(Session.class);
            this.versionManager.set(session.getWorkspace().getVersionManager());

            populateClarityImportConfiguration(resolver, resolver.resolve(MAPPING_CONFIG),
                this.clarityImportConfiguration.get());

            // Generate and perform the query
            PreparedStatement statement = connection.prepareStatement(generateClarityQuery());
            ResultSet results = statement.executeQuery();

            while (results.next()) {
                // Create the Subjects and Forms as is needed
                createFormsAndSubjects(resolver, results);
            }

            session.save();
            this.nodesToCheckin.get().forEach(node -> {
                try {
                    this.versionManager.get().checkin(node);
                } catch (final RepositoryException e) {
                    LOGGER.warn("Failed to check in node {}: {}", node, e.getMessage(), e);
                }
            });
        } catch (SQLException e) {
            LOGGER.error("Failed to connect to SQL: {}", e.getMessage(), e);
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while writing results: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Error during Clarity import: {}", e.getMessage(), e);
        } catch (PersistenceException e) {
            LOGGER.error("PersistenceException while importing data to JCR", e);
        } catch (ParseException e) {
            LOGGER.error("ParseException while importing data to JCR");
        } finally {
            // Cleanup all ThreadLocals
            this.nodesToCheckin.remove();
            this.versionManager.remove();
            this.clarityImportConfiguration.remove();
            this.sqlColumnToDataType.remove();
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    // Methods for preparing the Clarity SQL query

    private void populateClarityImportConfiguration(ResourceResolver resolver, Resource configNode,
        ClaritySubjectMapping clarityConf)
    {
        for (Resource configChildNode : configNode.getChildren()) {
            String configChildNodeType = configChildNode.getValueMap().get(PRIMARY_TYPE_PROP, "");
            if ("cards:claritySubjectMapping".equals(configChildNodeType)) {
                String subjectNodeType = configChildNode.getValueMap().get(SUBJECT_TYPE_PROP, "");
                String subjectIDColumnLabel = configChildNode.getValueMap().get("subjectIDColumn", "");

                // Add this cards:claritySubjectMapping to the local Java data structures
                ClaritySubjectMapping claritySubjectMapping = new ClaritySubjectMapping(configChildNode.getName(),
                    subjectIDColumnLabel, subjectNodeType, clarityConf.path + "/" + configChildNode.getName());

                // Iterate through all Questionnaires that are to be created
                Resource questionnaires = configChildNode.getChild("questionnaires");
                if (questionnaires != null) {
                    // Add the questionnaires associated with this subject to the local Java data structures
                    for (Resource questionnaire : questionnaires.getChildren()) {
                        boolean updatesExisting = questionnaire.getValueMap().get("updatesExisting", false);
                        ClarityQuestionnaireMapping clarityQuestionnaireMapping = new ClarityQuestionnaireMapping(
                            questionnaire.getName(), updatesExisting);

                        for (Resource questionMapping : questionnaire.getChildren()) {
                            // Add the questions associated with this questionnaire to the local Java data structures
                            String questionPath = questionMapping.getValueMap().get(QUESTION_PROP, "");
                            String column = questionMapping.getValueMap().get("column", "");
                            boolean computed = questionMapping.getValueMap().get("computed", Boolean.FALSE);
                            Resource questionResource = resolver.resolve(questionPath);
                            QuestionType qType = this.getQuestionType(questionResource);
                            ClarityQuestionMapping clarityQuestionMapping = new ClarityQuestionMapping(
                                questionMapping.getName(), questionPath, column, qType, computed);
                            clarityQuestionnaireMapping.addQuestion(clarityQuestionMapping);

                            // Populate this.sqlColumnToDataType
                            if (!clarityQuestionMapping.computed) {
                                this.sqlColumnToDataType.get().put(column,
                                    questionResource.getValueMap().get(DATA_TYPE_PROP, ""));
                            }
                        }
                        claritySubjectMapping.addQuestionnaire(clarityQuestionnaireMapping);
                    }
                }

                // Recursively go through the childSubjects
                Resource childSubjects = configChildNode.getChild("childSubjects");
                if (childSubjects != null) {
                    populateClarityImportConfiguration(resolver, childSubjects, claritySubjectMapping);
                }

                // Attach claritySubjectMapping as a child of clarityConf
                clarityConf.addChildSubject(claritySubjectMapping);
            }
        }
    }

    /***
     * Identify the question type from a question Resource.
     *
     * @param question the cards:Question node to analyze
     * @return A QuestionType corresponding to the given cards:Question node
     */
    private QuestionType getQuestionType(Resource question)
    {
        ValueMap questionProps = question.getValueMap();
        String dataType = questionProps.containsKey(DATA_TYPE_PROP) ? questionProps.get(DATA_TYPE_PROP, "") : "";
        String primaryType = questionProps.containsKey("primaryType") ? questionProps.get("primaryType", "") : "";
        if ("date".equals(dataType)) {
            return QuestionType.DATE;
        } else if ("boolean".equals(dataType)) {
            return QuestionType.BOOLEAN;
        } else if ("cards:ClinicMapping".equals(primaryType)) {
            return QuestionType.CLINIC;
        }
        return QuestionType.STRING;
    }

    private String generateClarityQuery()
    {
        String queryString = "SELECT ";
        Iterator<Map.Entry<String, String>> columnsIterator = this.sqlColumnToDataType.get().entrySet().iterator();
        while (columnsIterator.hasNext()) {
            Map.Entry<String, String> col = columnsIterator.next();
            if ("date".equals(col.getValue())) {
                queryString += "FORMAT(" + col.getKey() + ", 'yyyy-MM-dd HH:mm:ss') AS " + col.getKey();
            } else {
                queryString += col.getKey();
            }
            if (columnsIterator.hasNext()) {
                queryString += ", ";
            }
        }
        queryString += " FROM path.CL_EP_IP_EMAIL_CONSENT_IN_LAST_7_DAYS";
        queryString += " WHERE CAST(LoadTime AS DATE) = CAST(GETDATE()-1 AS DATE);";

        return queryString;
    }

    // Methods for handling a result row

    private void createFormsAndSubjects(ResourceResolver resolver, ResultSet sqlRow)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        // Recursively move down the local Clarity Import configuration tree
        walkThroughLocalConfig(resolver, sqlRow, this.clarityImportConfiguration.get(),
            resolver.resolve("/Subjects"));
    }

    private void walkThroughLocalConfig(ResourceResolver resolver, ResultSet sqlRow,
        ClaritySubjectMapping subjectMapping, Resource subjectParent)
        throws ParseException, PersistenceException, RepositoryException, SQLException
    {
        for (ClaritySubjectMapping childSubjectMapping : subjectMapping.childSubjects) {
            // Get or create the subject
            String subjectNodeType = childSubjectMapping.subjectType;
            String subjectIDColumnLabel = childSubjectMapping.subjectIdColumn;
            String subjectIDColumnValue = (!"".equals(subjectIDColumnLabel))
                ? sqlRow.getString(subjectIDColumnLabel) : UUID.randomUUID().toString();

            Resource newSubjectParent = getOrCreateSubject(subjectIDColumnValue,
                subjectNodeType, resolver, subjectParent);
            resolver.commit();

            for (ClarityQuestionnaireMapping questionnaireMapping : childSubjectMapping.questionnaires) {
                boolean updatesExisting = questionnaireMapping.updatesExisting;
                Resource formNode = getFormForSubject(resolver, questionnaireMapping.getQuestionnaireResource(resolver),
                    newSubjectParent);

                if (updatesExisting && (formNode != null)) {
                    // Update the answers to an existing Form
                    updateExistingForm(resolver, formNode, questionnaireMapping, sqlRow);
                } else {
                    // Create a new Form
                    formNode = createForm(resolver, questionnaireMapping.getQuestionnaireResource(resolver),
                        newSubjectParent);

                    // Attach all the Answer nodes to it
                    populateEmptyForm(resolver, formNode, questionnaireMapping, sqlRow);

                    // Commit the changes to the JCR
                    resolver.commit();

                    // Perform a JCR check-in to this cards:Form node once the import is completed
                    this.nodesToCheckin.get().add(formNode.getPath());
                }
            }
            walkThroughLocalConfig(resolver, sqlRow, childSubjectMapping, newSubjectParent);
        }
    }

    // Methods for storing subjects

    /**
     * Grab a subject of the specified type, or create it if it doesn't exist.
     *
     * @param identifier Identifier to use for the subject
     * @param subjectTypePath path to a SubjectType node for this subject
     * @param resolver resource resolver to use
     * @param parent parent Resource if this is a child of that resource, or null
     * @return A Subject resource
     */
    private Resource getOrCreateSubject(final String identifier, final String subjectTypePath,
        final ResourceResolver resolver, final Resource parent) throws RepositoryException, PersistenceException
    {
        String subjectMatchQuery = String.format(
            "SELECT * FROM [cards:Subject] as subject WHERE subject.'identifier'='%s' option (index tag property)",
            identifier);
        resolver.refresh();
        final Iterator<Resource> subjectResourceIter = resolver.findResources(subjectMatchQuery, "JCR-SQL2");
        if (subjectResourceIter.hasNext()) {
            final Resource subjectResource = subjectResourceIter.next();
            this.versionManager.get().checkout(subjectResource.getPath());
            this.nodesToCheckin.get().add(subjectResource.getPath());
            return subjectResource;
        } else {
            Resource parentResource = parent;
            if (parentResource == null) {
                parentResource = resolver.getResource("/Subjects/");
            }
            final Resource patientType = resolver.getResource(subjectTypePath);
            final Resource newSubject = resolver.create(parentResource, identifier, Map.of(
                ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Subject",
                "identifier", identifier,
                "type", patientType.adaptTo(Node.class)));
            this.nodesToCheckin.get().add(newSubject.getPath());
            return newSubject;
        }
    }

    private Resource getFormForSubject(ResourceResolver resolver, Resource questionnaireResource,
        Resource subjectResource)
    {
        // Get the jcr:uuid associated with questionnairePath
        String questionnaireUUID = questionnaireResource.getValueMap().get("jcr:uuid", "");

        // Get the jcr:uuid associated with subjectPath
        String subjectUUID = subjectResource.getValueMap().get("jcr:uuid", "");

        // Query for a cards:Form node with the specified questionnaire and subject
        String formMatchQuery = String.format(
            "SELECT * FROM [cards:Form] as form WHERE"
                + " form.'subject'='%s'"
                + " AND form.'questionnaire'='%s'"
                + " option (index tag property)",
            subjectUUID,
            questionnaireUUID);

        resolver.refresh();
        final Iterator<Resource> formResourceIter = resolver.findResources(formMatchQuery, "JCR-SQL2");
        if (formResourceIter.hasNext()) {
            return formResourceIter.next();
        }

        return null;
    }

    // Methods for updating an existing form

    private void updateExistingForm(ResourceResolver resolver, Resource formNode,
        ClarityQuestionnaireMapping questionnaireMapping, ResultSet sqlRow)
        throws ParseException, RepositoryException, SQLException
    {
        this.versionManager.get().checkout(formNode.getPath());
        for (ClarityQuestionMapping questionMapping : questionnaireMapping.questions) {
            if (StringUtils.isBlank(questionMapping.question)) {
                continue;
            }
            replaceFormAnswer(resolver, formNode,
                generateAnswerNodeProperties(resolver, questionMapping, sqlRow));
        }
        // Perform a JCR check-in to this cards:Form node once the import is completed
        this.nodesToCheckin.get().add(formNode.getPath());
    }

    private void replaceFormAnswer(final ResourceResolver resolver, final Resource form,
        final Map<String, Object> props) throws RepositoryException
    {
        final String questionUUID = ((Node) props.get(QUESTION_PROP)).getIdentifier();
        for (Resource answer : form.getChildren()) {
            String thisAnswersQuestionUUID = answer.getValueMap().get(QUESTION_PROP, "");
            if (questionUUID.equals(thisAnswersQuestionUUID)) {
                // Now, copy the value from the props Map into the cards:Answer JCR node
                Object newValue = props.get(ClarityImportTask.VALUE_PROP);
                if (newValue instanceof String) {
                    answer.adaptTo(Node.class).setProperty(ClarityImportTask.VALUE_PROP, (String) newValue);
                } else if (newValue instanceof Calendar) {
                    answer.adaptTo(Node.class).setProperty(ClarityImportTask.VALUE_PROP, (Calendar) newValue);
                } else if (newValue instanceof Integer) {
                    answer.adaptTo(Node.class).setProperty(ClarityImportTask.VALUE_PROP,
                        ((Integer) newValue).longValue());
                }
            }
        }
    }

    // Methods for storing a new form

    private Resource createForm(ResourceResolver resolver, Resource questionnaire, Resource subject)
        throws PersistenceException
    {
        return resolver.create(resolver.resolve("/Forms"), UUID.randomUUID().toString(), Map.of(
            ClarityImportTask.PRIMARY_TYPE_PROP, "cards:Form",
            QUESTIONNAIRE_PROP, questionnaire.adaptTo(Node.class),
            "subject", subject.adaptTo(Node.class)));
    }

    private void populateEmptyForm(ResourceResolver resolver, Resource formNode,
        ClarityQuestionnaireMapping questionnaireMapping, ResultSet sqlRow)
        throws ParseException, PersistenceException, SQLException
    {
        for (ClarityQuestionMapping questionMapping : questionnaireMapping.questions) {
            if (StringUtils.isBlank(questionMapping.question)) {
                continue;
            }
            // Create the answer node in the JCR
            resolver.create(formNode, UUID.randomUUID().toString(),
                generateAnswerNodeProperties(resolver, questionMapping, sqlRow));
        }
    }

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver,
        final ClarityQuestionMapping questionMapping, final ResultSet sqlRow) throws ParseException, SQLException
    {
        String questionPath = questionMapping.question;
        String column = questionMapping.column;
        String answerValue = sqlRow.getString(column);
        QuestionType qType = questionMapping.questionType;
        return generateAnswerNodeProperties(resolver, qType, questionPath, answerValue);
    }

    private Map<String, Object> generateAnswerNodeProperties(final ResourceResolver resolver, final QuestionType qType,
        final String questionPath, final String answerValue) throws ParseException
    {
        Map<String, Object> props = new HashMap<>();
        Resource questionResource = resolver.resolve(questionPath);

        props.put(QUESTION_PROP, questionResource.adaptTo(Node.class));

        if (qType == QuestionType.STRING) {
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
            props.put(ClarityImportTask.VALUE_PROP, answerValue == null ? "" : answerValue);
        } else if (qType == QuestionType.DATE) {
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:DateAnswer");
            SimpleDateFormat clarityDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
            Date date = clarityDateFormat.parse(answerValue);
            if (date != null) {
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(date);
                props.put(ClarityImportTask.VALUE_PROP, calendar);
            } else {
                LOGGER.warn("Could not parse date");
            }
        } else if (qType == QuestionType.BOOLEAN) {
            // Note that the MS-SQL database doesn't save booleans as true/false
            // So instead we have to check if it is Yes or No
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:BooleanAnswer");
            props.put(ClarityImportTask.VALUE_PROP, "Yes".equals(answerValue) ? 1 : 0);
        } else if (qType == QuestionType.CLINIC) {
            // This is similar to a string, except we transform the output to look at the ClinicMapping node
            props.put(ClarityImportTask.PRIMARY_TYPE_PROP, "cards:TextAnswer");
            props.put(ClarityImportTask.VALUE_PROP,
                answerValue == null ? "" : "/Survey/ClinicMapping/" + String.valueOf(answerValue));
        } else {
            LOGGER.warn("Unsupported question type: " + qType);
        }

        // Fix any instances where VALUE should be transformed into [VALUE]
        props = fixAnswerMultiValues(props, questionResource);

        return props;
    }

    /*
     * If the corresponding cards:Question has a maxAnswers value != 1 set the "value" property of this answer node to a
     * single-element list containing only the type-casted answer value.
     */
    private Map<String, Object> fixAnswerMultiValues(Map<String, Object> props, Resource questionResource)
    {
        int maxAnswers = questionResource.getValueMap().get("maxAnswers", 1);
        Object valuePropValue = props.get(ClarityImportTask.VALUE_PROP);
        if ((maxAnswers != 1) && (valuePropValue != null)) {
            // Make this value a single element "multi-valued" property
            Object[] multiValues = { valuePropValue };
            props.put(ClarityImportTask.VALUE_PROP, multiValues);
        }
        return props;
    }
}
