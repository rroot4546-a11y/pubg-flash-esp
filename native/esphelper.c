/*
 * Root-side memory reader for PUBG MOBILE FLASH 4.6.1 (64-bit).
 * Runs as root, opens /proc/<pid>/mem, speaks a small framed protocol over
 * an AF_UNIX socket. No code is injected into the game and nothing is written
 * to the game process.
 */
#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <sys/stat.h>
#include <sys/un.h>
#include <sys/types.h>
#include <unistd.h>
#include <dirent.h>

#define SOCKET_PATH "/data/local/tmp/ofx_esp.sock"
#define DUMP_PATH   "/sdcard/Download/esp_dump.bin"
#define MAX_ACTORS  512
#define ENTRY_SIZE  64

typedef struct __attribute__((packed)) {
    uint64_t world_ptr_off;
    int64_t  world_deref_delta;
    int64_t  world_final_add;
    uint32_t world_mode;
    uint64_t view_ptr_off;
    int64_t  view_add;
    uint64_t gname_ptr_off;
    int64_t  gname_add;
    uint64_t guobject_ptr_off;
    uint64_t gnative_ptr_off;
    uint64_t level_off;
    uint64_t actors_off;
    uint64_t root_off;
    uint64_t location_off;
    uint64_t health_off;
    uint64_t team_off;
    uint64_t name_ptr_off;
    float    head_height;
    uint32_t reserved;
} EspConfig;

typedef struct __attribute__((packed)) {
    uint8_t  tag;
    uint8_t  status;
    uint32_t count;
    float    camera[3];
    float    view[16];
} FrameHeader;

typedef struct __attribute__((packed)) {
    float    x, y, z;
    float    head_z;
    float    health;
    int32_t  team_id;
    uint8_t  is_bot;
    char     name[32];
    uint8_t  pad[7];
} EspEntry;

static EspConfig cfg;
static int mem_fd = -1;
static pid_t current_pid = -1;
static uint64_t lib_base = 0;

static int recv_all(int fd, void *buf, size_t len) {
    uint8_t *p = (uint8_t *)buf;
    size_t done = 0;
    while (done < len) {
        ssize_t n = recv(fd, p + done, len - done, 0);
        if (n <= 0) return -1;
        done += (size_t)n;
    }
    return 0;
}

static int send_all(int fd, const void *buf, size_t len) {
    const uint8_t *p = (const uint8_t *)buf;
    size_t done = 0;
    while (done < len) {
        ssize_t n = send(fd, p + done, len - done, MSG_NOSIGNAL);
        if (n <= 0) return -1;
        done += (size_t)n;
    }
    return 0;
}

static int read_mem(uint64_t addr, void *out, size_t len) {
    if (mem_fd < 0 || addr == 0) return -1;
    uint8_t *dst = (uint8_t *)out;
    size_t done = 0;
    while (done < len) {
        ssize_t n = pread(mem_fd, dst + done, len - done, (off_t)(addr + done));
        if (n <= 0) return -1;
        done += (size_t)n;
    }
    return 0;
}

static uint64_t rd64(uint64_t addr) {
    uint64_t v = 0;
    if (read_mem(addr, &v, 8) != 0) return 0;
    return v;
}
static uint32_t rd32(uint64_t addr) {
    uint32_t v = 0;
    if (read_mem(addr, &v, 4) != 0) return 0;
    return v;
}
static float rdf(uint64_t addr) {
    float v = 0.0f;
    if (read_mem(addr, &v, 4) != 0) return 0.0f;
    return v;
}
static int valid_ptr(uint64_t p) {
    return p > 0x10000000ULL && p < 0x800000000000ULL && (p & 3) == 0;
}

static pid_t find_pid(const char *pkg) {
    DIR *d = opendir("/proc");
    if (!d) return -1;
    struct dirent *e;
    pid_t found = -1;
    while ((e = readdir(d))) {
        if (e->d_name[0] < '0' || e->d_name[0] > '9') continue;
        char path[128];
        snprintf(path, sizeof(path), "/proc/%s/cmdline", e->d_name);
        FILE *f = fopen(path, "rb");
        if (!f) continue;
        char buf[512];
        size_t n = fread(buf, 1, sizeof(buf) - 1, f);
        fclose(f);
        buf[n] = 0;
        if (n == 0) continue;
        for (size_t i = 0; i < n; i++) if (buf[i] == 0) buf[i] = ' ';
        if (strncmp(buf, pkg, strlen(pkg)) == 0 &&
            (buf[strlen(pkg)] == ' ' || buf[strlen(pkg)] == 0)) {
            found = (pid_t)strtol(e->d_name, NULL, 10);
            break;
        }
    }
    closedir(d);
    return found;
}

