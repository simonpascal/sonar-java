/*
 * Sonar Java
 * Copyright (C) 2012 SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.java.ast.visitors;

import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.sonar.sslr.api.AstNode;
import com.sonar.sslr.api.GenericTokenType;
import org.sonar.java.ast.api.JavaKeyword;
import org.sonar.java.ast.api.JavaMetric;
import org.sonar.java.signature.JvmJavaType;
import org.sonar.java.signature.MethodSignature;
import org.sonar.java.signature.MethodSignaturePrinter;
import org.sonar.java.signature.Parameter;
import org.sonar.squid.api.SourceClass;
import org.sonar.squid.api.SourceMethod;

import java.util.List;
import java.util.Map;

public class MethodVisitor extends JavaAstVisitor {

  @Override
  public void init() {
    MethodHelper.subscribe(this);
  }

  @Override
  public void visitNode(AstNode astNode) {
    String methodName = buildMethodSignature(new MethodHelper(getContext().getGrammar(), astNode));
    SourceClass sourceClass = peekSourceClass();
    SourceMethod sourceMethod = new SourceMethod(sourceClass, methodName, astNode.getTokenLine());
    sourceMethod.setMeasure(JavaMetric.METHODS, 1);
    getContext().addSourceCode(sourceMethod);
  }

  @Override
  public void leaveNode(AstNode astNode) {
    getContext().popSourceCode();
  }

  private String buildMethodSignature(MethodHelper methodHelper) {
    String methodName = extractMethodName(methodHelper);
    Parameter returnType = extractMethodReturnType(methodHelper);
    List<Parameter> argumentTypes = extractMethodArgumentTypes(methodHelper);
    MethodSignature signature = new MethodSignature(methodName, returnType, argumentTypes);
    return MethodSignaturePrinter.print(signature);
  }

  private String extractMethodName(MethodHelper methodHelper) {
    if (methodHelper.isConstructor()) {
      return "<init>";
    }
    return methodHelper.getName().getTokenValue();
  }

  private Parameter extractMethodReturnType(MethodHelper methodHelper) {
    if (methodHelper.isConstructor()) {
      return new Parameter(JvmJavaType.V, false);
    }
    AstNode returnType = methodHelper.getReturnType();
    boolean isArray = returnType.hasDirectChildren(getContext().getGrammar().dim);
    return new Parameter(extractArgumentAndReturnType(returnType, isArray));
  }

  private List<Parameter> extractMethodArgumentTypes(MethodHelper methodHelper) {
    List<Parameter> argumentTypes = Lists.newArrayList();
    for (AstNode astNode : methodHelper.getParameters()) {
      AstNode type = astNode.findFirstDirectChild(getContext().getGrammar().type);
      boolean isArray = type.hasDirectChildren(getContext().getGrammar().dim)
          || astNode.findFirstChild(getContext().getGrammar().variableDeclaratorId).hasDirectChildren(getContext().getGrammar().dim);
      argumentTypes.add(extractArgumentAndReturnType(type, isArray));
    }
    return argumentTypes;
  }

  private Parameter extractArgumentAndReturnType(AstNode astNode, boolean isArray) {
    Preconditions.checkArgument(astNode.is(JavaKeyword.VOID, getContext().getGrammar().type));
    if (astNode.is(JavaKeyword.VOID)) {
      return new Parameter(JvmJavaType.V, false);
    }
    if (astNode.getFirstChild().is(getContext().getGrammar().basicType)) {
      return new Parameter(JAVA_TYPE_MAPPING.get(astNode.getFirstChild().getFirstChild().getType()), isArray);
    } else if (astNode.getFirstChild().is(getContext().getGrammar().classType)) {
      return new Parameter(extractClassName(astNode.getFirstChild()), isArray);
    } else {
      throw new IllegalStateException();
    }
  }

  private String extractClassName(AstNode astNode) {
    Preconditions.checkArgument(astNode.is(getContext().getGrammar().classType));
    // TODO Godin: verify
    return Iterables.getLast(astNode.findDirectChildren(GenericTokenType.IDENTIFIER)).getTokenValue();
  }

  private static final Map<JavaKeyword, JvmJavaType> JAVA_TYPE_MAPPING = Maps.newHashMap();

  static {
    JAVA_TYPE_MAPPING.put(JavaKeyword.BYTE, JvmJavaType.B);
    JAVA_TYPE_MAPPING.put(JavaKeyword.CHAR, JvmJavaType.C);
    JAVA_TYPE_MAPPING.put(JavaKeyword.SHORT, JvmJavaType.S);
    JAVA_TYPE_MAPPING.put(JavaKeyword.INT, JvmJavaType.I);
    JAVA_TYPE_MAPPING.put(JavaKeyword.LONG, JvmJavaType.J);
    JAVA_TYPE_MAPPING.put(JavaKeyword.BOOLEAN, JvmJavaType.Z);
    JAVA_TYPE_MAPPING.put(JavaKeyword.FLOAT, JvmJavaType.F);
    JAVA_TYPE_MAPPING.put(JavaKeyword.DOUBLE, JvmJavaType.D);
    JAVA_TYPE_MAPPING.put(JavaKeyword.VOID, JvmJavaType.V);
  }

}
