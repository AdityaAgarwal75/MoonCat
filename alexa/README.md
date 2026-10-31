# MoonCat Alexa search (development preview)

This is a **named custom skill**, not a replacement for Fire TV global search.
With MoonCat's Search screen open, say **“Alexa, ask Moon Cat to search for Dune”**.
Alexa handles speech recognition; a private relay sends the title to MoonCat.
The Alexa button remains controlled by Fire TV. This has not been tested on a
physical Fire TV Stick, so do not ship an APK until end-to-end testing passes.

## Pieces

1. `interaction-model.json`: Alexa custom skill, English (IN) interaction model.
2. `template.yaml` and `handler.py`: Lambda, DynamoDB table, HTTPS API. Deploy
   the template with AWS SAM to an account you control. Create the skill first
   so `AlexaSkillId` can scope Lambda invocation permission to that skill.
3. Set `ALEXA_RELAY_URL` (the template's `RelayUrl` output) in the MoonCat Android
   build environment or `local.dev.properties`, then rebuild and install the APK.
   A build without this HTTPS URL hides **Link Alexa** and makes no relay calls.

## Developer account setup

Create a **Custom** Alexa skill in the Amazon developer console under the same
Amazon account used by the test Fire TV. Choose English (IN). Import the model,
build it, copy the skill ID, and deploy `template.yaml` with that ID in an
Alexa-supported AWS region. Set the skill's AWS Lambda ARN endpoint to
`SkillEndpointArn` from the stack output and enable development testing. The
Lambda permission is limited to the skill ID. For India, Amazon recommends
`eu-west-1`, though `ap-south-1` is also allowed.

Open MoonCat **Search → Link Alexa** to obtain a code. Hold the Alexa button and
say **“Alexa, ask Moon Cat to pair with 123456”** (use the displayed code). Then
say **“Alexa, ask Moon Cat to search for Dune”**. The app polls only while Search
is open, every 2.5 seconds; one command expires after 90 seconds. Pairing codes
expire after five minutes. Search text is sent through the skill and relay.

## Verification before release

- Confirm the actual Fire TV Stick invokes the development skill from its remote.
- Confirm the phrase and six digit code are recognized correctly in English (IN).
- Confirm Search stays open/resumes after Alexa's overlay and shows the title.
- Confirm network loss, expired pairing, relinking, and a second TV behave safely.
- Run `python3 -m unittest discover -s alexa -p 'test_*.py'` and the Android CI.

The relay is a proof of concept. Before broad distribution, add API throttling,
monitoring, deletion/unlink controls, and a privacy notice for voice query text.
