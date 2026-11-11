/* Linuxoid's small root-only mount helper. SPDX-License-Identifier: GPL-3.0-only */
#define _GNU_SOURCE
#include <sys/mount.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <limits.h>
int main(int argc,char **argv) {
    const char *prefix="/data/local/linuxoid/";
    if(argc!=3 || getuid()!=0) return 2;
    char canonical[PATH_MAX];
    size_t n=strlen(argv[2]);
    if(n<strlen(prefix)+36+7 || strncmp(argv[2],prefix,strlen(prefix)) || strcmp(argv[2]+n-7,"/rootfs") || !realpath(argv[2],canonical) || strcmp(canonical,argv[2])) {
        fputs("Expected a canonical Linuxoid rootfs path\n",stderr); return 2;
    }
    int result;
    if(!strcmp(argv[1],"bind"))result=mount(argv[2],argv[2],NULL,MS_BIND,NULL);
    else if(!strcmp(argv[1],"private"))result=mount(NULL,argv[2],NULL,MS_PRIVATE|MS_REC,NULL);
    else if(!strcmp(argv[1],"suid"))result=mount(NULL,argv[2],NULL,MS_REMOUNT|MS_BIND,NULL);
    else return 2;
    if(result){perror("Linuxoid rootfs mount");return 1;}return 0;
}
