/*
 * (C) Copyright 2006-2019 Nuxeo (http://nuxeo.com/) and others.
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
 *     Nuxeo - initial API and implementation
 */

package org.nuxeo.ecm.core.schema;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.nuxeo.common.xmap.annotation.XNode;
import org.nuxeo.common.xmap.annotation.XNodeList;
import org.nuxeo.common.xmap.annotation.XObject;
import org.nuxeo.runtime.model.Descriptor;

/**
 * Document Type Descriptor.
 * <p>
 * Can be used to delay document type registration when not all prerequisites are met (e.g. supertype was not yet
 * registered).
 * <p>
 * In this case the descriptor containing all the information needed to register the document is put in a queue waiting
 * for the prerequisites to be met.
 *
 * @author <a href="mailto:bs@nuxeo.com">Bogdan Stefanescu</a>
 */
@XObject("doctype")
public class DocumentTypeDescriptor implements Descriptor {

    @XNode("@id")
    public String id;

    @XNode("@name")
    public String name;

    @XNodeList(value = "schema", type = SchemaDescriptor[].class, componentType = SchemaDescriptor.class)
    public SchemaDescriptor[] schemas;

    @XNode("@extends")
    public String superTypeName;

    @XNodeList(value = "facet@name", type = String[].class, componentType = String.class)
    public String[] facets;

    @XNode("prefetch")
    public String prefetch;

    @XNode("@append")
    public boolean append = false;

    /**
     * @since 11.1
     *
     * Allows to exclude the doctype from copy operations.
     */
    @XNode("@excludeFromCopy")
    public Boolean excludeFromCopy;

    @XNodeList(value = "subtypes/type", type = String[].class, componentType = String.class)
    public String[] subtypes = new String[0];

    @XNodeList(value = "subtypes-forbidden/type", type = String[].class, componentType = String.class)
    public String[] forbiddenSubtypes = new String[0];

    public DocumentTypeDescriptor() {
    }

    public DocumentTypeDescriptor(String superTypeName, String name, SchemaDescriptor[] schemas, String[] facets) {
        this.name = name;
        this.superTypeName = superTypeName;
        this.schemas = schemas;
        this.facets = facets;
    }

    public DocumentTypeDescriptor(String superTypeName, String name, SchemaDescriptor[] schemas, String[] facets,
                                  String[] subtypes, String[] forbiddenSubtypes) {
        this(superTypeName, name, schemas, facets);
        this.subtypes = subtypes;
        this.forbiddenSubtypes = forbiddenSubtypes;
    }

    @Override
    public String toString() {
        return "DocType: " + name;
    }

    public DocumentTypeDescriptor clone() {
        DocumentTypeDescriptor clone = new DocumentTypeDescriptor();
        clone.name = name;
        clone.schemas = schemas;
        clone.superTypeName = superTypeName;
        clone.facets = facets;
        clone.prefetch = prefetch;
        clone.append = append;
        clone.excludeFromCopy = excludeFromCopy;
        clone.subtypes = subtypes;
        clone.forbiddenSubtypes = forbiddenSubtypes;
        return clone;
    }

    @Override
    public Descriptor merge(Descriptor o) {
        DocumentTypeDescriptor other = (DocumentTypeDescriptor) o;
        DocumentTypeDescriptor merged = new DocumentTypeDescriptor();
        merged.id = id;
        merged.schemas = ArrayUtils.addAll(other.schemas, schemas);
        merged.facets = ArrayUtils.addAll(other.facets,facets);
        merged.prefetch = prefetch == null ? other.prefetch : prefetch + " " + other.prefetch;
        merged.excludeFromCopy = excludeFromCopy == null ? other.excludeFromCopy : excludeFromCopy;
        // update supertype
        if (StringUtils.isEmpty(superTypeName) && StringUtils.isNotEmpty(other.superTypeName)) {
            merged.superTypeName = other.superTypeName;
        }
        merged.subtypes = subtypes == null ? other.subtypes : ArrayUtils.addAll(subtypes, other.subtypes);
        merged.forbiddenSubtypes = forbiddenSubtypes == null ? other.forbiddenSubtypes : ArrayUtils.addAll(forbiddenSubtypes, other.forbiddenSubtypes);

        return this;
    }

    @Override
    public String getId() {
        return id;
    }

}
