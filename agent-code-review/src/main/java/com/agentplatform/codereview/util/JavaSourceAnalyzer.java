package com.agentplatform.codereview.util;

import com.agentplatform.codereview.model.CodeReviewIssue;
import com.agentplatform.codereview.model.JavaFileSnapshot;
import com.agentplatform.codereview.model.Severity;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.ConditionalExpr;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.CatchClause;
import com.github.javaparser.ast.stmt.DoStmt;
import com.github.javaparser.ast.stmt.ForEachStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.WhileStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Parses Java source with JavaParser and produces metrics plus rule-based issues.
 */
@Component
public class JavaSourceAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(JavaSourceAnalyzer.class);
    private static final int MAX_METHOD_LINES = 80;
    private static final int MAX_METHOD_COMPLEXITY = 15;

    public JavaFileSnapshot analyze(Path root, Path javaFile) {
        String relativePath = root.toAbsolutePath().relativize(javaFile.toAbsolutePath()).toString()
                .replace('\\', '/');
        List<CodeReviewIssue> issues = new ArrayList<>();
        CompilationUnit cu = parse(javaFile, relativePath, issues);
        if (cu == null) {
            return new JavaFileSnapshot(relativePath, "", "", 0, 0, 0, 0, 0, 0, 0, issues);
        }

        int lineCount = countLines(javaFile);
        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");
        String className = cu.findAll(ClassOrInterfaceDeclaration.class).stream()
                .findFirst()
                .map(ClassOrInterfaceDeclaration::getNameAsString)
                .orElse("");
        int methodCount = cu.findAll(MethodDeclaration.class).size();
        int fieldCount = cu.findAll(FieldDeclaration.class).size();
        int importCount = cu.getImports().size();
        int commentCount = cu.getAllContainedComments().size();
        int todoCount = countTodoComments(cu);
        int complexity = countComplexity(cu);

        collectMethodIssues(cu, relativePath, issues);
        collectCatchIssues(cu, relativePath, issues);
        collectCallIssues(cu, relativePath, issues);
        collectCommentIssues(cu, relativePath, issues);

        return new JavaFileSnapshot(
                relativePath,
                packageName,
                className,
                lineCount,
                methodCount,
                fieldCount,
                importCount,
                commentCount,
                todoCount,
                complexity,
                issues);
    }

    private CompilationUnit parse(Path file, String relativePath, List<CodeReviewIssue> issues) {
        try {
            return StaticJavaParser.parse(file);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to parse {}: {}", relativePath, e.getMessage());
            issues.add(new CodeReviewIssue(
                    Severity.MAJOR,
                    relativePath,
                    0,
                    "PARSE_ERROR",
                    "JavaParser failed: " + e.getMessage()));
            return null;
        }
    }

    private int countLines(Path file) {
        try {
            return (int) Files.lines(file, StandardCharsets.UTF_8).count();
        } catch (IOException e) {
            return 0;
        }
    }

    private int countTodoComments(CompilationUnit cu) {
        int count = 0;
        for (Comment comment : cu.getAllContainedComments()) {
            String content = comment.getContent();
            if (content != null && content.matches("(?is).*(TODO|FIXME).*")) {
                count++;
            }
        }
        return count;
    }

    private int countComplexity(CompilationUnit cu) {
        AtomicInteger counter = new AtomicInteger();
        cu.accept(new ComplexityVisitor(), counter);
        return counter.get();
    }

    private void collectMethodIssues(CompilationUnit cu, String relativePath, List<CodeReviewIssue> issues) {
        for (MethodDeclaration method : cu.findAll(MethodDeclaration.class)) {
            Optional<BlockStmt> body = method.getBody();
            if (body.isEmpty()) {
                continue;
            }
            int beginLine = method.getBegin().map(pos -> pos.line).orElse(0);
            int endLine = method.getEnd().map(pos -> pos.line).orElse(0);
            int methodLines = endLine - beginLine + 1;
            if (methodLines > MAX_METHOD_LINES) {
                issues.add(new CodeReviewIssue(
                        Severity.MAJOR,
                        relativePath,
                        beginLine,
                        "LONG_METHOD",
                        "Method " + method.getNameAsString() + " has " + methodLines + " lines"));
            }

            AtomicInteger methodComplexity = new AtomicInteger();
            method.accept(new ComplexityVisitor(), methodComplexity);
            if (methodComplexity.get() > MAX_METHOD_COMPLEXITY) {
                issues.add(new CodeReviewIssue(
                        Severity.MAJOR,
                        relativePath,
                        beginLine,
                        "HIGH_COMPLEXITY",
                        "Method " + method.getNameAsString() + " complexity is " + methodComplexity.get()));
            }
        }
    }

    private void collectCatchIssues(CompilationUnit cu, String relativePath, List<CodeReviewIssue> issues) {
        for (CatchClause catchClause : cu.findAll(CatchClause.class)) {
            int line = catchClause.getBegin().map(pos -> pos.line).orElse(0);
            BlockStmt body = catchClause.getBody();
            if (body.getStatements().isEmpty()) {
                issues.add(new CodeReviewIssue(
                        Severity.MAJOR,
                        relativePath,
                        line,
                        "EMPTY_CATCH",
                        "Empty catch block silently swallows exceptions"));
                continue;
            }
            boolean onlyPrint = body.getStatements().stream()
                    .allMatch(stmt -> stmt instanceof ExpressionStmt
                            && ((ExpressionStmt) stmt).getExpression().isMethodCallExpr()
                            && ((ExpressionStmt) stmt).getExpression().asMethodCallExpr()
                            .getNameAsString().equals("printStackTrace"));
            if (onlyPrint) {
                issues.add(new CodeReviewIssue(
                        Severity.MAJOR,
                        relativePath,
                        line,
                        "SWALLOWED_EXCEPTION",
                        "Catch block only prints stack trace without recovery or logging context"));
            }
        }
    }

    private void collectCallIssues(CompilationUnit cu, String relativePath, List<CodeReviewIssue> issues) {
        for (MethodCallExpr call : cu.findAll(MethodCallExpr.class)) {
            int line = call.getBegin().map(pos -> pos.line).orElse(0);
            String name = call.getNameAsString();
            String scope = call.getScope().map(Object::toString).orElse("");
            if (name.equals("println") && scope.contains("System.out")) {
                issues.add(new CodeReviewIssue(
                        Severity.MINOR,
                        relativePath,
                        line,
                        "SYSTEM_OUT",
                        "Use a logger instead of System.out"));
            }
            if (name.equals("printStackTrace")) {
                issues.add(new CodeReviewIssue(
                        Severity.MAJOR,
                        relativePath,
                        line,
                        "PRINT_STACK_TRACE",
                        "Use a logging framework instead of printStackTrace"));
            }
            if (name.equals("sleep")) {
                issues.add(new CodeReviewIssue(
                        Severity.MINOR,
                        relativePath,
                        line,
                        "THREAD_SLEEP",
                        "Thread.sleep in production code usually indicates a polling or timing smell"));
            }
        }
    }

    private void collectCommentIssues(CompilationUnit cu, String relativePath, List<CodeReviewIssue> issues) {
        for (Comment comment : cu.getAllContainedComments()) {
            String content = comment.getContent();
            if (content != null && content.matches("(?is).*(TODO|FIXME).*")) {
                issues.add(new CodeReviewIssue(
                        Severity.MINOR,
                        relativePath,
                        comment.getBegin().map(pos -> pos.line).orElse(0),
                        "TODO_COMMENT",
                        "Unresolved TODO/FIXME comment: " + content.trim()));
            }
        }
    }

    /**
     * Counts decision points in a Java AST.
     */
    private static class ComplexityVisitor extends VoidVisitorAdapter<AtomicInteger> {

        @Override
        public void visit(IfStmt n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(ForStmt n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(ForEachStmt n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(WhileStmt n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(DoStmt n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(SwitchStmt n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(CatchClause n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(ConditionalExpr n, AtomicInteger counter) {
            counter.incrementAndGet();
            super.visit(n, counter);
        }

        @Override
        public void visit(BinaryExpr n, AtomicInteger counter) {
            if (n.getOperator() == BinaryExpr.Operator.AND
                    || n.getOperator() == BinaryExpr.Operator.OR) {
                counter.incrementAndGet();
            }
            super.visit(n, counter);
        }
    }
}
