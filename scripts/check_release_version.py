"""Refuse duplicate versions, downgrades and tags that differ from the build version."""

import json
import os
from pathlib import Path
import subprocess
import tempfile

from verify_apk import load_identity


def gh(*arguments: str) -> str:
    return subprocess.run(['gh', *arguments], check=True, capture_output=True, text=True).stdout


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    identity = load_identity(root / 'version.properties', root / 'distribution.properties')
    code = identity.version_code
    name = identity.version_name
    tag = f'v{name}'
    repository = identity.update_repository
    if os.environ.get('GITHUB_REF_TYPE') == 'tag' and os.environ['GITHUB_REF_NAME'] != tag:
        raise SystemExit('Tag must match version.properties.')

    if os.environ.get('GITHUB_REF_TYPE') != 'tag':
        existing_tag = subprocess.run(
            ['git', 'show-ref', '--verify', '--quiet', f'refs/tags/{tag}'], cwd=root,
        )
        if existing_tag.returncode == 0:
            raise SystemExit('This tag already exists. Increase the version or publish its exact tagged source.')
        if existing_tag.returncode != 1:
            raise SystemExit('Cannot verify existing Git tags.')

    pages = json.loads(gh('api', f'repos/{repository}/releases', '--paginate', '--slurp'))
    releases = [release for page in pages for release in page]
    for release in releases:
        if release['tag_name'] == tag:
            raise SystemExit('This version already has a release. Increase versionCode and versionName.')
        if release['draft'] or release['prerelease']:
            continue
        if not any(asset['name'] == 'release-manifest.json' for asset in release['assets']):
            raise SystemExit('An existing stable release has no version manifest; refusing an unverified upgrade.')
        with tempfile.TemporaryDirectory() as temp:
            gh('release', 'download', release['tag_name'], '--repo', repository,
               '--pattern', 'release-manifest.json', '--dir', temp)
            previous = json.loads((Path(temp) / 'release-manifest.json').read_text(encoding='utf-8'))
        if code <= previous['versionCode']:
            raise SystemExit('versionCode must exceed every published stable update.')
        if tuple(map(int, name.split('.'))) <= tuple(map(int, previous['versionName'].split('.'))):
            raise SystemExit('versionName must exceed every published stable update.')

    output = os.environ.get('GITHUB_OUTPUT')
    if output:
        with open(output, 'a', encoding='utf-8') as stream:
            stream.write(f'tag={tag}\nversionName={name}\n')
    print(f'Ready to publish {tag} (versionCode {code}).')


if __name__ == '__main__':
    main()
