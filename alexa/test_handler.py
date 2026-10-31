import importlib.util
import json
import os
import pathlib
import sys
import types
import unittest


class FakeClientError(Exception):
    def __init__(self):
        self.response = {"Error": {"Code": "ConditionalCheckFailedException"}}


class FakeTable:
    def __init__(self):
        self.items = {}

    def get_item(self, Key, **_):
        return {"Item": self.items[Key["pk"]]} if Key["pk"] in self.items else {}

    def put_item(self, Item, ConditionExpression=None, **_):
        if ConditionExpression and Item["pk"] in self.items:
            raise FakeClientError()
        self.items[Item["pk"]] = Item

    def delete_item(self, Key, ConditionExpression=None, ExpressionAttributeValues=None, **_):
        previous = self.items.get(Key["pk"])
        if ConditionExpression and (
            previous is None or
            (ExpressionAttributeValues and previous.get("commandId") != ExpressionAttributeValues[":id"])
        ):
            raise FakeClientError()
        self.items.pop(Key["pk"], None)


table = FakeTable()
fake_boto3 = types.ModuleType("boto3")
fake_boto3.resource = lambda _: types.SimpleNamespace(Table=lambda _: table)
sys.modules["boto3"] = fake_boto3
sys.modules["botocore"] = types.ModuleType("botocore")
fake_exceptions = types.ModuleType("botocore.exceptions")
fake_exceptions.ClientError = FakeClientError
sys.modules["botocore.exceptions"] = fake_exceptions
os.environ["MOONCAT_ALEXA_SKILL_ID"] = "test-skill"
spec = importlib.util.spec_from_file_location("mooncat_voice_handler", pathlib.Path(__file__).with_name("handler.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class RelayTests(unittest.TestCase):
    def setUp(self):
        table.items.clear()
        self.device = "1a45678b-1234-4bca-9876-abcdefabcdef"
        self.token = "secret" * 12

    def http(self, method, path, body=None, token=None):
        event = {
            "requestContext": {"http": {"method": method}}, "rawPath": path,
            "queryStringParameters": {"deviceId": self.device},
            "headers": {"authorization": "Bearer " + (token or "")},
            "body": json.dumps(body or {}),
        }
        response = module.handler(event, None)
        return response["statusCode"], json.loads(response["body"])

    def skill(self, intent, slots):
        event = {
            "session": {"application": {"applicationId": "test-skill"}, "user": {"userId": "alexa-user"}},
            "request": {"type": "IntentRequest", "intent": {
                "name": intent, "slots": {key: {"value": value} for key, value in slots.items()}
            }},
        }
        return module.handler(event, None)["response"]["outputSpeech"]["text"]

    def test_pair_and_search_delivers_once(self):
        status, result = self.http("POST", "/register", {"deviceId": self.device, "token": self.token})
        self.assertEqual(status, 200)
        self.assertIn("linked", self.skill("PairIntent", {"code": result["code"]}))
        self.assertIn("Searching", self.skill("SearchIntent", {"query": "Dune"}))
        self.assertEqual(self.http("GET", "/command", token=self.token), (200, {"query": "Dune"}))
        self.assertEqual(self.http("GET", "/command", token=self.token), (200, {}))

    def test_cannot_rotate_an_existing_device_token(self):
        self.http("POST", "/register", {"deviceId": self.device, "token": self.token})
        status, _ = self.http("POST", "/register", {"deviceId": self.device, "token": "attacker" * 8})
        self.assertEqual(status, 401)
        self.assertEqual(self.http("GET", "/command", token="attacker" * 8)[0], 401)

    def test_unlinked_user_cannot_send_command(self):
        self.assertIn("link MoonCat", self.skill("SearchIntent", {"query": "Dune"}))


if __name__ == "__main__":
    unittest.main()
