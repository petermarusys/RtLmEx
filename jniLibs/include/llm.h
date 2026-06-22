#pragma once

#include <string>
#include <functional>
#include <chrono>
#include "llmsdk.h"

// 콜백 타입 정의
using LLMCallback = std::function<void(const std::string& text, bool is_done, bool is_error)>;

class LLM {
public:
    LLM();
    ~LLM();

    bool init(const std::string& model_path,
              const std::string& tokenizer_path,
              const std::string& model_type = "");

    void run(const std::string& prompt, LLMCallback callback);
    void reset();
    void stop();
    void uninit();

private:
    LLMContext context_;
    LLMCallback current_callback_;
    bool initialized_ = false;

    static void sdkCallback(AML_LLMResult* result, void* userdata, AML_LLMRunStatus run_status);
};