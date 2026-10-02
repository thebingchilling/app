// Lychee: a small JNI bridge to librime for app.lychee.rime.Rime.
// SPDX-License-Identifier: GPL-3.0-only

#include <jni.h>
#include <opencc/opencc.h>
#include <rime_api.h>

#include <cstdint>
#include <string>
#include <vector>

#ifdef __ANDROID__
#include <android/log.h>
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "LycheeRime", __VA_ARGS__)
#else  // host build for tests (test/host_jni.sh)
#include <cstdio>
#define LOGI(...) (fprintf(stderr, __VA_ARGS__), fputc('\n', stderr))
#endif

namespace {

RimeApi *rime = nullptr;
std::string sharedDir, userDir;

std::string toStd(JNIEnv *env, jstring s) {
    if (!s) return {};
    const char *c = env->GetStringUTFChars(s, nullptr);
    std::string r(c);
    env->ReleaseStringUTFChars(s, c);
    return r;
}

// NewStringUTF wants modified UTF-8, which breaks on characters outside the
// BMP (common in Rime's dictionaries), so build strings from UTF-16.
jstring toJava(JNIEnv *env, const char *s) {
    if (!s) return nullptr;
    std::u16string out;
    const auto *p = reinterpret_cast<const unsigned char *>(s);
    while (*p) {
        uint32_t c = *p++;
        int extra = c >= 0xF0 ? 3 : c >= 0xE0 ? 2 : c >= 0xC0 ? 1 : 0;
        if (extra) c &= (0x3F >> extra);
        for (int i = 0; i < extra && (*p & 0xC0) == 0x80; ++i) c = (c << 6) | (*p++ & 0x3F);
        if (c >= 0x10000) {
            c -= 0x10000;
            out.push_back(static_cast<char16_t>(0xD800 + (c >> 10)));
            out.push_back(static_cast<char16_t>(0xDC00 + (c & 0x3FF)));
        } else {
            out.push_back(static_cast<char16_t>(c));
        }
    }
    return env->NewString(reinterpret_cast<const jchar *>(out.data()), static_cast<jsize>(out.size()));
}

void onNotification(void *, RimeSessionId, const char *type, const char *value) {
    LOGI("notification %s: %s", type, value);
}

void fillTraits(RimeTraits &traits) {
    traits.shared_data_dir = sharedDir.c_str();
    traits.user_data_dir = userDir.c_str();
    traits.distribution_name = "Lychee";
    traits.distribution_code_name = "lychee";
    traits.distribution_version = "2.0";
    traits.app_name = "rime.lychee";
    traits.min_log_level = 2;
    traits.log_dir = "";
}

}  // namespace

