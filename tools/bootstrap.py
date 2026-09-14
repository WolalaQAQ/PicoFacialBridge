"""Install a project-specific Android toolchain from official, checksum-verified archives."""
import concurrent.futures
import hashlib
import json
import pathlib
import subprocess
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[1]
configuration = ROOT / 'tools/downloads.json'
if not configuration.exists():
    metadata = json.load(urllib.request.urlopen('https://api.adoptium.net/v3/assets/latest/17/hotspot?architecture=x64&image_type=jdk&os=windows&vendor=eclipse', timeout=60))[0]['binary']['package']
    configuration.write_text(json.dumps(dict(jdkUrl=metadata['link'], jdkSha256=metadata['checksum'], cache=str(pathlib.Path.home() / '.cache/pico-android')), indent=2), encoding='utf-8')
CONFIG = json.loads(configuration.read_text(encoding='utf-8-sig'))
CACHE = pathlib.Path(CONFIG['cache'])
CACHE.mkdir(parents=True, exist_ok=True)
SDK = CACHE / 'sdk'
NS = '{http://schemas.android.com/repository/android/generic/01}'
if not (CACHE / 'repository.xml').exists():
    subprocess.run(['curl.exe', '-L', '--fail', '--silent', '--show-error', 'https://dl.google.com/android/repository/repository2-1.xml', '-o', str(CACHE / 'repository.xml')], check=True)
tree = ET.parse(CACHE / 'repository.xml')

def download(url, path, checksum, algorithm):
    if not path.exists() or hashlib.file_digest(path.open('rb'), algorithm).hexdigest() != checksum:
        print(f'Downloading/resuming {path.name}', flush=True)
        subprocess.run(['curl.exe', '-L', '--fail', '--retry', '3', '--connect-timeout', '20', '--max-time', '1800', '--continue-at', '-', '--silent', '--show-error', url, '-o', str(path)], check=True)
    with path.open('rb') as file:
        actual = hashlib.file_digest(file, algorithm).hexdigest()
    if actual.lower() != checksum.lower():
        raise RuntimeError(f'Checksum mismatch: {path}')
    print(f'Verified {path.name}', flush=True)

def install_android(package, destination):
    matches = [p for p in tree.getroot().findall('remotePackage') if p.attrib['path'] == package]
    # Prefer a stable, non-extension platform package for reproducible compilation.
    candidates = [a for p in matches for a in p.findall('./archives/archive')
                  if a.findtext('host-os') in (None, 'windows')]
    archive = next((a for a in candidates if a.findtext('complete/url') == 'platform-35_r02.zip'), candidates[0])
    complete = archive.find('complete')
    url = complete.findtext('url')
    checksum = complete.find('checksum')
    path = CACHE / url
    download('https://dl.google.com/android/repository/' + url, path, checksum.text, checksum.attrib.get('type', 'sha1'))
    if destination.exists():
        print(f'Already installed: {destination}', flush=True)
        return
    temporary = CACHE / ('extract-' + package.replace(';', '-'))
    temporary.mkdir(exist_ok=True)
    with zipfile.ZipFile(path) as archive_zip:
        archive_zip.extractall(temporary)
    entries = list(temporary.iterdir())
    if (temporary / 'source.properties').exists():
        destination.parent.mkdir(parents=True, exist_ok=True)
        temporary.rename(destination)
        print(f'Installed {package}', flush=True)
        return
    if len(entries) != 1 or not entries[0].is_dir():
        raise RuntimeError(f'Unexpected archive root: {path}')
    destination.parent.mkdir(parents=True, exist_ok=True)
    entries[0].rename(destination)
    temporary.rmdir()
    print(f'Installed {package}', flush=True)

def install_jdk():
    path = CACHE / 'jdk17.zip'
    download(CONFIG['jdkUrl'], path, CONFIG['jdkSha256'], 'sha256')
    with zipfile.ZipFile(path) as archive:
        root = archive.namelist()[0].split('/')[0]
        if not (CACHE / root / 'bin/java.exe').exists():
            archive.extractall(CACHE)
    (ROOT / 'tools/jdk-path.txt').write_text(str(CACHE / root), encoding='utf-8')
    print('JDK installed', flush=True)

def install_gradle():
    url = 'https://services.gradle.org/distributions/gradle-8.9-bin.zip'
    checksum = urllib.request.urlopen(url + '.sha256', timeout=60).read().decode().strip()
    path = CACHE / 'gradle-8.9-bin.zip'
    download(url, path, checksum, 'sha256')
    if not (CACHE / 'gradle-8.9/bin/gradle.bat').exists():
        with zipfile.ZipFile(path) as archive:
            archive.extractall(CACHE)
    print('Gradle installed', flush=True)

if __name__ == '__main__':
    jobs = [install_jdk, install_gradle]
    for package, destination in [('cmdline-tools;12.0', SDK / 'cmdline-tools/12.0'),
                                  ('platforms;android-35', SDK / 'platforms/android-35'),
                                  ('build-tools;35.0.0', SDK / 'build-tools/35.0.0'),
                                  ('ndk;26.1.10909125', SDK / 'ndk/26.1.10909125'),
                                  ('cmake;3.22.1', SDK / 'cmake/3.22.1')]:
        jobs.append(lambda package=package, destination=destination: install_android(package, destination))
    with concurrent.futures.ThreadPoolExecutor(max_workers=5) as executor:
        for future in concurrent.futures.as_completed([executor.submit(job) for job in jobs]):
            future.result()
