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
package io.uhndata.cards.serialize.internal;

import java.util.function.Function;

import javax.jcr.Node;
import javax.jcr.Property;
import javax.jcr.RepositoryException;
import javax.json.JsonValue;

import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

import io.uhndata.cards.forms.api.FormUtils;
import io.uhndata.cards.serialize.spi.ResourceJsonProcessor;

/**
 * Remove any properties from the output that woulkd not be present in an importable questionnaire XML.
 * This processor is intended to be run alongside the following other processors:
 * * .deep
 * * .simple
 * * .-identify
 * * .nolinks
 * The name of this processor is {@code jsontoxml}.
 *
 * @version $Id$
 */
@Component(immediate = true)
public class JsonToXmlProcessor implements ResourceJsonProcessor
{
    @Reference
    private FormUtils formUtils;

    @Override
    public String getName()
    {
        return "jsontoxml";
    }

    @Override
    public int getPriority()
    {
        return 100;
    }

    @Override
    public JsonValue processProperty(final Node node, final Property property, final JsonValue input,
        final Function<Node, JsonValue> serializeNode)
    {
        try {
            switch (property.getName()) {
                case "jcr:created":
                case "jcr:createdBy":
                case "jcr:lastModified":
                case "jcr:lastModifiedBy":
                case "jcr:uuid":
                    // Skip most jcr nodes. Can't skip all as jcr:primaryType is still needed
                    return null;
                default:
                    return input;
            }
        } catch (RepositoryException e) {
            // Really shouldn't happen
        }
        return input;
    }
}
