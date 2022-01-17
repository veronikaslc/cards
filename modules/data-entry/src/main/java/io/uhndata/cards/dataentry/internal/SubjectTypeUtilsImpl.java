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
package io.uhndata.cards.dataentry.internal;

import javax.jcr.Node;
import javax.jcr.RepositoryException;

import org.apache.jackrabbit.oak.spi.state.NodeBuilder;
import org.apache.jackrabbit.oak.spi.state.NodeState;
import org.apache.sling.api.resource.ResourceResolverFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.FieldOption;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.osgi.service.component.annotations.ReferencePolicyOption;

import io.uhndata.cards.dataentry.api.SubjectTypeUtils;

/**
 * Basic utilities for working with SubjectType data.
 *
 * @version $Id$
 */
@Component
public final class SubjectTypeUtilsImpl extends AbstractNodeUtils implements SubjectTypeUtils
{
    @Reference(fieldOption = FieldOption.REPLACE, cardinality = ReferenceCardinality.OPTIONAL,
        policyOption = ReferencePolicyOption.GREEDY)
    private ResourceResolverFactory rrf;

    // Subject methods

    @Override
    public Node getSubjectType(final String identifier)
    {
        final Node result = getNodeByIdentifier(identifier, getSession(this.rrf));
        return isSubjectType(result) ? result : null;
    }

    @Override
    public boolean isSubjectType(final Node node)
    {
        return isNodeType(node, SUBJECT_TYPE_NODETYPE);
    }

    @Override
    public boolean isSubjectType(final NodeBuilder node)
    {
        return node == null ? false : isSubjectType(node.getNodeState());
    }

    @Override
    public boolean isSubjectType(final NodeState node)
    {
        return isNodeType(node, SUBJECT_TYPE_NODETYPE, getSession(this.rrf));
    }

    @Override
    public String getLabel(final Node subjectType)
    {
        try {
            if (isSubjectType(subjectType) && subjectType.hasProperty(LABEL_PROPERTY)) {
                return subjectType.getProperty(LABEL_PROPERTY).getString();
            }
        } catch (final RepositoryException e) {
            // Should not happen
        }
        return null;
    }
}
