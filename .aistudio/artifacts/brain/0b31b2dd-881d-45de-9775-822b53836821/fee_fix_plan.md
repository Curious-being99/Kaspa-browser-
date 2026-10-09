# Implementation Plan: Fix Kaspa Fee Estimation (Hardcoded Tiers)

## Problem
The current dynamic fee estimation is too low because it underestimates the required network fees. The user wants accurate estimations without a custom toggle, specifically for "Priority" and "Normal" tiers.

## Proposed Solution
We will remove the custom fee switch logic and hardcode the accurate Kaspa fee tiers requested by the user, ensuring the calculator always returns reliable values.

### Steps
1.  **Modify `KaspaTransactionEngine.kt`**:
    -   Define the new accurate fee constants:
        -   Priority: 0.0069 KAS (based on mass)
        -   Normal: 0.0056 KAS (based on mass)
    -   Update `calculateFeeForMass` or the relevant fee estimation methods in `KaspaTransactionEngine` to use these hardcoded values when planning a transaction.
2.  **Modify `KaspaWalletService.kt`**:
    -   Update the fee estimation UI/logic to display and use these specific tiers instead of attempting dynamic calculation.
3.  **Verification**:
    -   Verify that the fee calculator now consistently shows 0.0069 KAS for "Priority" and 0.0056 KAS for "Normal" without requiring a custom toggle.

## Rationale
By using the user-provided accurate fee values, we eliminate the estimation error entirely and simplify the UI by removing the now-unnecessary custom fee switch.
