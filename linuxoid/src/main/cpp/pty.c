#define _GNU_SOURCE
#include <jni.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <termios.h>
#include <errno.h>

JNIEXPORT jintArray JNICALL Java_io_groxaxo_linuxoid_Pty_open(JNIEnv *env, jclass type, jstring command) {
    (void)type;
    const char *cmd = (*env)->GetStringUTFChars(env, command, NULL);
    if (!cmd) return NULL;
    int master = posix_openpt(O_RDWR | O_NOCTTY | O_CLOEXEC);
    if (master < 0 || grantpt(master) || unlockpt(master)) { if (master >= 0) close(master); (*env)->ReleaseStringUTFChars(env, command, cmd); return NULL; }
    char path[128];
    if (ptsname_r(master, path, sizeof(path))) { close(master); (*env)->ReleaseStringUTFChars(env, command, cmd); return NULL; }
    pid_t pid = fork();
    if (pid < 0) { close(master); (*env)->ReleaseStringUTFChars(env, command, cmd); return NULL; }
    if (pid == 0) {
        setsid();
        int slave = open(path, O_RDWR);
        if (slave < 0) _exit(125);
        ioctl(slave, TIOCSCTTY, 0);
        struct termios t;
        if (!tcgetattr(slave, &t)) { t.c_lflag &= ~ECHO; tcsetattr(slave, TCSANOW, &t); }
        for (int i = 0; i < 3; ++i) dup2(slave, i);
        if (slave > 2) close(slave);
        close(master);
        execlp("su", "su", "-c", cmd, (char *)NULL);
        _exit(127);
    }
    (*env)->ReleaseStringUTFChars(env, command, cmd);
    jint values[] = { master, (jint)pid };
    jintArray result = (*env)->NewIntArray(env, 2);
    if (result) (*env)->SetIntArrayRegion(env, result, 0, 2, values);
    return result;
}
JNIEXPORT jbyteArray JNICALL Java_io_groxaxo_linuxoid_Pty_read(JNIEnv *env, jclass type, jint fd) {
    (void)type;
    char data[8192]; ssize_t n;
    do { n = read(fd, data, sizeof(data)); } while (n < 0 && errno == EINTR);
    if (n <= 0) return NULL;
    jbyteArray out = (*env)->NewByteArray(env, (jsize)n);
    if (out) (*env)->SetByteArrayRegion(env, out, 0, (jsize)n, (jbyte *)data);
    return out;
}
JNIEXPORT jboolean JNICALL Java_io_groxaxo_linuxoid_Pty_write(JNIEnv *env, jclass type, jint fd, jbyteArray input) {
    (void)type;
    jsize n = (*env)->GetArrayLength(env, input);
    jbyte *data = (*env)->GetByteArrayElements(env, input, NULL);
    if (!data) return JNI_FALSE;
    ssize_t sent = 0;
    while (sent < n) {
        ssize_t k = write(fd, data + sent, (size_t)(n - sent));
        if (k < 0 && errno == EINTR) continue;
        if (k <= 0) break;
        sent += k;
    }
    (*env)->ReleaseByteArrayElements(env, input, data, JNI_ABORT);
    return sent == n;
}
JNIEXPORT void JNICALL Java_io_groxaxo_linuxoid_Pty_resize(JNIEnv *env, jclass type, jint fd, jint cols, jint rows) {
    (void)env; (void)type;
    struct winsize size = { .ws_row = (unsigned short)rows, .ws_col = (unsigned short)cols };
    ioctl(fd, TIOCSWINSZ, &size);
}
JNIEXPORT void JNICALL Java_io_groxaxo_linuxoid_Pty_close(JNIEnv *env, jclass type, jint fd, jint pid) {
    (void)env; (void)type;
    close(fd);
    if (pid > 0) { kill(pid, SIGHUP); waitpid(pid, NULL, WNOHANG); }
}
