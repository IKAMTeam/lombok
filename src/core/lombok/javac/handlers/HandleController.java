/*
 * Copyright (C) 2009-2018 The Project Lombok Authors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package lombok.javac.handlers;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.List;
import lombok.ConfigurationKeys;
import lombok.ToString;
import lombok.core.AST.Kind;
import lombok.core.AnnotationValues;
import lombok.core.LombokImmutableList;
import lombok.core.configuration.CallSuperType;
import lombok.core.handlers.InclusionExclusionUtils;
import lombok.core.handlers.InclusionExclusionUtils.Included;
import lombok.javac.JavacAnnotationHandler;
import lombok.javac.JavacNode;
import lombok.javac.JavacTreeMaker;
import org.mangosdk.spi.ProviderFor;
import org.springframework.stereotype.Controller;

import java.util.Collection;

import static lombok.core.handlers.HandlerUtil.FieldAccess;
import static lombok.javac.Javac.CTC_PLUS;
import static lombok.javac.handlers.JavacHandlerUtil.*;

/**
 * Handles the {@code Controller} annotation for javac.
 */
@ProviderFor(JavacAnnotationHandler.class)
public class HandleController extends JavacAnnotationHandler<Controller> {
    private static final LombokImmutableList<String> METHOD_CHECK_TRIAL = LombokImmutableList.of("com","onevizion","web","filter","TrialLimitationCheck","checkTrial");
    private static final LombokImmutableList<String> IGNORED_METHODS = LombokImmutableList.of("detectLastApiVersion", "initTbGridPage");

    @Override
    public void handle(AnnotationValues<Controller> annotation, JCAnnotation ast, JavacNode annotationNode) {
        JavacNode classNode = annotationNode.up();

        if (classNode != null && classNode.get() instanceof JCClassDecl && !classNode.getName().startsWith("Trial")) {
            for (JCTree def : ((JCClassDecl) classNode.get()).defs) {
                if (def instanceof JCMethodDecl) {
                    JCMethodDecl methodDecl = (JCMethodDecl) def;

                    if (isConstructor(methodDecl) || isSetter(methodDecl)
                            || IGNORED_METHODS.contains(methodDecl.name.toString())) {
                        continue;
                    }

                    JavacTreeMaker maker = classNode.getTreeMaker();

                    JCStatement callCheckMethodStatement = maker.Exec(maker.Apply(List.<JCExpression>nil(),
                            chainDots(classNode, METHOD_CHECK_TRIAL), List.<JCExpression>nil()));

                    List<JCStatement> statements = methodDecl.body.stats;

                    List<JCStatement> tail = statements;
                    List<JCStatement> head = List.nil();
                    for (JCStatement stat : statements) {
                        if (JavacHandlerUtil.isConstructorCall(stat)) {
                            tail = tail.tail;
                            head = head.prepend(stat);
                            continue;
                        }
                        break;
                    }
                    List<JCStatement> newList = tail.prepend(callCheckMethodStatement);
                    for (JCStatement stat : head) newList = newList.prepend(stat);
                    methodDecl.body.stats = newList;
                    annotationNode.getAst().setChanged();
                }
            }
        }
    }

    private boolean isSetter(JCMethodDecl methodDecl) {
        return methodDecl.name.toString().startsWith("set");
    }

    private boolean isConstructor(JCMethodDecl methodDecl) {
        return "<init>".equals(methodDecl.name.toString());
    }
}
