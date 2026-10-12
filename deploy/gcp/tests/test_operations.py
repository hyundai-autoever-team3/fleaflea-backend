import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch


spec = importlib.util.spec_from_file_location('operations', Path(__file__).resolve().parents[1] / 'operations.py')
operations = importlib.util.module_from_spec(spec)
spec.loader.exec_module(operations)


class ImageCleanupTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.commands = []
        self.images = [self.image('newest', '2026-10-12T00:00:00Z'),
                       self.image('second', '2026-10-11T00:00:00Z'),
                       self.image('third', '2026-10-10T00:00:00Z'),
                       self.image('rollback', '2026-01-01T00:00:00Z'),
                       self.image('unused', '2026-01-01T00:00:00Z')]

    def image(self, identifier, created):
        return {'Id': identifier, 'Created': created,
                'RepoTags': [operations.IMAGE_REPOSITORY + ':' + identifier], 'RepoDigests': []}

    def command(self, arguments, **options):
        self.commands.append(arguments)
        if arguments == ['docker', 'ps', '-aq']:
            return 'stopped-container'
        if arguments[:2] == ['docker', 'inspect']:
            return json.dumps([{'Image': 'rollback'}])
        if arguments[:3] == ['docker', 'image', 'ls']:
            return '\n'.join(image['Id'] for image in self.images)
        if arguments[:3] == ['docker', 'image', 'inspect']:
            if arguments[3:] == ['slot-image']:
                return json.dumps([{'Id': 'unused'}])
            return json.dumps(self.images)
        return ''

    def cleanup(self, dry_run=False, slot=False):
        if slot:
            (self.root / 'blue.env').write_text('APP_IMAGE=slot-image\n')
        real_open = open
        with patch.object(operations, 'command', side_effect=self.command), \
             patch.object(operations, 'Path', side_effect=lambda path: self.root / Path(path).name), \
             patch('builtins.open', side_effect=lambda path, *args, **kwargs: real_open(
                 self.root / 'deploy.lock' if path == '/run/lock/fleaflea-deploy.lock' else path, *args, **kwargs)), \
             patch.object(operations.subprocess, 'run') as run, \
             patch.object(operations, 'mark_success') as success, \
             patch.object(operations, 'event'):
            run.return_value.returncode = 0
            operations.cleanup(dry_run)
            return success.call_count

    def deletions(self):
        return [command[3] for command in self.commands if command[:3] == ['docker', 'image', 'rm']]

    def test_keeps_stopped_container_and_three_newest_images(self):
        self.assertEqual(1, self.cleanup())
        self.assertEqual([operations.IMAGE_REPOSITORY + ':unused'], self.deletions())
        self.assertTrue(all('prune' not in command and '--force' not in command for command in self.commands))

    def test_keeps_inactive_slot_image(self):
        self.cleanup(slot=True)
        self.assertEqual([], self.deletions())

    def test_dry_run_does_not_remove_or_mark_success(self):
        self.assertEqual(0, self.cleanup(dry_run=True))
        self.assertEqual([], self.deletions())


if __name__ == '__main__':
    unittest.main()
