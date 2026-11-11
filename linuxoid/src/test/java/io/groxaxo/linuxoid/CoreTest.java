package io.groxaxo.linuxoid;

import static org.junit.Assert.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class CoreTest {
  private static final String HASH =
      "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

  @Test
  public void usesNewestPointImageForTheRequestedSeriesAndCpu() throws Exception {
    String manifest =
        HASH
            + " *ubuntu-base-26.04-base-arm64.tar.gz\n"
            + HASH
            + " *ubuntu-base-26.04.1-base-arm64.tar.gz\n"
            + HASH
            + " *ubuntu-base-26.04.2-base-amd64.tar.gz\n";
    UbuntuImages.Image i = UbuntuImages.parse(manifest, "26.04", "arm64");
    assertEquals("ubuntu-base-26.04.1-base-arm64.tar.gz", i.name);
    assertTrue(i.url.startsWith("https://cdimage.ubuntu.com/ubuntu-base/releases/26.04/release/"));
  }

  @Test(expected = IOException.class)
  public void refusesMissingArchitectureInsteadOfUsingAnotherCpu() throws Exception {
    UbuntuImages.parse(HASH + " *ubuntu-base-26.04.1-base-amd64.tar.gz\n", "26.04", "i386");
  }

  @Test(expected = IOException.class)
  public void refusesManifestPathsOutsideOfficialArchive() throws Exception {
    UbuntuImages.parse(HASH + " *../../ubuntu-base-26.04-base-arm64.tar.gz\n", "26.04", "arm64");
  }

  @Test
  public void numericVersionsDoNotSortLexicographically() {
    assertTrue(UbuntuImages.compareVersion("24.04.10", "24.04.9") > 0);
  }

  @Test
  public void shellArgumentsRoundTripWithoutExpandingCommands() throws Exception {
    String value = "Ubuntu's image; $(printf hacked) `printf nope`\nsecond line";
    Process p = new ProcessBuilder("sh", "-c", "printf '%s' " + RootShell.quote(value)).start();
    String got = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    assertEquals(0, p.waitFor());
    assertEquals(value, got);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsPathTraversalAsProfileId() {
    new Profile("../../data", "26.04", "arm64", Profile.Mode.MINIMAL, 0, false);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsShellSyntaxAsRelease() {
    Profile.create("26.04;id", "arm64", Profile.Mode.MINIMAL, 0, false);
  }

  @Test
  public void independentlyCreatedWorkspacesHaveDifferentRoots() {
    Profile a = Profile.create("26.04", "arm64", Profile.Mode.MINIMAL, 0, false),
        b = Profile.create("26.04", "arm64", Profile.Mode.MINIMAL, 1, false);
    assertNotEquals(a.root(), b.root());
    assertNotEquals(a.sshPort, b.sshPort);
  }
}
