import os
import json
import struct
import math
import numpy as np
import flatbuffers
import sentencepiece as spm
from ai_edge_litert.interpreter import Interpreter

def make_flatbuffer(embeddings_list):
    builder = flatbuffers.Builder(1024 * 1024)
    embedding_offsets = []
    for values in embeddings_list:
        # Step 1: Write values vector
        builder.StartVector(4, len(values), 4)
        for val in reversed(values):
            builder.PrependFloat32(val)
        values_vector = builder.EndVector()
        
        # Step 2: Write Embedding table
        builder.StartObject(1)
        builder.PrependUOffsetTRelativeSlot(0, values_vector, 0)
        emb_offset = builder.EndObject()
        embedding_offsets.append(emb_offset)
        
    # Step 3: Write embeddings vector
    builder.StartVector(4, len(embedding_offsets), 4)
    for off in reversed(embedding_offsets):
        builder.PrependUOffsetTRelative(off)
    embeddings_vector = builder.EndVector()
    
    # Step 4: Write DatabaseEmbeddings table
    builder.StartObject(1)
    builder.PrependUOffsetTRelativeSlot(0, embeddings_vector, 0)
    db_offset = builder.EndObject()
    
    builder.Finish(db_offset)
    return builder.Output()

def get_embedding(interpreter, sp, text, input_index, output_index):
    # Prepend the standard document prefix for Gemma 3 embedding model
    text_with_prefix = f"title: none | text: {text}"
    tokens = [2] + sp.encode_as_ids(text_with_prefix)
    
    # Pad or truncate to 256 tokens
    if len(tokens) < 256:
        tokens = tokens + [0] * (256 - len(tokens))
    else:
        tokens = tokens[:256]
        
    input_data = np.array([tokens], dtype=np.int32)
    interpreter.set_tensor(input_index, input_data)
    interpreter.invoke()
    
    emb = interpreter.get_tensor(output_index)[0]
    
    # L2 normalize the embedding
    norm = math.sqrt(sum(x * x for x in emb))
    if norm > 0:
        emb = [x / norm for x in emb]
    return emb

def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    
    model_path = os.path.join(script_dir, "embeddinggemma-300M_seq256_mixed-precision.tflite")
    sp_path = os.path.join(script_dir, "sentencepiece.model")
    jsonl_path = os.path.join(project_root, "assets", "ko.jsonl")
    fb_path = os.path.join(project_root, "assets", "ko.fb")
    
    print("Loading sentencepiece tokenizer...")
    sp = spm.SentencePieceProcessor(model_file=sp_path)
    
    print("Loading LiteRT embedding model...")
    interpreter = Interpreter(model_path=model_path)
    interpreter.allocate_tensors()
    
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    input_index = input_details[0]['index']
    output_index = output_details[0]['index']
    
    print(f"Reading JSONL from {jsonl_path}...")
    
    embeddings = []
    count = 0
    
    with open(jsonl_path, "r", encoding="utf-8") as f:
        for idx, line in enumerate(f):
            line = line.strip()
            if not line:
                continue
            
            try:
                data = json.loads(line)
                questions = data.get("questions", [])
                answer = data.get("answer", "")
                
                canonical = questions[0] if len(questions) > 0 else ""
                variants = questions[1:] if len(questions) > 1 else []
                
                text_to_embed = f"[Questions] {canonical}. "
                if variants:
                    text_to_embed += ", ".join(variants) + ". "
                text_to_embed += f"[Answer] {answer}."
                
                print(f"Generating embedding for entry {idx+1}: {canonical[:40]}...")
                emb = get_embedding(interpreter, sp, text_to_embed, input_index, output_index)
                embeddings.append(emb)
                count += 1
            except Exception as e:
                print(f"Error parsing line {idx+1}: {e}")
        
    print(f"Generated {count} embeddings. Building FlatBuffer...")
    fb_data = make_flatbuffer(embeddings)
    
    print(f"Saving FlatBuffer to {fb_path}...")
    with open(fb_path, "wb") as f:
        f.write(fb_data)
        
    print("Embedding database reconstruction from JSONL complete!")

if __name__ == "__main__":
    main()
