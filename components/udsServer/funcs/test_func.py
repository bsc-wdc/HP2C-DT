import json


def main(n):
    """ Echoes the received message. """
    return json.dumps({"result": f"Test: {n}"})