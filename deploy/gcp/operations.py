#!/usr/bin/env python3
"""Root-only maintenance for the FleaFlea production VM."""

import argparse
import datetime
import fcntl
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile
import time
import urllib.request
import uuid

STATE_DIRECTORY = Path('/var/lib/fleaflea-operations')
IMAGE_REPOSITORY = 'asia-northeast3-docker.pkg.dev/fleaflea-511001/fleaflea/backend'


def command(arguments, **options):
    return subprocess.run(arguments, check=True, text=True, capture_output=True, **options).stdout.strip()


def event(name, **fields):
    print(json.dumps({'event': name, **fields}), flush=True)


def metadata(path):
    request = urllib.request.Request(
        'http://metadata.google.internal/computeMetadata/v1/' + path,
        headers={'Metadata-Flavor': 'Google'})
    with urllib.request.urlopen(request, timeout=10) as response:
        return response.read().decode()


def write_metrics(values):
    project = metadata('project/project-id')
    token = json.loads(metadata('instance/service-accounts/default/token'))['access_token']
    resource = {'type': 'gce_instance', 'labels': {
        'project_id': project, 'instance_id': metadata('instance/id'),
        'zone': metadata('instance/zone').split('/')[-1]}}
    timestamp = datetime.datetime.now(datetime.timezone.utc).isoformat()
    series = [{'metric': {'type': 'custom.googleapis.com/fleaflea/' + name},
               'resource': resource, 'points': [{'interval': {'endTime': timestamp},
                                                'value': {'doubleValue': float(value)}}]}
              for name, value in values.items()]
    request = urllib.request.Request(
        f'https://monitoring.googleapis.com/v3/projects/{project}/timeSeries',
        data=json.dumps({'timeSeries': series}).encode(), method='POST',
        headers={'Authorization': 'Bearer ' + token, 'Content-Type': 'application/json'})
    with urllib.request.urlopen(request, timeout=30) as response:
        response.read()


def mark_success(name):
    STATE_DIRECTORY.mkdir(mode=0o700, parents=True, exist_ok=True)
    temporary = STATE_DIRECTORY / (name + '.tmp')
    temporary.write_text(str(time.time()))
    temporary.replace(STATE_DIRECTORY / name)


def postgres(arguments, **options):
    return command(['docker', 'exec', '-i', 'fleaflea-postgres-1', *arguments], **options)


def table_counts(database):
    return postgres(['psql', '-X', '-qAt', '-v', 'ON_ERROR_STOP=1', '-U', 'fleaflea', '-d', database,
                     '-c', "SELECT json_object_agg(table_name, row_count) FROM ("
                     "SELECT 'members' AS table_name, count(*) AS row_count FROM members UNION ALL "
                     "SELECT 'markets', count(*) FROM markets UNION ALL "
                     "SELECT 'collection_items', count(*) FROM collection_items UNION ALL "
                     "SELECT 'items', count(*) FROM items UNION ALL "
                     "SELECT 'chat_messages', count(*) FROM chat_messages) counts"])


