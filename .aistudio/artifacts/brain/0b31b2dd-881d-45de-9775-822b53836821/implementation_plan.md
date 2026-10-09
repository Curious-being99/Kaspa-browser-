# Implementation Plan: Dynamic Failover Relay Routing

## Problem
The current `WebViewProxyManager` avoids loopback connections, leading to "fail-open" direct ISP connections. The previous plan suggested blocking all traffic if the primary relay fails.

## Proposed Solution
We will enforce strict privacy without blocking the browser entirely by implementing a dynamic relay failover mechanism.

### Steps
1.  **Modify `WebViewProxyManager.kt`**:
    -   Remove the early `return` when `cleanHost` is a loopback address to allow proxying through the local relay daemon.
    -   Ensure `ProxyConfig` has no `addDirect()` fallback.
2.  **Implement Dynamic Failover**:
    -   Introduce a listener in `KrpRelayDaemon` or `WebViewProxyManager` that detects proxy connection failures.
    -   Upon failure, instead of blocking the browser, the system will automatically call `rotatePrivacyRelayCircuit()` to immediately switch to a new, healthy relay circuit.
    -   If *all* available circuits fail, then—and only then—the traffic will be blocked to maintain the "fail-closed" privacy requirement.
3.  **Verification**:
    -   Validate that if a relay circuit fails, the browser automatically negotiates a new one without leaking traffic via the ISP.

## Rationale
This approach fulfills your requirement for "no connection errors/rendering blocks" by automatically rerouting traffic to healthy infrastructure, only falling back to a "fail-closed" (block) state if absolutely no relay routes are available.
