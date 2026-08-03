---
name: explain
description: Explain code with visual diagrams and clear mental models
argument-hint: "[file, function, or concept]"
disable-model-invocation: true
---

Explain `$ARGUMENTS` clearly.

## Format

### 1. One-sentence summary
What does it do and why does it exist? One sentence.

### 2. Mental model
Give an analogy or metaphor that captures the core idea. Relate it to something the developer already knows.

### 3. Visual diagram
Draw an ASCII diagram showing the data/control flow. Keep it readable:
```
Input → [Step A] → [Step B] → Output
              ↓
         [Side Effect]
```

### 4. Key details
Walk through the important parts. Skip the obvious — focus on:
- Non-obvious decisions (why this approach?)
- Edge cases and gotchas
- Dependencies and side effects

### 5. How to modify it
What would someone need to know to safely change this code? Where are the landmines?
