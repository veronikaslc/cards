/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.uhndata.cards.patients.internal;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Map;

import javax.jcr.Node;
import javax.jcr.RepositoryException;
import javax.jcr.Session;

import org.apache.jackrabbit.oak.spi.commit.Editor;
import org.apache.sling.api.resource.LoginException;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.apache.sling.api.resource.observation.ResourceChange;
import org.apache.sling.api.resource.observation.ResourceChangeListener;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.forms.api.QuestionnaireUtils;
import io.uhndata.cards.resolverProvider.ThreadResourceResolverProvider;

/**
 * An {@link Editor} records when the patient proceeds to open the survey first time.
 *
 * @version $Id$
 */
public class SurveyFirstOpenedListener implements ResourceChangeListener
{
    private static final Logger LOGGER = LoggerFactory.getLogger(SurveyFirstOpenedListener.class);

    private static final String OPENED_PROP = "survey_opened";

    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    @Reference
    private ThreadResourceResolverProvider rrp;

    @Reference
    private QuestionnaireUtils questionnaireUtils;

    @Reference
    private FormUtils formUtils;

    @Override
    public void onChange(List<ResourceChange> changes) {
        changes.forEach(this::handleEvent);
    }

    void handleEvent(ResourceChange change)
    {
        // Check that if the property was changed by user
        final String userID = change.getUserId();
        final Boolean isUser = "patient".equals(userID) || "guest-patient".equals(userID);
        if (!isUser) {
            return;
        }


        boolean mustPopResolver = false;
        try (ResourceResolver localResolver = this.rrf
            .getServiceResourceResolver(Map.of(ResourceResolverFactory.SUBSERVICE, "SurveyFirstOpenedEditor"))) {
            this.rrp.push(localResolver);
            mustPopResolver = true;

            // TODO check if the form is part of the survey events questionnaire set
            Node node = getCurrentNode(localResolver.adaptTo(Session.class), change.getPath());
            if (!this.formUtils.isForm(node)) {
                return;
            }

            final Session session = localResolver.adaptTo(Session.class);
            //TODO change next line to get surveyForm via *new* method
            final Node surveyForm = this.formUtils.getForm(node);
            final Node questionnaire = this.formUtils.getQuestionnaire(surveyForm);

            if (surveyForm != null && "/Questionnaires/Survey events".equals(questionnaire.getPath())) {
                // If the form is for the survey events questionnaire, update the survey opened date
                final Node question = this.questionnaireUtils.getQuestion(questionnaire, OPENED_PROP);
                final Node answer = this.formUtils.getAnswer(surveyForm, question);
                updateSurveyOpenedDate(surveyForm, session, question, answer);
            }
        } catch (LoginException e) {
            LOGGER.error("Could not find service user while writing results: {}", e.getMessage(), e);
        } catch (RepositoryException e) {
            LOGGER.error("Error during updating Survey events first opened time: {}", e.getMessage(), e);
        } finally {
            if (mustPopResolver) {
                this.rrp.pop();
            }
        }
    }

    private void updateSurveyOpenedDate(final Node form, final Session session, final Node question,
            final Node answer) throws RepositoryException
    {
        if (this.formUtils.getValue(answer) != null) {
            return;
        }

        boolean checkinNeeded = false;
        try {
            checkinNeeded = checkoutIfNeeded(form, session);

            final String format = question.getProperty("dateFormat").getString();
            answer.setProperty("value", new SimpleDateFormat(format).format(Calendar.getInstance().getTime()));

            checkinNeeded |= save(form, session);
        } catch (final RepositoryException e) {
            LOGGER.error("Failed to obtain form data: {}", e.getMessage(), e);
        } finally {
            if (checkinNeeded) {
                checkin(form, session);
            }
        }
    }

    private boolean checkoutIfNeeded(final Node form, final Session session) throws RepositoryException
    {
        session.refresh(true);
        if (!form.isCheckedOut()) {
            session.getWorkspace().getVersionManager().checkout(form.getPath());
            return true;
        }
        return false;
    }

    private void checkin(final Node form, final Session session)
    {
        try {
            session.getWorkspace().getVersionManager().checkin(form.getPath());
        } catch (final RepositoryException e) {
            LOGGER.warn("Failed check in the form: {}", e.getMessage(), e);
        }
    }

    private Node getCurrentNode(final Session session, String nodePath)
    {
        try {
            if (session.nodeExists(nodePath)) {
                return session.getNode(nodePath);
            }
        } catch (RepositoryException e) {
            // just return null
        }
        return null;
    }

    private boolean save(final Node form, final Session session) throws RepositoryException
    {
        try {
            session.save();
            return false;
        } catch (RepositoryException e) {
            boolean checkedOut = checkoutIfNeeded(form, session);
            session.save();
            return checkedOut;
        }
    }
}
