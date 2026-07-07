import os
import sys
import json
import csv

def jsonl_to_csv(jsonl_path, csv_path):
    # First pass to find maximum number of questions and all keys
    max_questions = 0
    has_image = False
    
    rows = []
    if not os.path.exists(jsonl_path):
        print(f"Error: {jsonl_path} does not exist.")
        sys.exit(1)
        
    with open(jsonl_path, 'r', encoding='utf-8') as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            try:
                data = json.loads(line)
                rows.append(data)
                questions = data.get('questions', [])
                max_questions = max(max_questions, len(questions))
                if 'image' in data:
                    has_image = True
            except Exception as e:
                print(f"Warning: Failed to parse line: {e}")
                
    # Prepare header
    header = []
    if has_image:
        header.append('image')
    
    for i in range(1, max_questions + 1):
        header.append(f'question_{i}')
        
    header.append('answer')
    
    # Write CSV with UTF-8 BOM (utf-8-sig) for Excel compatibility
    with open(csv_path, 'w', encoding='utf-8-sig', newline='') as f:
        writer = csv.writer(f)
        writer.writerow(header)
        for data in rows:
            row_data = []
            if has_image:
                row_data.append(data.get('image', ''))
            
            questions = data.get('questions', [])
            for i in range(max_questions):
                if i < len(questions):
                    row_data.append(questions[i])
                else:
                    row_data.append('')
            
            row_data.append(data.get('answer', ''))
            writer.writerow(row_data)
            
    print(f"Successfully converted {jsonl_path} to {csv_path}")

def main():
    if len(sys.argv) < 2:
        print("Usage:")
        print("  python3 scripts/jsonl2csv.py <ko|en|fr>")
        print("  python3 scripts/jsonl2csv.py <input_jsonl_path> <output_csv_path>")
        sys.exit(1)
        
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    if len(sys.argv) == 2:
        lang = sys.argv[1].lower()
        if lang not in ["ko", "en", "fr"]:
            print(f"Error: Unknown language '{sys.argv[1]}'. Expected 'ko', 'en', or 'fr'.")
            sys.exit(1)
        jsonl_path = os.path.join(project_root, "assets", f"{lang}.jsonl")
        csv_path = os.path.join(project_root, "assets", f"{lang}.csv")
    else:
        jsonl_path = sys.argv[1]
        csv_path = sys.argv[2]
        
    jsonl_to_csv(jsonl_path, csv_path)

if __name__ == "__main__":
    main()
