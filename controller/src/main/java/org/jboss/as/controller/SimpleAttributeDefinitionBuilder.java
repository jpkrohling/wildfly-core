/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.as.controller;

import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.dmr.ModelNode;
import org.jboss.dmr.ModelType;

/**
 * Provides a builder API for creating a {@link SimpleAttributeDefinition}.
 *
 * @author Brian Stansberry (c) 2011 Red Hat Inc.
 */
public class SimpleAttributeDefinitionBuilder extends AbstractAttributeDefinitionBuilder<SimpleAttributeDefinitionBuilder, SimpleAttributeDefinition> {

    public static SimpleAttributeDefinitionBuilder create(final String name, final ModelType type) {
        return new SimpleAttributeDefinitionBuilder(name, type);
    }

    public static SimpleAttributeDefinitionBuilder create(final String name, final ModelType type, final boolean optional) {
        return new SimpleAttributeDefinitionBuilder(name, type, optional);
    }

    public static SimpleAttributeDefinitionBuilder create(final SimpleAttributeDefinition basis) {
        return new SimpleAttributeDefinitionBuilder(basis);
    }

    /*
    "code" => {
        "type" => STRING,
        "description" => "Fully Qualified Name of the Security Vault Implementation.",
        "expressions-allowed" => false,
        "nillable" => true,
        "min-length" => 1L,
        "max-length" => 2147483647L,
        "access-type" => "read-write",
        "storage" => "configuration",
        "restart-required" => "no-services"
    },
    */
    public static SimpleAttributeDefinitionBuilder create(final String name, final ModelNode node) {
        ModelType type = node.get(ModelDescriptionConstants.TYPE).asType();
        boolean nillable = node.get(ModelDescriptionConstants.NILLABLE).asBoolean(true);
        boolean expressionAllowed = node.get(ModelDescriptionConstants.EXPRESSIONS_ALLOWED).asBoolean(false);
        ModelNode defaultValue = nillable ? node.get(ModelDescriptionConstants.DEFAULT) : new ModelNode();
        return SimpleAttributeDefinitionBuilder.create(name, type, nillable)
                .setDefaultValue(defaultValue)
                .setAllowExpression(expressionAllowed);
    }

    public static SimpleAttributeDefinitionBuilder create(final String attributeName, final SimpleAttributeDefinition basis) {
        return new SimpleAttributeDefinitionBuilder(attributeName, basis);
    }

    public SimpleAttributeDefinitionBuilder(final String attributeName, final ModelType type) {
        this(attributeName, type, false);
    }

    public SimpleAttributeDefinitionBuilder(final String attributeName, final ModelType type, final boolean optional) {
        super(attributeName, type, optional);
        setAttributeParser(AttributeParser.SIMPLE);
    }

    public SimpleAttributeDefinitionBuilder(final SimpleAttributeDefinition basis) {
        super(basis);
    }

    public SimpleAttributeDefinitionBuilder(final String attributeName, final SimpleAttributeDefinition basis) {
        super(attributeName, basis);
    }

    public SimpleAttributeDefinition build() {
        return new SimpleAttributeDefinition(this);
    }

}
