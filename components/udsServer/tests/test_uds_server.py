import json
import socket
import os
import threading
import time
import unittest
from pathlib import Path

SOCKET_PATH = "/tmp/unix_socket_example"
TESTING_FUNC = "test_func"


def start_server():
    from uds_server import main  # Import the actual server function
    main(SOCKET_PATH, TESTING_FUNC, {})


class TestUDSServer(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.server_thread = threading.Thread(target=start_server, daemon=True)
        cls.server_thread.start()
        time.sleep(1)  # Allow server to start

    def test_client_messages(self):
        """
        Test the UDS Server by sending it 5 consecutive messages and wait
        for the echoed response.
        """
        client = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        client.connect(SOCKET_PATH)

        for msg in range(1, 6):
            msg_json = json.dumps({
                "module_name": TESTING_FUNC,
                "parameters": {
                    "sensors": "",
                    "actuators": "",
                    "msg": msg}
            })
            client.sendall(msg_json.encode("utf-8"))
            response = client.recv(1024)
            print(response)
            json_result = json.loads(response)
            self.assertEqual(json_result["result"], f"Test: {msg}")

        client.close()

    @classmethod
    def tearDownClass(cls):
        if Path(SOCKET_PATH).exists():
            os.remove(SOCKET_PATH)


if __name__ == "__main__":
    unittest.main()
