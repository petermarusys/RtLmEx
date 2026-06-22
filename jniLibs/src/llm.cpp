#include "llm.h"
#include <cstring>
#include <iostream>

LLM::LLM() : context_(nullptr), initialized_(false) {}

LLM::~LLM() {
    uninit();
}

void LLM::sdkCallback(AML_LLMResult* result, void* userdata, AML_LLMRunStatus run_status) {
    if (!userdata) return;
    LLM* self = static_cast<LLM*>(userdata);

    if (!self->current_callback_) return;

    if (run_status == AML_LLM_RUN_NORMAL) {
        self->current_callback_(result->text ? result->text : "", false, false);
    } else if (run_status == AML_LLM_RUN_FINISH) {
        self->current_callback_("", true, false);
    } else if (run_status == AML_LLM_RUN_ERROR) {
        self->current_callback_("", false, true);
    }
}

bool LLM::init(const std::string& model_path,
               const std::string& tokenizer_path,
               const std::string& model_type) {
    AML_LLMInitConfig config;
    memset(&config, 0, sizeof(AML_LLMInitConfig));
    config.model_path      = model_path.c_str();
    config.tokenizer_path  = tokenizer_path.c_str();
    config.sampling_mode   = AML_LLM_ARG_Max;
    config.repeat_penalty  = 1.5f;

    int ret = aml_llm_init(&context_, &config, sdkCallback);
    if (ret != 0) return false;

    initialized_ = true;
    return true;
}

void LLM::run(const std::string& prompt, LLMCallback callback) {
    if (!initialized_) return;

    // 🔥 매 추론 전 컨텍스트 초기화
    //aml_llm_reset(context_);

    current_callback_ = callback;

    AML_LLMInput input;
    memset(&input, 0, sizeof(AML_LLMInput));
    input.input_type   = AML_LLM_INPUT_PROMPT;
    input.prompt_input = prompt.c_str();

    AML_LLMRunConfig run_config;
    memset(&run_config, 0, sizeof(AML_LLMRunConfig));
    run_config.run_mode       = AML_LLM_RUN_GENERATE;
    run_config.retain_history = 0;

    aml_llm_run(context_, &input, &run_config, this);
}

void LLM::reset() {
    if (initialized_) aml_llm_reset(context_);
}

void LLM::stop() {
    if (initialized_) aml_llm_break(context_);
}

void LLM::uninit() {
    if (initialized_) {
        aml_llm_uninit(context_);
        initialized_ = false;
    }
}