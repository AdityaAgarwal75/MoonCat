"""Private Alexa-to-MoonCat search relay, deployed as one AWS Lambda.

Configure an Alexa custom skill endpoint and an API Gateway HTTP API to invoke
this Lambda. The skill trigger verifies Alexa request signatures; the HTTP API
is authenticated with a per-device bearer token for reads and registration.
"""

import hashlib
import hmac
import json
import os
import re
import secrets
import time
import uuid

import boto3
from botocore.exceptions import ClientError


TABLE = boto3.resource("dynamodb").Table(os.environ.get("MOONCAT_ALEXA_TABLE", "mooncat-alexa"))
SKILL_ID = os.environ.get("MOONCAT_ALEXA_SKILL_ID", "")
CODE_RE = re.compile(r"^[0-9]{6}$")
DEVICE_RE = re.compile(r"^[0-9a-fA-F-]{36}$")


def _reply(status, payload):
    return {
        "statusCode": status,
        "headers": {"Content-Type": "application/json", "Cache-Control": "no-store"},
        "body": json.dumps(payload),
    }


def _speech(text, end=True):
    return {
        "version": "1.0",
        "response": {
            "outputSpeech": {"type": "PlainText", "text": text},
            "shouldEndSession": end,
        },
    }


def _slot(intent, name):
    return (intent.get("slots", {}).get(name) or {}).get("value", "").strip()


def _skill(event):
    app_id = event.get("session", {}).get("application", {}).get("applicationId") or event.get("context", {}).get("System", {}).get("application", {}).get("applicationId")
    if not SKILL_ID or app_id != SKILL_ID:
        return _speech("This skill is not configured.")

    user_id = event.get("session", {}).get("user", {}).get("userId") or event.get("context", {}).get("System", {}).get("user", {}).get("userId")
    request = event.get("request", {})
    if request.get("type") == "LaunchRequest":
        return _speech("Open MoonCat search on your TV and choose Link Alexa. Then say ask MoonCat to pair, followed by the six digit code.", False)
    if request.get("type") != "IntentRequest" or not user_id:
        return _speech("Please try again.")

    intent = request.get("intent", {})
    name = intent.get("name")
    if name == "PairIntent":
        code = re.sub(r"\s+", "", _slot(intent, "code"))
        if not CODE_RE.fullmatch(code):
            return _speech("Please say the six digit code shown in MoonCat.")
        pair_key = {"pk": "PAIR#" + code}
        record = TABLE.get_item(Key=pair_key, ConsistentRead=True).get("Item")
        if not record or record.get("expiresAt", 0) <= int(time.time()):
            return _speech("That code has expired. Get a new code in MoonCat.")
        try:
            TABLE.delete_item(Key=pair_key, ConditionExpression="attribute_exists(pk)")
        except ClientError as error:
            if error.response.get("Error", {}).get("Code") == "ConditionalCheckFailedException":
                return _speech("That code has already been used. Get a new code in MoonCat.")
            raise
        TABLE.put_item(Item={"pk": "LINK#" + user_id, "deviceId": record["deviceId"]})
        return _speech("MoonCat is linked. Say ask MoonCat to search for, followed by a title.")

    if name == "SearchIntent":
        query = _slot(intent, "query")[:120]
        if not query:
            return _speech("What would you like to search for?")
        link = TABLE.get_item(Key={"pk": "LINK#" + user_id}).get("Item")
        if not link:
            return _speech("First link MoonCat. Open MoonCat search and choose Link Alexa.")
        TABLE.put_item(Item={
            "pk": "COMMAND#" + link["deviceId"],
            "query": query,
            "commandId": str(uuid.uuid4()),
            "expiresAt": int(time.time()) + 90,
        })
        return _speech("Searching MoonCat for " + query + ".")

    return _speech("Say ask MoonCat to search for, followed by a title.")


def _json_body(event):
    try:
        return json.loads(event.get("body") or "{}")
    except (ValueError, TypeError):
        return {}


def _http(event):
    method = event.get("requestContext", {}).get("http", {}).get("method")
    path = event.get("rawPath", "")
    body = _json_body(event)
    headers = {key.lower(): value for key, value in event.get("headers", {}).items()}
    if method == "POST" and path.endswith("/register"):
        device_id = body.get("deviceId", "")
        token = body.get("token", "")
        if not DEVICE_RE.fullmatch(device_id) or len(token) < 40:
            return _reply(400, {"error": "invalid device"})
        # AMAZON.NUMBER can drop a leading zero from a spoken pairing code.
        code = str(secrets.randbelow(9) + 1) + "".join(str(secrets.randbelow(10)) for _ in range(5))
        expires = int(time.time()) + 300
        token_hash = hashlib.sha256(token.encode()).hexdigest()
        # A public registration endpoint must never let another caller rotate
        # the token of an existing device.
        try:
            TABLE.put_item(
                Item={"pk": "DEVICE#" + device_id, "tokenHash": token_hash},
                ConditionExpression="attribute_not_exists(pk)",
            )
        except ClientError as error:
            if error.response.get("Error", {}).get("Code") != "ConditionalCheckFailedException":
                raise
            existing = TABLE.get_item(Key={"pk": "DEVICE#" + device_id}).get("Item")
            if not existing or not hmac.compare_digest(existing["tokenHash"], token_hash):
                return _reply(401, {"error": "unauthorized"})
        try:
            TABLE.put_item(Item={"pk": "PAIR#" + code, "deviceId": device_id, "expiresAt": expires}, ConditionExpression="attribute_not_exists(pk)")
        except ClientError as error:
            if error.response.get("Error", {}).get("Code") == "ConditionalCheckFailedException":
                return _reply(409, {"error": "code collision; retry"})
            raise
        return _reply(200, {"code": code, "expiresAt": expires})

    if method == "GET" and path.endswith("/command"):
        device_id = (event.get("queryStringParameters") or {}).get("deviceId", "")
        bearer = headers.get("authorization", "")
        if not DEVICE_RE.fullmatch(device_id) or not bearer.startswith("Bearer "):
            return _reply(401, {"error": "unauthorized"})
        device = TABLE.get_item(Key={"pk": "DEVICE#" + device_id}).get("Item")
        supplied = hashlib.sha256(bearer[7:].encode()).hexdigest()
        if not device or not hmac.compare_digest(supplied, device["tokenHash"]):
            return _reply(401, {"error": "unauthorized"})
        key = {"pk": "COMMAND#" + device_id}
        # Returning a command once only; the TV sends a new request to get the next.
        command = TABLE.get_item(Key=key, ConsistentRead=True).get("Item")
        if not command or command.get("expiresAt", 0) <= int(time.time()):
            return _reply(200, {})
        try:
            TABLE.delete_item(Key=key, ConditionExpression="commandId = :id", ExpressionAttributeValues={":id": command["commandId"]})
        except ClientError as error:
            if error.response.get("Error", {}).get("Code") == "ConditionalCheckFailedException":
                return _reply(200, {})
            raise
        return _reply(200, {"query": command["query"]})
    return _reply(404, {"error": "not found"})


def handler(event, context):
    if "requestContext" in event and "http" in event["requestContext"]:
        return _http(event)
    return _skill(event)
