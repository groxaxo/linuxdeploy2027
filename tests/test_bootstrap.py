import os
from pathlib import Path
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]
ENV = REPO / 'app/src/main/assets/env'

class UbuntuProfilesTest(unittest.TestCase):
    def test_custom_profiles_inherit_installer_and_pin_their_suite(self):
        for suite in ['jammy', 'noble']:
            for flavor in ['server', 'desktop']:
                with self.subTest(suite=suite, flavor=flavor), tempfile.TemporaryDirectory() as work:
                    env = dict(os.environ, ENV_DIR=work, INCLUDE_DIR=str(ENV / 'include'), CLI=str(ENV / 'cli.sh'),
                               EXPECTED_SUITE=suite, COMPONENT_NAME=f'bootstrap/ubuntu-{suite}-{flavor}')
                    result = subprocess.run(['bash', '-c', '''
. "$CLI" -d help >/dev/null
WITHOUT_DEPENDS=true
SUITE= ARCH=amd64
DO_ACTION='test "$SUITE" = "$EXPECTED_SUITE" && declare -f do_install | grep -q debootstrap'
component_exec "$COMPONENT_NAME"
'''], env=env, text=True, capture_output=True)
                    self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_noble_extracts_existing_merged_usr_symlinks(self):
        import tarfile
        with tempfile.TemporaryDirectory() as work:
            root = Path(work)
            fixture = root / 'base-files.tar'
            with tarfile.open(fixture, 'w') as archive:
                link = tarfile.TarInfo('./bin')
                link.type = tarfile.SYMTYPE
                link.linkname = 'usr/bin'
                archive.addfile(link)
            env = dict(os.environ, TARGET=str(root / 'target'), FIXTURE=str(fixture),
                       SUITE_SCRIPT=str(REPO / 'custom_env/include/bootstrap/debian/debootstrap/scripts/noble'))
            result = subprocess.run(['bash', '-ec', r'''
for name in default_mirror mirror_style download_style finddebs_style variants keyring setup_etc setup_devices; do eval "$name() { :; }"; done
doing_variant() { return 1; }
setup_merged_usr() { mkdir -p "$TARGET/usr/bin" "$TARGET/etc"; ln -s usr/bin "$TARGET/bin"; }
extract() { (cd "$TARGET"; tar $EXTRACT_DEB_TAR_OPTIONS -xf "$FIXTURE"); }
ARCH=amd64 CODENAME=noble
. "$SUITE_SCRIPT"
first_stage_install
test -L "$TARGET/bin"
'''], env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

if __name__ == '__main__':
    unittest.main()
