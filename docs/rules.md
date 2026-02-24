# JFocus Integration Rules

You have a tool called `jfocus` installed in system PATH.
When user asks for Java code analysis, refactoring, or explanation:

1. **Analysis Strategy (Tool Selection)**:
   - **For Method-Level Logic:** ALWAYS use `jfocus <file> <method>` to extract the context. This saves tokens and focuses on the specific logic. Use `-v` flag ONLY when you need to see dependency implementation details.
   - **For Class-Level Structure/Fields:** If the user asks about class fields, annotations, or overall architecture, you MAY read the file directly using standard tools (like `cat`), but try to run `jfocus <file> -l` first to grasp the method outline.

2. **Command Usage (CRITICAL)**:
   - **NEVER use `jfocus <file>` without `-l` or a method name.** It triggers an interactive prompt that will cause you (the agent) to hang/timeout.
   - `jfocus <file> -l` : Lists available methods in the file non-interactively.
   - `jfocus <file> <method>` : Extracts context for the method.
   - If JFocus returns a "Multiple overloads found" error, it will also print the exact available signatures. You MUST retry using the EXACT signature.
   - **CRITICAL:** When specifying an exact method signature with parameters (like `void add(int index, E element)`), you MUST wrap the method name in quotes to prevent Bash syntax errors.
     - Correct: `jfocus MyClass "void add(int index, E element)"`
     - Incorrect: `jfocus MyClass void add(int index, E element)`

3. **Trust Model**:
   - When using `jfocus <file> <method>`, treat its output as the source of truth **for that specific method and its dependencies**.
   - However, remember that `jfocus` extracts specific targets; do NOT falsely assume the class lacks fields or other methods just because they are omitted from the targeted output.

4. **Execution**: Run the command appropriate for the user's intent, analyze the output, and answer based on it.
