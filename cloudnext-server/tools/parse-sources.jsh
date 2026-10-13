import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import javax.tools.*;
import com.sun.source.util.JavacTask;
// Parse only: intentionally never call analyze(), generate(), or CompilationTask.call().
var sourceFiles = Files.walk(Paths.get("src/main/java")).filter(p -> p.toString().endsWith(".java")).map(Path::toFile).collect(Collectors.toList());
var diagnostics = new DiagnosticCollector<JavaFileObject>();
var compiler = ToolProvider.getSystemJavaCompiler();
var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, java.nio.charset.StandardCharsets.UTF_8);
var task = (JavacTask) compiler.getTask(null, manager, diagnostics, Arrays.asList("-proc:none", "--release", "11"), null, manager.getJavaFileObjectsFromFiles(sourceFiles));
for (var parsed : task.parse()) {}
var errors = diagnostics.getDiagnostics().stream().filter(d -> d.getKind() == Diagnostic.Kind.ERROR).collect(Collectors.toList());
for (var error : errors) System.out.println(error);
System.out.println("Java syntax parsed: " + sourceFiles.size() + ", errors: " + errors.size() + "; no class files generated.");
manager.close();
System.exit(errors.isEmpty() ? 0 : 1);
