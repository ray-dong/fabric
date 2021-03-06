/*
 * Copyright (C) 2010-2011, FuseSource Corp.  All rights reserved.
 *
 * 	http://fusesource.com
 *
 * The software in this package is published under the terms of the
 * CDDL license, a copy of which has been included with this distribution
 * in the license.txt file.
 */

package org.fusesource.fabric.apollo.amqp.generator;

import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import org.fusesource.fabric.apollo.amqp.jaxb.schema.Choice;
import org.fusesource.fabric.apollo.amqp.jaxb.schema.Type;
import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtbuf.Buffer;

import static org.fusesource.fabric.apollo.amqp.generator.Utilities.toJavaClassName;
import static org.fusesource.fabric.apollo.amqp.generator.Utilities.toStaticName;

/**
 *
 */
public class RestrictedType extends AmqpDefinedType {

    Class basePrimitiveType;

    public RestrictedType(Generator generator, String className, Type type) throws JClassAlreadyExistsException {
        super(generator, className, type);
    }

    @Override
    protected void init() {
        if ( type.getProvides() != null ) {
            cls()._implements(cm.ref(generator.getInterfaces() + "." + toJavaClassName(type.getProvides())));
        } else {
            cls()._implements(cm.ref(generator.getAmqpBaseType()));
        }

        String source = generator.getPrimitiveJavaClass().get(type.getSource());
        if ( source == null ) {
            source = generator.getTypes() + "." + toJavaClassName(type.getSource());
        }

        basePrimitiveType = generator.getMapping().get(generator.getRestrictedMapping().get(type.getName()));

        cls()._extends(cm.ref(source));

        cls().constructor(JMod.PUBLIC).body().block();


        JMethod setter = cls().constructor(JMod.PUBLIC);
        setter.param(basePrimitiveType, "value");
        setter.body().block().assign(JExpr._this().ref("value"), JExpr.ref("value"));

        generateConstants();

    }

    @Override
    protected void createGetArrayConstructor() {

    }

    private void generateConstants() {

        for ( Object obj : type.getEncodingOrDescriptorOrFieldOrChoiceOrDoc() ) {
            if ( obj instanceof Choice ) {
                Choice constant = (Choice) obj;
                int mods = JMod.PUBLIC | JMod.STATIC | JMod.FINAL;
                String name = toStaticName(constant.getName());
                if ( basePrimitiveType == Buffer.class ) {
                    cls().field(mods, cls(), name, JExpr
                            ._new(cls()).arg(JExpr
                                    ._new(cm.ref(AsciiBuffer.class)).arg(JExpr.lit(constant.getValue()))));
                } else if ( basePrimitiveType == Boolean.class ) {
                    if ( Boolean.parseBoolean(constant.getValue()) ) {
                        cls().field(mods, cls(), name, JExpr._new(cls()).arg(cm.ref("java.lang.Boolean").staticRef("TRUE")));
                    } else {
                        cls().field(mods, cls(), name, JExpr._new(cls()).arg(cm.ref("java.lang.Boolean").staticRef("FALSE")));
                    }
                } else if ( basePrimitiveType == Short.class ) {
                    cls().field(mods, cls(), name, JExpr._new(cls()).arg(JExpr._new(cm.ref("java.lang.Short")).arg(JExpr.cast(cm.ref("short"), JExpr.lit(Short.parseShort(constant.getValue()))))));
                } else if ( basePrimitiveType == Long.class ) {
                    cls().field(mods, cls(), name, JExpr._new(cls()).arg(JExpr._new(cm.ref("java.lang.Long")).arg(JExpr.lit(Long.parseLong(constant.getValue())))));
                } else {
                    Log.warn("Not generating constant %s with type %s for restricted type %s!", constant.getName(), basePrimitiveType.getSimpleName(), type.getName());
                }
            }
        }
    }

    @Override
    protected void createStaticBlock() {

    }

    @Override
    protected void createInitialFields() {

    }
}