static uint64_t find_base(pid_t pid) {
    char path[128];
    snprintf(path, sizeof(path), "/proc/%d/maps", pid);
    FILE *f = fopen(path, "r");
    if (!f) return 0;
    char line[1024];
    uint64_t base = 0;
    while (fgets(line, sizeof(line), f)) {
        if (strstr(line, "libUE4.so") || strstr(line, "libUE4")) {
            base = strtoull(line, NULL, 16);
            break;
        }
    }
    fclose(f);
    return base;
}

static void ensure_target(const char *pkg) {
    pid_t pid = find_pid(pkg);
    if (pid < 0 || pid == current_pid) return;
    if (mem_fd >= 0) close(mem_fd);
    char path[128];
    snprintf(path, sizeof(path), "/proc/%d/mem", pid);
    mem_fd = open(path, O_RDONLY);
    current_pid = mem_fd >= 0 ? pid : -1;
    lib_base = mem_fd >= 0 ? find_base(pid) : 0;
}

static uint64_t resolve_world(void) {
    uint64_t p = rd64(lib_base + cfg.world_ptr_off);
    if (!valid_ptr(p)) return 0;
    if (cfg.world_mode == 1) return p + (uint64_t)cfg.world_final_add;
    uint64_t q = rd64((uint64_t)((int64_t)p + cfg.world_deref_delta));
    if (!valid_ptr(q)) return 0;
    if (cfg.world_mode == 2) return q;
    return q + (uint64_t)cfg.world_final_add;
}

static void read_fstring(uint64_t addr, char *out, size_t cap) {
    memset(out, 0, cap);
    if (!valid_ptr(addr)) return;
    uint16_t chars[32];
    if (read_mem(addr, chars, sizeof(chars)) != 0) return;
    size_t n = 0;
    for (size_t i = 0; i < 32 && n + 1 < cap; i++) {
        uint16_t c = chars[i];
        if (c == 0) break;
        if (c < 0x80) out[n++] = (char)c;
        else if (c >= 0xD800 && c <= 0xDFFF) continue;
        else out[n++] = '?';
    }
    out[n] = 0;
}

static int build_frame(uint8_t *out, size_t *out_len, const char *pkg) {
    ensure_target(pkg);
    FrameHeader h;
    memset(&h, 0, sizeof(h));
    h.tag = 'F';
    *out_len = sizeof(FrameHeader);
    if (mem_fd < 0) { h.status = 1; memcpy(out, &h, sizeof(h)); return 0; }
    if (!valid_ptr(lib_base)) { h.status = 2; memcpy(out, &h, sizeof(h)); return 0; }

    uint64_t world = resolve_world();
    if (!valid_ptr(world)) { h.status = 3; memcpy(out, &h, sizeof(h)); return 0; }

    uint64_t vm_ptr = rd64(lib_base + cfg.view_ptr_off);
    uint64_t vm_addr = valid_ptr(vm_ptr) ? vm_ptr + (uint64_t)cfg.view_add : 0;
    if (valid_ptr(vm_addr)) read_mem(vm_addr, h.view, sizeof(h.view));
    h.camera[0] = h.view[12];
    h.camera[1] = h.view[13];
    h.camera[2] = h.view[14];

    uint64_t level = rd64(world + cfg.level_off);
    if (!valid_ptr(level)) { h.status = 4; memcpy(out, &h, sizeof(h)); return 0; }
    uint64_t arr = rd64(level + cfg.actors_off);
    uint32_t count = rd32(level + cfg.actors_off + 8);
    if (!valid_ptr(arr) || count == 0) { memcpy(out, &h, sizeof(h)); return 0; }
    if (count > MAX_ACTORS) count = MAX_ACTORS;

    uint64_t actor_buf[MAX_ACTORS];
    if (read_mem(arr, actor_buf, count * 8) != 0) { memcpy(out, &h, sizeof(h)); return 0; }

    EspEntry *entries = (EspEntry *)(out + sizeof(FrameHeader));
    uint32_t kept = 0;
    for (uint32_t i = 0; i < count; i++) {
        uint64_t actor = actor_buf[i];
        if (!valid_ptr(actor)) continue;
        uint64_t root = rd64(actor + cfg.root_off);
        if (!valid_ptr(root)) continue;
        uint64_t loc_addr = root + cfg.location_off;
        float x = rdf(loc_addr), y = rdf(loc_addr + 4), z = rdf(loc_addr + 8);
        if (x < -100000.0f || x > 100000.0f || y < -100000.0f || y > 100000.0f) continue;
        if (z < -5000.0f || z > 20000.0f) continue;

        float health = rdf(actor + cfg.health_off);
        int32_t team = (int32_t)rd32(actor + cfg.team_off);
        char name[32];
        read_fstring(rd64(actor + cfg.name_ptr_off), name, sizeof(name));
        if (name[0] == 0 && (health <= 0.0f || health > 200.0f)) continue;

        EspEntry *e = &entries[kept];
        memset(e, 0, sizeof(*e));
        e->x = x; e->y = y; e->z = z;
        e->head_z = z + cfg.head_height;
        e->health = health;
        e->team_id = team;
        e->is_bot = (uint8_t)(strstr(name, "Bot") != NULL);
        snprintf(e->name, sizeof(e->name), "%s", name);
        kept++;
        if (kept >= MAX_ACTORS) break;
    }
    h.count = kept;
    memcpy(out, &h, sizeof(h));
    *out_len = sizeof(FrameHeader) + (size_t)kept * ENTRY_SIZE;
    return 0;
}

