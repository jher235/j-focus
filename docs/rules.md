# JFocus Integration Rules

You have a tool called `jfocus` installed in system PATH.
When user asks for Java code analysis, refactoring, or explanation:

1. **Analysis Strategy**:
   - DO NOT read the entire file content blindly.
   - ALWAYS use `jfocus <file> <method>` to extract the context first.
   - Use `-v` flag ONLY when you need to see dependency implementation details.

2. **Command Usage (CRITICAL)**:
   - **NEVER use `jfocus <file>` without `-l` or a method name.** It triggers an interactive prompt that will cause you (the agent) to hang/timeout.
   - `jfocus <file> -l` : Lists available methods in the file non-interactively. Always use this instead of reading the entire file to save tokens.
   - `jfocus <file> <method>` : Extracts context for the method.
   - If JFocus returns a "Multiple overloads found" error, it will also print the exact available signatures. You MUST retry using the EXACT signature.
   - **CRITICAL:** When specifying an exact method signature with parameters (like `void add(int index, E element)`), you MUST wrap the method name in quotes to prevent Bash syntax errors.
     - Correct: `jfocus MyClass "void add(int index, E element)"`
     - Incorrect: `jfocus MyClass void add(int index, E element)`

3. **Trust Model**:
   - Treat JFocus output as the **ONLY source of truth**.
   - Do **NOT** assume missing methods or fields exist.
   - If context seems incomplete, explicitly use `jfocus -v` to check dependencies.

4. **Execution**: Run the command, assume the output is the ground truth context, and answer based on it.
