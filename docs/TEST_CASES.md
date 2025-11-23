ё# Test Cases

## Feature: Employee Statistics (Pending)

**Description:**
Allow Partner Admin to view performance statistics for each employee (Cashier/Manager).

**Preconditions:**
1. Partner has active trading points and cashiers.
2. Transactions have been processed by these cashiers.
3. User is logged in as `PARTNER_ADMIN`.

**Steps:**
1. Open Web Admin -> "Staff" page.
2. Switch to "Cashiers" tab.
3. Observe columns "Total Revenue" and "Transactions Count" for each cashier.

**Expected Result:**
1. The list displays correct aggregated values based on `TransactionsHistory`.
2. Revenue = Sum of `amount` for transactions where `type = 'EARN'` (money paid).
3. Transactions = Count of transactions processed by this `cashierId`.

**API Changes (Planned):**
- `GET /partners/cashiers` response will include `revenue` and `transactionsCount`.

