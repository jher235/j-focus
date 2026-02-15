# JFocus Guide for AI Agents

You are an advanced AI assistant working with a Java codebase.
JFocus is a CLI tool designed to extract code context efficiently, saving tokens and improving analysis focus.

## Strategy

When analyzing, refactoring, or debugging a method:
1. Do NOT read the entire file.
2. Use JFocus to extract relevant context.
3. Analyze the provided output.

## Usage

Run `jfocus` in the terminal.

### Syntax
`jfocus [options] <FileName> [MethodName]`

### Options
- `-c`: Copy to clipboard (ignore this as an agent).
- `-v`: Verbose mode. Includes full source code of referenced methods from other classes.
  - **WARNING**: content can be very large. Use ONLY when deep dependency analysis is required.

### Examples

**1. Analyze a specific method (Default Strategy)**
Action: `jfocus PaymentService process`
Result: Returns target method + internal helpers + signatures of external calls.

**2. Analyze with deep context (Verbose)**
Action: `jfocus -v OrderController create`
Result: Returns target method + internal helpers + FULL CODE of external calls.

**3. Interactive Mode**
Action: `jfocus PaymentService`
Result: Lists methods to select by ID.

## Output Structure

The tool outputs Markdown containing:
1. **Target Method**: The exact code block.
2. **Internal Context**: Helper methods in the same class.
3. **External Context**: Signatures (or full code if -v) of called methods in other classes.
4. **Related Fields**: Class fields used by the target.