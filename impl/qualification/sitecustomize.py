"""Keep isolated same-host media probes on loopback only."""

import os

if os.getenv("AWP_WEBRTC_LOOPBACK_ONLY") == "1":
    import aioice.ice

    aioice.ice.get_host_addresses = lambda use_ipv4, use_ipv6: (
        ["127.0.0.1"] if use_ipv4 else []
    )
