import asyncio
import unittest

from voice_media import Qualification, SyntheticMicrophone, assert_result


class MediaDriverUnitTest(unittest.TestCase):
    def test_qualification_requires_v2_grain_binding(self):
        environment = {
            "VOICE_EDGE_URL": "http://127.0.0.1:7860",
            "VOICE_ORIGIN": "http://127.0.0.1:7860",
            "VOICE_OWNER_IDENTITY": "owner@example.test",
            "VOICE_UTTERANCE": "hello",
            "VOICE_AUDIO_WAV": "/tmp/utterance.wav",
        }
        with self.assertRaisesRegex(RuntimeError, "VOICE_GRAIN_URL"):
            Qualification.from_environment(environment)

    def test_synthetic_microphone_emits_timed_pcm(self):
        async def read():
            microphone = SyntheticMicrophone()
            microphone.play(b"\x01\x02" * 320)
            frame = await microphone.recv()
            return frame, microphone

        frame, microphone = asyncio.run(read())
        self.assertEqual(frame.sample_rate, 16000)
        self.assertEqual(frame.samples, 320)
        self.assertEqual(frame.to_ndarray().tobytes(), b"\x01\x02" * 320)
        self.assertTrue(microphone._drained.is_set())

    def test_audio_is_required_for_spoken_success(self):
        with self.assertRaisesRegex(AssertionError, "Voice response"):
            assert_result({"response_completed": True, "output_bytes": 0})


if __name__ == "__main__":
    unittest.main()