def backup(verify_restore):
    from google.cloud import storage

    bucket_name = os.environ['BACKUP_BUCKET']
    timestamp = datetime.datetime.now(datetime.timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    with tempfile.TemporaryDirectory(prefix='fleaflea-backup-') as directory:
        dump_path = Path(directory) / 'database.dump'
        with dump_path.open('wb') as output:
            subprocess.run(['docker', 'exec', 'fleaflea-postgres-1', 'pg_dump', '-U', 'fleaflea',
                            '-d', 'fleaflea_db', '-Fc'], stdout=output, check=True)
        if dump_path.stat().st_size == 0:
            raise RuntimeError('Empty database backup')
        with dump_path.open('rb') as source:
            digest = hashlib.file_digest(source, 'sha256').hexdigest()
        blob = storage.Client().bucket(bucket_name).blob('postgres/' + timestamp + '-' + uuid.uuid4().hex[:8] + '.dump')
        blob.metadata = {'sha256': digest}
        blob.upload_from_filename(str(dump_path), if_generation_match=0, timeout=300)
        # Read the off-VM copy back, including the SDK checksum validation.
        downloaded = Path(directory) / 'downloaded.dump'
        blob.download_to_filename(str(downloaded), timeout=300)
        with downloaded.open('rb') as source:
            downloaded_digest = hashlib.file_digest(source, 'sha256').hexdigest()
        if downloaded_digest != digest:
            raise RuntimeError('Downloaded backup checksum mismatch')
        if verify_restore:
            verify_database = 'fleaflea_restore_' + uuid.uuid4().hex[:12]
            postgres(['createdb', '-U', 'fleaflea', '-O', 'fleaflea', verify_database])
            try:
                with downloaded.open('rb') as source:
                    subprocess.run(['docker', 'exec', '-i', 'fleaflea-postgres-1', 'pg_restore',
                                    '-U', 'fleaflea', '-d', verify_database, '--no-owner', '--no-acl',
                                    '--exit-on-error'], stdin=source, check=True)
                # A real restore must contain usable application tables. Production
                # counts can legitimately change while pg_dump runs, so don't compare
                # them with a later live SELECT.
                restored_counts = json.loads(table_counts(verify_database))
                event('restore_verified', tables=restored_counts)
            finally:
                postgres(['dropdb', '-U', 'fleaflea', '--force', verify_database])
            mark_success('restore-success')
        mark_success('backup-success')
        event('backup_completed', object=blob.name, bytes=dump_path.stat().st_size, sha256=digest)


def cleanup(dry_run):
    # Deployment and cleanup share a lock; never remove an image being deployed.
    with open('/run/lock/fleaflea-deploy.lock', 'a') as deployment_lock:
        fcntl.flock(deployment_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        containers = command(['docker', 'ps', '-aq']).splitlines()
        protected = set()
        if containers:
            protected.update(container['Image'] for container in json.loads(command(['docker', 'inspect', *containers])))
        for slot in ('blue', 'green'):
            environment = Path('/etc/fleaflea/' + slot + '.env')
            if environment.exists():
                image = next(line.split('=', 1)[1] for line in environment.read_text().splitlines()
                             if line.startswith('APP_IMAGE='))
                protected.add(json.loads(command(['docker', 'image', 'inspect', image]))[0]['Id'])
        image_ids = command(['docker', 'image', 'ls', '-aq', '--no-trunc']).splitlines()
        images = json.loads(command(['docker', 'image', 'inspect', *sorted(set(image_ids))])) if image_ids else []
        candidates = []
        for image in images:
            references = (image.get('RepoTags') or []) + (image.get('RepoDigests') or [])
            if any(reference.startswith(IMAGE_REPOSITORY + ':') or reference.startswith(IMAGE_REPOSITORY + '@')
                   or reference.startswith('fleaflea-migration:') for reference in references):
                candidates.append(image)
        # Also retain the newest three project images even if a deploy was interrupted.
        candidates.sort(key=lambda image: image['Created'], reverse=True)
        protected.update(image['Id'] for image in candidates[:3])
        removed = []
        project_ids = {image['Id'] for image in candidates}
        cutoff = datetime.datetime.now(datetime.timezone.utc) - datetime.timedelta(days=7)
        for image in images:
            if image['Id'] in protected:
                continue
            if image['Id'] not in project_ids:
                created = datetime.datetime.fromisoformat(image['Created'].replace('Z', '+00:00'))
                if created >= cutoff:
                    continue
            removed.append(image['Id'])
            if not dry_run:
                # Never force deletion: Docker additionally protects container references.
                references = (image.get('RepoTags') or []) + (image.get('RepoDigests') or [])
                for reference in references or [image['Id']]:
                    exists = subprocess.run(['docker', 'image', 'inspect', reference],
                                            capture_output=True).returncode == 0
                    if exists:
                        command(['docker', 'image', 'rm', reference])
        if not dry_run:
            mark_success('cleanup-success')
        event('image_cleanup', dry_run=dry_run, images=removed, protected=len(protected))


def age(marker):
    path = STATE_DIRECTORY / marker
    return max(0, time.time() - float(path.read_text())) if path.exists() else 365 * 86400


def check():
    containers = json.loads(command(['docker', 'inspect', 'fleaflea-postgres-1', 'fleaflea-redis-1',
                                     'fleaflea-' + Path('/etc/fleaflea/active-slot').read_text().strip() + '-app-1']))
    unhealthy = sum(not container['State']['Running'] or
                    container['State'].get('Health', {}).get('Status') != 'healthy' for container in containers)
    values = {'backup_age_seconds': age('backup-success'), 'restore_age_seconds': age('restore-success'),
              'cleanup_age_seconds': age('cleanup-success'), 'unhealthy_containers': unhealthy}
    write_metrics(values)
    event('operations_check', **values)


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser()
    parser.add_argument('operation', choices=['backup', 'cleanup', 'check'])
    parser.add_argument('--verify-restore', action='store_true')
    parser.add_argument('--dry-run', action='store_true')
    arguments = parser.parse_args()
    try:
        if arguments.operation == 'backup':
            STATE_DIRECTORY.mkdir(mode=0o700, parents=True, exist_ok=True)
            with (STATE_DIRECTORY / 'backup.lock').open('a') as backup_lock:
                fcntl.flock(backup_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
                backup(arguments.verify_restore)
        elif arguments.operation == 'cleanup':
            cleanup(arguments.dry_run)
        else:
            check()
    except Exception as exception:
        event('operations_failed', severity='ERROR', operation=arguments.operation, error=str(exception))
        raise


if __name__ == '__main__':
    main()
