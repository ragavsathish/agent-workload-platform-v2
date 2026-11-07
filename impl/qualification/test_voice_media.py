"""Named deployed acceptance gate for slice 05. Requires explicit live endpoints."""

import os
import unittest


class VoiceMediaAcceptance(unittest.TestCase):
    def setUp(self):
        from voice_media import Qualification

        self.qualification = Qualification.from_environment(os.environ)

    def test_voice_001_spoken_round_trip(self):
        self.qualification.voice_001()

    def test_voice_002_restart_read(self):
        self.qualification.voice_002()

    def test_voice_003_disconnect(self):
        self.qualification.voice_003()

    def test_voice_004_task_route(self):
        self.qualification.voice_004()

    def test_voice_005_missing_speech(self):
        self.qualification.voice_005()

    def test_voice_006_public_authority(self):
        self.qualification.voice_006()


if __name__ == "__main__":
    unittest.main()
