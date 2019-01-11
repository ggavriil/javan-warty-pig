package jwp.extras;

import com.squareup.javapoet.*;
import jwp.fuzz.ExecutionResult;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

/**
 * Writes out test cases for completed executions. This base class provides helpers for implementors.
 */
public abstract class TestWriter {

  /** Return a single-line, double-quoted, and unescaped Java string from the given string value */
  public static String doubleQuotedString(String string) {
    // https://github.com/square/javapoet/issues/604 :-(
    string = CodeBlock.of("$S", string).toString();
    while (true) {
      int endQuoteIndex = string.indexOf("\"\n");
      if (endQuoteIndex == -1) return string;
      string = string.substring(0, endQuoteIndex) + string.substring(string.indexOf('+', endQuoteIndex) + 3);
    }
  }

  /** The config for this test case */
  public final Config config;
  protected final TypeSpec.Builder typeBld;

  public TestWriter(Config config) {
    this.config = config;
    typeBld = TypeSpec.classBuilder(config.getSimpleClassName()).addModifiers(Modifier.PUBLIC);
  }

  /** Append the result to have test case(s) written for it. This may be called my multiple threads simultaneously. */
  public abstract void append(ExecutionResult result);

  /** Append all code to the given appendable */
  public void flush(Appendable to) throws IOException {
    JavaFile.builder(config.getPackageName(), typeBld.build()).
        addFileComment("File generated by JWP fuzzer").
        build().
        writeTo(to);
  }

  /**
   * Return a code block that invokes the given execution. If it returns a result and it did not fail with an exception,
   * the result is written to a local variable named "result".
   */
  protected CodeBlock.Builder invokeAndStore(ExecutionResult result) {
    CodeBlock.Builder preCall = CodeBlock.builder();
    // Make the call and store result in local var if not void
    CodeBlock.Builder call = CodeBlock.builder();
    if (result.exception == null && result.method.getReturnType() != Void.TYPE)
      call.add("$T result = ", result.method.getGenericReturnType());

    // Invoke the method
    // TODO: instance method?
    if (!java.lang.reflect.Modifier.isStatic(result.method.getModifiers()))
      throw new RuntimeException("Only static supported for now");
    call.add("$T.$L(", result.method.getDeclaringClass(), result.method.getName());
    for (int i = 0; i < result.params.length; i++) {
      if (i > 0) call.add(", ");
      appendItem(preCall, call, result.params[i]);
    }
    call.add(");\n");
    return preCall.add(call.build());
  }

  /**
   * For the given actual item, append its code representation to the code parameter. The preCode parameter is provided
   * as a way for the appender to do some setup if necessary. This only works for some item types, though should improve
   * over time.
   */
  protected void appendItem(CodeBlock.Builder preCode, CodeBlock.Builder code, Object item) {
    if (item == null) code.add("null");
    else if (item instanceof List) {
      code.add("$T.asList(", Arrays.class);
      List list = (List) item;
      for (int i = 0; i < list.size(); i++) {
        if (i > 0) code.add(", ");
        appendItem(preCode, code, list.get(i));
      }
      code.add(")");
    } else if (item instanceof String) code.add("$L", doubleQuotedString((String) item));
    //else throw new RuntimeException("Unsupported type: " + item.getClass()); // TODO: more
    else {
      code.add("// TODO")
      code.add("$L", "[fill in]"); // TODO: more
    }
  }

  /** A {@link TestWriter} to write JUnit 4 unit tests */
  public static class JUnit4 extends TestWriter {
    public JUnit4(Config config) { super(config); }

    @Override
    public synchronized void append(ExecutionResult result) {
      MethodSpec.Builder methodBld = MethodSpec.methodBuilder(config.namer.name(result)).addModifiers(Modifier.PUBLIC);
      // If the method throws, we throw
      Arrays.asList(result.method.getExceptionTypes()).forEach(methodBld::addException);
      // Create @Test annotation, add expected exception if there
      AnnotationSpec.Builder annBld = AnnotationSpec.builder(ClassName.get("org.junit", "Test"));
      if (result.exception != null) {
        annBld.addMember("expected", "$T.class", result.exception.getClass());
        methodBld.addComment("Got exception $L: $L", result.exception.getClass().getSimpleName(),
            doubleQuotedString(result.exception.getMessage()));
      }
      methodBld.addAnnotation(annBld.build());
      // Add the code
      CodeBlock.Builder code = invokeAndStore(result);
      // If there is a result type, do an assertion
      if (result.exception == null && result.method.getReturnType() != Void.TYPE) {
        String assertMethodName;
        if (result.method.getReturnType().isArray()) assertMethodName = "assertArrayEquals";
        else assertMethodName = "assertEquals";
        CodeBlock.Builder assertCall = CodeBlock.builder().add(
            "$T.$L(", ClassName.get("org.junit", "Assert"), assertMethodName);
        appendItem(code, assertCall, result.result);
        code.add(assertCall.add(", result);\n").build());
      }
      methodBld.addCode(code.build());
      typeBld.addMethod(methodBld.build());
    }
  }

  /** Base class for test case namer. This is used to name methods for the test case. */
  public interface Namer {
    /**
     * Return a new, unique test case name. This has to be thread-safe since it can be called by multiple threads
     * simultaneously.
     */
    String name(ExecutionResult result);

    /**
     * A namer that will name the test case "PREFIXSuccessX" or "PREFIXFailX" with "X" replaced by an incrementing
     * counter per success or failure respectively and PREFIX replaced by the given prefix.
     */
    class SuccessOrFailCounted implements Namer {
      public final String prefix;
      private int successCount;
      private int failureCount;

      public SuccessOrFailCounted(String prefix) {
        this.prefix = prefix;
      }

      @Override
      public synchronized String name(ExecutionResult result) {
        if (result.exception != null) return prefix + "Fail" + ++failureCount;
        return prefix + "Success" + ++successCount;
      }
    }
  }

  /** Configuration for the test writer */
  public static class Config {
    public final String className;
    public final Namer namer;

    /** Config with given class name and {@link Namer.SuccessOrFailCounted} namer with "test" prefix */
    public Config(String className) {
      this(className, new Namer.SuccessOrFailCounted("test"));
    }

    /** Config for given class name and namer */
    public Config(String className, Namer namer) {
      this.className = className;
      this.namer = namer;
    }

    /** The analog to {@link Class#getSimpleName()} */
    public String getSimpleClassName() {
      return className.substring(className.lastIndexOf('.') + 1);
    }

    /** The package name of the class */
    public String getPackageName() {
      return className.substring(0, className.lastIndexOf('.'));
    }
  }
}
