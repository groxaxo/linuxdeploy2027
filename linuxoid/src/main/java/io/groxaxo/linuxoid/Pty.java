package io.groxaxo.linuxoid;

public final class Pty {
  static {
    System.loadLibrary("linuxoid_pty");
  }

  public static native int[] open(String command);

  public static native byte[] read(int fd);

  public static native boolean write(int fd, byte[] data);

  public static native void resize(int fd, int columns, int rows);

  public static native void close(int fd, int pid);

  private Pty() {}
}
