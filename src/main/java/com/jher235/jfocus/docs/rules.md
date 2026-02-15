# JFocus Integration Rules

You have a tool called `jfocus` installed in system PATH.
When user asks for Java code analysis, refactoring, or explanation:

1. **Analysis Strategy**:
   - DO NOT read the entire file content blindly.
   - ALWAYS use `jfocus <file> <method>` to extract the context first.
   - Use `-v` flag ONLY when you need to see dependency implementation details.

2. **Command Usage**:
   - `jfocus <file>` : Lists available methods in the file.
   - `jfocus <file> <method>` : Extracts context for the method.

3. **Execution**: Run the command, assume the output is the ground truth context, and answer based on it.
