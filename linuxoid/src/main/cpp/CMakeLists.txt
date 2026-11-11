cmake_minimum_required(VERSION 3.22.1)
project(linuxoid_pty C)
add_library(linuxoid_pty SHARED pty.c)
target_compile_options(linuxoid_pty PRIVATE -Wall -Wextra -Werror)
