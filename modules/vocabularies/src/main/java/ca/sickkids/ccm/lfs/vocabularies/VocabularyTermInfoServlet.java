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
package ca.sickkids.ccm.lfs.vocabularies;

import java.io.IOException;
import java.io.Writer;
import java.util.Iterator;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonValue.ValueType;
import javax.json.stream.JsonGenerator;
import javax.servlet.Servlet;

import org.apache.sling.api.SlingHttpServletRequest;
import org.apache.sling.api.SlingHttpServletResponse;
import org.apache.sling.api.resource.Resource;
import org.apache.sling.api.resource.ResourceResolver;
import org.apache.sling.api.servlets.SlingSafeMethodsServlet;
import org.apache.sling.servlets.annotations.SlingServletResourceTypes;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.log.LogService;

/**
 * A servlet that extends the normal JSON object of a VocabularyTermNode by replacing IDs with objects containing their
 * name and ID.
 *
 * @version $Id$
 */
@Component(service = { Servlet.class })
@SlingServletResourceTypes(
    resourceTypes = { "lfs/VocabularyTerm" },
    methods = { "GET" },
    selectors = { "info" }
    )
public class VocabularyTermInfoServlet extends SlingSafeMethodsServlet
{
    private static final long serialVersionUID = -8244429250995709300L;

    private static final String HAS_CHILDREN_PROPERTY = "lfs:hasChildren";

    private static final String CHILDREN_PROPERTY = "lfs:children";

    /* During tests, copying over every child tends to bloat the response, so we only copy a subset of the data */
    private static final String[] KEYS_TO_COPY = {"id", "name"};

    @Reference
    private LogService logger;

    @Override
    public void doGet(final SlingHttpServletRequest request, final SlingHttpServletResponse response) throws IOException
    {
        Resource vocab = request.getResource().getParent();
        ResourceResolver resolver = request.getResourceResolver();

        // Our normal output would be ourselves as a JSONObject
        JsonObject json = request.getResource().adaptTo(JsonObject.class);
        response.setContentType("application/json");
        final Writer out = response.getWriter();
        try (JsonGenerator jsonGen = Json.createGenerator(out)) {
            jsonGen.writeStartObject();

            // Loop through our properties
            Set<String> keys = json.keySet();
            for (String key : keys) {
                JsonValue value = json.get(key);
                if (value instanceof JsonArray) {
                    // Any arrays should be iterated through and written
                    jsonGen.write(key, this.processArray((JsonArray) value, vocab, resolver));
                } else {
                    // Anything else should be left as-is
                    jsonGen.write(key, value);
                }
            }

            // Check to see if we ourselves have children, and add it to the result
            boolean hasChildren = false;
            Iterator<Resource> children = getVocabularyTermChildren(
                request.getResource().adaptTo(JsonObject.class),
                resolver
                );
            jsonGen.writeStartArray(CHILDREN_PROPERTY);
            while (children.hasNext()) {
                hasChildren = true;
                Resource child = children.next();
                jsonGen.write(populateChildren(child.adaptTo(JsonObject.class), resolver));
            }
            jsonGen.writeEnd();
            jsonGen.write(HAS_CHILDREN_PROPERTY, hasChildren);

            jsonGen.writeEnd().flush();
        }
    }

    /**
     * Process the given array, following references (if any). Fill in an additional lfs:hasChildren field
     * if the node has children.
     *
     * @param array Array to iterate through
     * @param vocab Vocabulary object whose children are the vocabulary terms
     * @param resolver A reference to a ResourceResolver
     * @return The input array, but any strings whose names are vocabulary terms are replaced with the term itself
     */
    private JsonValue processArray(JsonArray array, Resource vocab, ResourceResolver resolver)
    {
        JsonArrayBuilder builder = Json.createArrayBuilder();
        for (int i = 0; i < array.size(); i++) {
            JsonValue value = array.get(i);

            // If this value is a string, attempt to find it within this vocabulary
            if (value.getValueType() == ValueType.STRING) {
                // Parse out the name of the node, removing colons
                JsonString potentialNameString = (JsonString) value;
                String vocabTermNodeName = potentialNameString.getString();
                vocabTermNodeName = vocabTermNodeName.replaceAll(":", "");

                Resource linkedValue = resolver.getResource(vocab, "./" + vocabTermNodeName);
                if (linkedValue == null) {
                    // We couldn't load the parent resource; leave it as is
                    builder.add(value);
                } else {
                    JsonObject linkedObject = linkedValue.adaptTo(JsonObject.class);
                    builder.add(populateChildren(linkedObject, resolver));
                }
            } else {
                // This is not a string, leave it as is
                builder.add(value);
            }
        }
        return builder.build();
    }

    private JsonObject populateChildren(JsonObject resource, ResourceResolver resolver)
    {
        // Add child terms
        Iterator<Resource> children = getVocabularyTermChildren(resource, resolver);

        // Copy the resource but add lfs:hasChildren to it
        JsonObjectBuilder objectCopier = Json.createObjectBuilder();
        for (String key : KEYS_TO_COPY) {
            objectCopier.add(key, resource.get(key));
        }
        objectCopier.add(HAS_CHILDREN_PROPERTY, children.hasNext());

        // Copy over our children to lfs:Children
        JsonArrayBuilder childrenBuilder = Json.createArrayBuilder();
        while (children.hasNext()) {
            Resource child = children.next();

            // Copying over the entire child bloats the response, so add only a subset of
            // the child's properties
            JsonObject childJson = child.adaptTo(JsonObject.class);
            JsonObjectBuilder truncatedChild = Json.createObjectBuilder();
            for (String key : KEYS_TO_COPY) {
                truncatedChild.add(key, childJson.get(key));
            }

            childrenBuilder.add(truncatedChild.build());
        }
        objectCopier.add(CHILDREN_PROPERTY, childrenBuilder.build());

        return objectCopier.build();
    }

    /**
     * Determine if the given resource has children or not.
     *
     * @param resource The JsonObject to obtain the children of. Must have a child with key "id"
     * @param resolver A reference to a ResourceResolver to use
     * @return An iterator over the resource's children
     */
    private Iterator<Resource> getVocabularyTermChildren(JsonObject resource, ResourceResolver resolver)
    {
        // Check to see if this resource has children
        String oakQuery = String.format(
            "SELECT * FROM [lfs:VocabularyTerm] AS a WHERE a.parents = '%s'",
            resource.getString("id")
            );
        return resolver.findResources(oakQuery, "JCR-SQL2");
    }
}
