# MoonCat 2.0.0 Beta 11

- Recover automatically from intermittent addon connection failures and timeouts with one bounded retry.
- Give queued addon requests their full network timeout once a request slot is available.
- Keep successful sources appearing independently while slower sources recover.
- Do not retry empty results, authentication errors, missing endpoints, or rate-limit responses.

Each addon attempt is limited to 15 seconds, with one retry after a 500 ms pause. An unavailable third-party server can still fail after both attempts; Retry starts a fresh search.

Based on Beta 10. Includes regression tests for queue delays, transient and permanent failures, timeout limits, and cancellation. The release workflow runs the full TV unit suite and validates APK identity and signing before publication.
