import re

file_path = r'c:/Users/vamsi/Desktop/Resume projects/StegAppMaterialUI/app/src/main/java/com/vamsi/stegapp/MainActivity.kt'

def verify_structure():
    with open(file_path, 'r', encoding='utf-8') as f:
        lines = f.readlines()

    stack = []
    
    with open("result.log", "w", encoding="utf-8") as out:
        for i, line in enumerate(lines):
            line_stripped = line.strip()
            
            # Check for function definitions and log their nesting level
            if line_stripped.startswith("fun ") or line_stripped.startswith("@Composable fun") or "fun MessageBubble" in line_stripped:
                func_name = line_stripped.split('(')[0]
                out.write(f"Found {func_name} at line {i+1}, Level: {len(stack)}\n")

            for char in line:
                if char == '{':
                    stack.append(i + 1)
                elif char == '}':
                    if not stack:
                        out.write(f"Error: Unexpected closing brace at line {i+1}\n")
                        return
                    stack.pop()
    
        if stack:
            out.write(f"Error: Unclosed brace starting at line {stack[-1]}\n")
            out.write(f"Total unclosed braces: {len(stack)}\n")
        else:
            out.write("Success: Braces are balanced.\n")

if __name__ == "__main__":
    verify_structure()
