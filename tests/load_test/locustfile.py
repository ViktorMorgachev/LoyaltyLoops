import os
import time
import uuid
import hmac
import hashlib
from locust import HttpUser, task, between, events

# Configuration
# Run with: locust -f tests/load_test/locustfile.py --host https://loyaltyloop.up.railway.app
CASHIER_TOKEN = os.getenv("CASHIER_TOKEN", "YOUR_CASHIER_JWT_HERE")
POINT_ID = os.getenv("POINT_ID", "YOUR_TRADING_POINT_ID")
TEST_USER_ID = os.getenv("TEST_USER_ID", "TEST_USER_UUID")
TEST_QR_SECRET = os.getenv("TEST_QR_SECRET", "TEST_USER_QR_SECRET")

class CashierUser(HttpUser):
    wait_time = between(1, 5) # Simulate pause between customers

    def on_start(self):
        self.client.headers.update({"Authorization": f"Bearer {CASHIER_TOKEN}"})

    @task(3)
    def scan_qr(self):
        """Simulates scanning a customer QR code"""
        timestamp = int(time.time())
        # Generate Valid QR Signature
        # Format: loyalty_v1:USER_ID:TIMESTAMP:SIGNATURE
        data = f"{TEST_USER_ID}:{timestamp}"
        signature = hmac.new(
            TEST_QR_SECRET.encode(), 
            data.encode(), 
            hashlib.sha256
        ).hexdigest()
        
        qr_content = f"loyalty_v1:{TEST_USER_ID}:{timestamp}:{signature}"

        with self.client.post(
            "/terminal/scan", 
            json={"qrContent": qr_content, "tradingPointId": POINT_ID},
            catch_response=True
        ) as response:
            if response.status_code == 200:
                self.card_id = response.json().get("cardId")
                response.success()
            else:
                response.failure(f"Scan failed: {response.text}")

    @task(1)
    def process_transaction(self):
        """Simulates processing a purchase"""
        if not hasattr(self, 'card_id'):
            return # Need to scan first

        amount = 100.0 + (time.time() % 100) # Random amount
        
        with self.client.post(
            "/terminal/process",
            json={
                "tradingPointId": POINT_ID,
                "cardId": self.card_id,
                "purchaseAmount": amount,
                "strategy": "TIERED_LTV" # or VISIT
            },
            catch_response=True
        ) as response:
            if response.status_code == 200:
                response.success()
            else:
                response.failure(f"Process failed: {response.text}")

# Helper to run locally without UI
if __name__ == "__main__":
    print("This script is intended to be run with 'locust'")
    print("Example: CASHIER_TOKEN=... POINT_ID=... locust -f locustfile.py")

