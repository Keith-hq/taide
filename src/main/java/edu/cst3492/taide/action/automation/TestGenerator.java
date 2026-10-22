package edu.cst3492.taide.action.automation;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.google.gson.JsonObject;
import edu.cst3492.taide.llm.LlmClient;
import edu.cst3492.taide.service.CodeContext;

import java.util.Objects;

public final class TestGenerator {
    private final LlmClient model;

    public TestGenerator(LlmClient model) {
        this.model = Objects.requireNonNull(model);
    }

    public GeneratedTest generate(String requirement, CodeContext context, String fullSource) {
        ModelOutput.checkLength(requirement, 4_000, "The test scenario");
        ModelOutput.checkLength(context.selectedText(), 30_000, "The code selection");
        ModelOutput.checkLength(fullSource, 60_000, "The Java source file");
        String expectedPackage = packageName(parse(fullSource));
        var input = new JsonObject();
        input.addProperty("scenario", requirement.strip());
        input.addProperty("package", expectedPackage);
        input.addProperty("enclosingClass", context.enclosingClass());
        input.addProperty("selection", context.selectedText());
        input.addProperty("source", fullSource);
        JsonObject response = ModelOutput.json(
                model.complete(ModelOutput.prompt("junit"), input.toString(), 0.2));
        return validate(ModelOutput.string(response, "source"), expectedPackage);
    }

    public static GeneratedTest validate(String source, String expectedPackage) {
        ModelOutput.checkLength(source, 100_000, "The generated test");
        CompilationUnit unit = parse(source);
        if (!packageName(unit).equals(expectedPackage)) {
            throw new AutomationException("The generated test must use the same package as the source class.");
        }
        if (unit.getTypes().size() != 1
                || !(unit.getType(0) instanceof ClassOrInterfaceDeclaration type)
                || type.isInterface() || !type.isPublic() || type.isAbstract()
                || !type.getNameAsString().matches("[A-Za-z_$][A-Za-z0-9_$]*Test")) {
            throw new AutomationException("Generate one public, concrete Java class with a name ending in Test.");
        }
        boolean importsTest = unit.getImports().stream().anyMatch(imported ->
                !imported.isStatic() && (imported.getNameAsString().equals("org.junit.jupiter.api.Test")
                || (imported.isAsterisk() && imported.getNameAsString().equals("org.junit.jupiter.api"))));
        boolean hasTest = type.getMethods().stream()
                .filter(method -> !method.isPrivate() && !method.isStatic() && !method.isAbstract()
                        && method.getType().isVoidType() && method.getParameters().isEmpty())
                .flatMap(method -> method.getAnnotations().stream())
                .anyMatch(annotation -> annotation.getNameAsString().equals("org.junit.jupiter.api.Test")
                        || (importsTest && annotation.getNameAsString().equals("Test")));
        if (!hasTest) {
            throw new AutomationException(
                    "The generated class must contain a non-private, non-static void JUnit 5 @Test method with no arguments.");
        }
        return new GeneratedTest(expectedPackage, type.getNameAsString(), source.strip() + "\n");
    }

    private static CompilationUnit parse(String source) {
        var configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        var result = new JavaParser(configuration).parse(source);
        if (!result.isSuccessful() || result.getResult().isEmpty()) {
            throw new AutomationException("The Java source contains syntax errors. Review the code and retry.");
        }
        return result.getResult().orElseThrow();
    }

    private static String packageName(CompilationUnit unit) {
        return unit.getPackageDeclaration().map(declaration -> declaration.getNameAsString()).orElse("");
    }
}
