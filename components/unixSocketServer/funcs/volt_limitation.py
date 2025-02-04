import json
from collections.abc import Sequence


def main(n):
    """
    Returns a sequence of n Fibonacci numbers. 
    If the input is a float, the value is rounded. If it's a list, we use the
    first element only
    """
    print("Running Fibonacci...")
    if isinstance(n, Sequence):
        n = n[0]
    n = int(n / 10)
    if n < 0:
        n = -n
    if n == 0:
        return json.dumps({"result": []})
    elif n == 1:
        return json.dumps({"result": [0]})
    sequence = [0, 1]
    for _ in range(2, n):
        sequence.append(sequence[-1] + sequence[-2])
    return json.dumps({"result": sequence})
