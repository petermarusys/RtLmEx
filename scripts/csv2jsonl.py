import os
import sys
import json
import csv

def csv_to_jsonl(csv_path, jsonl_path):
    if not os.path.exists(csv_path):
        print(f"Error: {csv_path} does not exist.")
        sys.exit(1)
        
    entries = []
    # Read CSV with utf-8-sig to automatically handle UTF-8 BOM if present
    with open(csv_path, 'r', encoding='utf-8-sig', newline='') as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames if reader.fieldnames else []
        
        # Identify question columns dynamically (e.g., question_1, question_2, ...)
        question_fields = sorted(
            [field for field in fieldnames if field.startswith('question_')],
            key=lambda x: int(x.split('_')[1]) if x.split('_')[1].isdigit() else x
        )
        
        for idx, row in enumerate(reader):
            entry = {}
            
            # 1. Process image field if it exists and is not empty
            if 'image' in row and row['image'].strip():
                entry['image'] = row['image'].strip()
                
            # 2. Collect all non-empty questions
            questions = []
            for q_field in question_fields:
                q_val = row.get(q_field, '').strip()
                if q_val:
                    questions.append(q_val)
            entry['questions'] = questions
            
            # 3. Add answer field
            entry['answer'] = row.get('answer', '').strip()
            
            entries.append(entry)
            
    # Write JSONL
    with open(jsonl_path, 'w', encoding='utf-8') as f:
        for entry in entries:
            # Ensure Korean characters are written as-is instead of escaped unicode (e.g., \uac00)
            f.write(json.dumps(entry, ensure_ascii=False) + '\n')
            
    print(f"Successfully converted {csv_path} to {jsonl_path}")

def main():
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python3 scripts/csv2jsonl.py <ko|en|fr>")
        print("  python3 scripts/csv2jsonl.py <input_csv_path> <output_jsonl_path>")
        sys.exit(1)
        
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    if len(sys.argv) == 2:
        lang = sys.argv[1].lower()
        if lang not in ["ko", "en", "fr"]:
            print(f"Error: Unknown language '{sys.argv[1]}'. Expected 'ko', 'en', or 'fr'.")
            sys.exit(1)
        csv_path = os.path.join(project_root, "assets", f"{lang}.csv")
        jsonl_path = os.path.join(project_root, "assets", f"{lang}.jsonl")
    else:
        csv_path = sys.argv[1]
        jsonl_path = sys.argv[2]
        
    csv_to_jsonl(csv_path, jsonl_path)

if __name__ == "__main__":
    main()
