/*
 * Lychee: check the Cantonese scheme on the build machine's librime.
 * Usage: schema_test <shared data dir> <user data dir> < cases
 * Each case line: <keys>\t<word that must be among the first 10 candidates>
 */
#include <rime_api.h>
#include <stdio.h>
#include <string.h>

int main(int argc, char *argv[]) {
    if (argc != 3) {
        fprintf(stderr, "usage: %s <shared dir> <user dir> < cases\n", argv[0]);
        return 2;
    }
    RimeApi *rime = rime_get_api();
    RIME_STRUCT(RimeTraits, traits);
    traits.shared_data_dir = argv[1];
    traits.user_data_dir = argv[2];
    traits.app_name = "rime.lychee.test";
    rime->setup(&traits);
    rime->initialize(&traits);
    if (!rime->start_maintenance(True)) {
        fprintf(stderr, "deploy failed to start\n");
        return 2;
    }
    rime->join_maintenance_thread();
    RimeSessionId session = rime->create_session();
    if (!rime->select_schema(session, "lychee_cantonese")) {
        fprintf(stderr, "lychee_cantonese did not deploy\n");
        return 2;
    }
    char line[256];
    int failed = 0, total = 0;
    while (fgets(line, sizeof line, stdin)) {
        line[strcspn(line, "\n")] = 0;
        char *tab = strchr(line, '\t');
        if (!tab || line[0] == '#') continue;
        *tab = 0;
        const char *keys = line, *want = tab + 1;
        rime->clear_composition(session);
        rime->simulate_key_sequence(session, keys);
        RIME_STRUCT(RimeContext, ctx);
        int found = 0;
        char seen[512] = "";
        if (rime->get_context(session, &ctx)) {
            for (int i = 0; i < ctx.menu.num_candidates; i++) {
                const RimeCandidate *c = &ctx.menu.candidates[i];
                if (strcmp(c->text, want) == 0) found = 1;
                if (strlen(seen) + strlen(c->text) + 40 < sizeof seen) {
                    strcat(seen, " ");
                    strcat(seen, c->text);
                    if (c->comment) { strcat(seen, "("); strcat(seen, c->comment); strcat(seen, ")"); }
                }
            }
            rime->free_context(&ctx);
        }
        total++;
        if (!found) failed++;
        printf("%s %-14s -> %s:%s\n", found ? "ok  " : "FAIL", keys, want, seen);
    }
    rime->destroy_session(session);
    rime->finalize();
    printf("%d/%d passed\n", total - failed, total);
    return failed ? 1 : 0;
}
