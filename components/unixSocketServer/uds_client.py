"""
Module to test the UDS Server by sending it 5 consecutive messages and waiting
for the response
"""
import socket
import json

SOCKET_PATH = "/tmp/unix_socket_example.sock"
TESTING_FUNC = "test_func"

client = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
client.connect(SOCKET_PATH)

for msg in range(1, 6):
    msg_json = json.dumps({
        "funcParams": [msg]  # Listener function expects an iterable
    })
    print("***Sending message", msg_json)
    client.sendall(msg_json.encode("utf-8"))
    response = client.recv(1024)
    print(response, b"Message received")

client.close()