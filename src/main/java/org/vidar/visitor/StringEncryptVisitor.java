package org.vidar.visitor;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.nodeTypes.NodeWithStatements;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.visitor.ModifierVisitor;
import com.github.javaparser.ast.visitor.Visitable;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.vidar.entry.StringEntry;
import org.vidar.utils.Encryptor;
import org.vidar.utils.StatementUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class StringEncryptVisitor extends VoidVisitorAdapter<Void> {
    @Override
    public void visit(ClassOrInterfaceDeclaration clz, Void arg) {
        List<MethodDeclaration> methods = clz.findAll(MethodDeclaration.class);
        for (MethodDeclaration method : methods) {
            if (!method.getBody().isPresent()) {
                return;
            }
            run(method.getBody().get());
        }
    }

    private void run(BlockStmt body) {
        // Extract string to variable
        Map<Statement, List<StringEntry>> encStrings = extractAndReplace(body);

        // Insert decryption routine
        for (Map.Entry<Statement, List<StringEntry>> entry : encStrings.entrySet()) {
            Statement stmt = entry.getKey();
            List<StringEntry> strEntries = entry.getValue();
            for (StringEntry strEntry : strEntries) {
                NodeList<Statement> decStmts = Encryptor.makeDecryptor(strEntry);
                NodeWithStatements<?> parent = (NodeWithStatements<?>) stmt.getParentNode().get();
                parent.getStatements().addAll(parent.getStatements().indexOf(stmt), decStmts);
            }
        }
    }

    private Map<Statement, List<StringEntry>> extractAndReplace(BlockStmt block) {
        Map<Statement, List<StringEntry>> result = new HashMap<>();

        ModifierVisitor<Void> visitor = new ModifierVisitor<Void>() {
            @Override
            public Visitable visit(StringLiteralExpr n, Void arg) {
                if (n.getValue().isEmpty()) {
                    return super.visit(n, arg);
                }
                StringEntry entry = new StringEntry(n.asString());
                Statement topStmt = StatementUtils.findParentBlock(n);

                result.computeIfAbsent(topStmt, k -> new ArrayList<>()).add(entry);
                return new NameExpr(entry.getVarName());
            }

            @Override
            public Visitable visit(SwitchEntry n, Void arg) { // Skip string constants from `switch`
                NodeList<Statement> statements = modifyList(n.getStatements(), arg);
                n.setStatements(statements);
                return n;
            }

            private <N extends Node> NodeList<N> modifyList(NodeList<N> list, Void arg) {
                return (NodeList<N>) list.accept(this, arg);
            }
        };
        block.accept(visitor, null);
        return result;
    }
}