int main(int argc, char **argv) {
    const char *pkg = argc > 1 ? argv[1] : "com.tencent.igfit";
    unlink(SOCKET_PATH);
    int srv = socket(AF_UNIX, SOCK_STREAM, 0);
    if (srv < 0) return 10;

    struct sockaddr_un addr;
    memset(&addr, 0, sizeof(addr));
    addr.sun_family = AF_UNIX;
    strncpy(addr.sun_path, SOCKET_PATH, sizeof(addr.sun_path) - 1);
    if (bind(srv, (struct sockaddr *)&addr, sizeof(addr)) != 0) return 11;
    chmod(SOCKET_PATH, 0666);
    if (listen(srv, 2) != 0) return 12;

    int client = accept(srv, NULL, NULL);
    if (client < 0) return 13;
    close(srv);

    uint8_t *scratch = (uint8_t *)malloc(sizeof(FrameHeader) + MAX_ACTORS * ENTRY_SIZE + 64);
    if (!scratch) { close(client); return 14; }

    for (;;) {
        uint8_t cmd;
        if (recv_all(client, &cmd, 1) != 0) break;

        if (cmd == 'C') {
            if (recv_all(client, &cfg, sizeof(cfg)) != 0) break;
            if (mem_fd >= 0) close(mem_fd);
            mem_fd = -1;
            current_pid = -1;
            lib_base = 0;
            const uint8_t ack = 0;
            if (send_all(client, &ack, 1) != 0) break;
        } else if (cmd == 'H') {
            ensure_target(pkg);
            uint8_t reply[14];
            reply[0] = 'H';
            uint32_t pid = (uint32_t)(current_pid > 0 ? current_pid : 0);
            memcpy(reply + 1, &pid, 4);
            memcpy(reply + 5, &lib_base, 8);
            reply[13] = (uint8_t)(mem_fd >= 0 ? 0 : 1);
            if (send_all(client, reply, sizeof(reply)) != 0) break;
        } else if (cmd == 'F') {
            size_t len = 0;
            build_frame(scratch, &len, pkg);
            if (send_all(client, scratch, len) != 0) break;
        } else if (cmd == 'D') {
            uint64_t daddr; uint32_t dlen;
            if (recv_all(client, &daddr, 8) != 0) break;
            if (recv_all(client, &dlen, 4) != 0) break;
            if (dlen > 0x10000) dlen = 0x10000;
            uint8_t *buf = (uint8_t *)malloc(dlen ? dlen : 1);
            uint8_t status = 1;
            if (buf) {
                status = (uint8_t)(read_mem(daddr, buf, dlen) == 0 ? 0 : 1);
                if (status == 0) {
                    FILE *f = fopen(DUMP_PATH, "wb");
                    if (f) { fwrite(buf, 1, dlen, f); fclose(f); }
                    else status = 2;
                }
                free(buf);
            }
            if (send_all(client, &status, 1) != 0) break;
        } else if (cmd == 'X') {
            break;
        }
    }

    free(scratch);
    close(client);
    if (mem_fd >= 0) close(mem_fd);
    unlink(SOCKET_PATH);
    return 0;
}
