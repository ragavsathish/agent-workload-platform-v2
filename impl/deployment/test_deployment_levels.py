import unittest

from stack import execution_mode_for_level, native_specs


class DeploymentLevelsTest(unittest.TestCase):
    def env(self, level):
        return {
            "AWP_DEPLOYMENT_LEVEL": level,
            "AWP_TASKING_STORAGE_DIR": "/tmp/tasks",
            "AWP_SESSION_STORAGE_DIR": "/tmp/sessions",
            "AWP_GRAIN_SESSION_TOKEN": "secret",
            "AWP_OWNER_IDENTITY": "owner@example.test",
            "AWP_ELIGIBLE_WORKSPACES": "scratch",
            "AWP_WORKSPACE_MAP_JSON": "{}",
            "INFERENCE_BROKER_TOKEN": "secret",
            "GONDOLIN_BROWSER_ASSETS": "/tmp/assets",
            "AWP_DEPLOY_ENV": "/tmp/service.env",
            "AWP_DEPLOY_STATE": "/tmp/state",
        }

    def test_voice_level_has_no_task_scheduler(self):
        names = {spec.name for spec in native_specs(self.env("voice"))}
        self.assertNotIn("scheduler", names)
        self.assertIn("pi", names)
        self.assertIn("broker", names)

    def test_deployment_001_default_is_full(self):
        env = self.env("full")
        del env["AWP_DEPLOYMENT_LEVEL"]
        self.assertIn("scheduler", {spec.name for spec in native_specs(env)})

    def test_deployment_003_durable_and_full_choose_executor(self):
        self.assertIn("scheduler", {spec.name for spec in native_specs(self.env("durable"))})
        self.assertEqual("host", execution_mode_for_level("durable"))

    def test_deployment_004_full_uses_isolated_executor(self):
        self.assertEqual("gondolin", execution_mode_for_level("full"))

    def test_deployment_008_voice_keeps_pi_isolated_capability(self):
        self.assertIn("pi", {spec.name for spec in native_specs(self.env("voice"))})

    def test_deployment_002_voice_cannot_select_task_executor(self):
        with self.assertRaises(ValueError):
            execution_mode_for_level("voice")

    def test_deployment_009_unknown_level_is_refused_before_service_selection(self):
        with self.assertRaises(ValueError):
            native_specs(self.env("other"))

    def test_deployment_010_repeated_level_has_same_capability_set(self):
        self.assertEqual({s.name for s in native_specs(self.env("durable"))},
                         {s.name for s in native_specs(self.env("durable"))})


if __name__ == "__main__":
    unittest.main()