extern "C" {

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeStartup(JNIEnv *env, jclass, jstring shared, jstring user, jboolean fullCheck) {
    if (!rime) rime = rime_get_api();
    sharedDir = toStd(env, shared);
    userDir = toStd(env, user);
    RIME_STRUCT(RimeTraits, traits);
    fillTraits(traits);
    static bool setUp = false;  // setup() may only run once per process
    if (!setUp) {
        rime->setup(&traits);
        rime->set_notification_handler(onNotification, nullptr);
        setUp = true;
    }
    rime->initialize(&traits);
    // Deploys (compiles) whatever is out of date; the prebuilt dictionaries
    // shipped in the APK are up to date, so this is quick.
    if (rime->start_maintenance(fullCheck)) rime->join_maintenance_thread();
}

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeRedeploy(JNIEnv *, jclass) {
    if (!rime) return;
    rime->finalize();
    RIME_STRUCT(RimeTraits, traits);
    fillTraits(traits);
    rime->initialize(&traits);
    if (rime->start_maintenance(True)) rime->join_maintenance_thread();
}

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeShutdown(JNIEnv *, jclass) {
    if (rime) rime->finalize();
}

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeSyncUserData(JNIEnv *, jclass) {
    if (rime) rime->sync_user_data();
}

JNIEXPORT jlong JNICALL
Java_app_lychee_rime_Rime_nativeCreateSession(JNIEnv *, jclass) {
    return rime ? static_cast<jlong>(rime->create_session()) : 0;
}

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeDestroySession(JNIEnv *, jclass, jlong s) {
    if (rime && s) rime->destroy_session(static_cast<RimeSessionId>(s));
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeSelectSchema(JNIEnv *env, jclass, jlong s, jstring id) {
    return rime->select_schema(static_cast<RimeSessionId>(s), toStd(env, id).c_str());
}

JNIEXPORT jstring JNICALL
Java_app_lychee_rime_Rime_nativeCurrentSchema(JNIEnv *env, jclass, jlong s) {
    char buf[100] = {0};
    if (!rime->get_current_schema(static_cast<RimeSessionId>(s), buf, sizeof(buf))) return nullptr;
    return toJava(env, buf);
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeProcessKey(JNIEnv *, jclass, jlong s, jint keycode, jint mask) {
    return rime->process_key(static_cast<RimeSessionId>(s), keycode, mask);
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeSetInput(JNIEnv *env, jclass, jlong s, jstring input) {
    return rime->set_input(static_cast<RimeSessionId>(s), toStd(env, input).c_str());
}

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeClearComposition(JNIEnv *, jclass, jlong s) {
    rime->clear_composition(static_cast<RimeSessionId>(s));
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeCommitComposition(JNIEnv *, jclass, jlong s) {
    return rime->commit_composition(static_cast<RimeSessionId>(s));
}

JNIEXPORT jstring JNICALL
Java_app_lychee_rime_Rime_nativeGetCommit(JNIEnv *env, jclass, jlong s) {
    RIME_STRUCT(RimeCommit, commit);
    if (!rime->get_commit(static_cast<RimeSessionId>(s), &commit)) return nullptr;
    jstring r = toJava(env, commit.text);
    rime->free_commit(&commit);
    return r;
}

JNIEXPORT jstring JNICALL
Java_app_lychee_rime_Rime_nativeGetInput(JNIEnv *env, jclass, jlong s) {
    return toJava(env, rime->get_input(static_cast<RimeSessionId>(s)));
}

// {preedit, commit text preview} or null when not composing
JNIEXPORT jobjectArray JNICALL
Java_app_lychee_rime_Rime_nativeGetComposition(JNIEnv *env, jclass, jlong s) {
    RIME_STRUCT(RimeContext, ctx);
    if (!rime->get_context(static_cast<RimeSessionId>(s), &ctx)) return nullptr;
    jobjectArray r = nullptr;
    if (ctx.composition.length > 0 || (ctx.composition.preedit && *ctx.composition.preedit)) {
        r = env->NewObjectArray(2, env->FindClass("java/lang/String"), nullptr);
        env->SetObjectArrayElement(r, 0, toJava(env, ctx.composition.preedit));
        if (RIME_STRUCT_HAS_MEMBER(ctx, ctx.commit_text_preview))
            env->SetObjectArrayElement(r, 1, toJava(env, ctx.commit_text_preview));
    }
    rime->free_context(&ctx);
    return r;
}

// Candidates from index start, at most max of them: text, comment, text, comment...
JNIEXPORT jobjectArray JNICALL
Java_app_lychee_rime_Rime_nativeGetCandidates(JNIEnv *env, jclass, jlong s, jint start, jint max) {
    RimeCandidateListIterator it = {nullptr};
    std::vector<std::pair<std::string, std::string>> list;
    if (rime->candidate_list_from_index(static_cast<RimeSessionId>(s), &it, start)) {
        while (static_cast<jint>(list.size()) < max && rime->candidate_list_next(&it)) {
            list.emplace_back(it.candidate.text ? it.candidate.text : "",
                              it.candidate.comment ? it.candidate.comment : "");
        }
        rime->candidate_list_end(&it);
    }
    jobjectArray r = env->NewObjectArray(static_cast<jsize>(list.size() * 2), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < list.size(); ++i) {
        jstring t = toJava(env, list[i].first.c_str());
        jstring c = toJava(env, list[i].second.c_str());
        env->SetObjectArrayElement(r, static_cast<jsize>(2 * i), t);
        env->SetObjectArrayElement(r, static_cast<jsize>(2 * i + 1), c);
        env->DeleteLocalRef(t);
        env->DeleteLocalRef(c);
    }
    return r;
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeSelectCandidate(JNIEnv *, jclass, jlong s, jint index) {
    return rime->select_candidate(static_cast<RimeSessionId>(s), index);
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeDeleteCandidate(JNIEnv *, jclass, jlong s, jint index) {
    return rime->delete_candidate(static_cast<RimeSessionId>(s), index);
}

JNIEXPORT void JNICALL
Java_app_lychee_rime_Rime_nativeSetOption(JNIEnv *env, jclass, jlong s, jstring name, jboolean value) {
    rime->set_option(static_cast<RimeSessionId>(s), toStd(env, name).c_str(), value);
}

JNIEXPORT jboolean JNICALL
Java_app_lychee_rime_Rime_nativeGetOption(JNIEnv *env, jclass, jlong s, jstring name) {
    return rime->get_option(static_cast<RimeSessionId>(s), toStd(env, name).c_str());
}

// OpenCC (built into librime) for text that does not come from Rime, e.g. voice typing.
// config is the path of an OpenCC config such as <shared>/opencc/s2hk.json.
JNIEXPORT jstring JNICALL
Java_app_lychee_rime_Rime_nativeOpenccConvert(JNIEnv *env, jclass, jstring config, jstring text) {
    std::string cfg = toStd(env, config), in = toStd(env, text);
    opencc_t cc = opencc_open(cfg.c_str());
    if (cc == reinterpret_cast<opencc_t>(-1)) return nullptr;
    char *out = opencc_convert_utf8(cc, in.c_str(), in.size());
    jstring result = out ? toJava(env, out) : nullptr;
    if (out) opencc_convert_utf8_free(out);
    opencc_close(cc);
    return result;
}

}  // extern "C"
