
from collections.abc import Sequence


def main(sensors, actuators, n):
    """
    Returns a sequence of n Fibonacci numbers based on the received numbers.

    If the input is a float, the value is rounded. If it's a list, we use the
    first element only
    """
    print("Running Fibonacci...")

    # Prepare input
    print(f"[Fibonacci]: using sensed value {n} as input")
    if isinstance(n, Sequence):
        n = n[0]
    if n < 0:
        n = -n

    # Compute sequence
    if n == 0:
        result = []
    elif n == 1:
        result = [0]
    else:
        result = [0, 1]
        for _ in range(2, n):
            result.append(result[-1] + result[-2])
    return result, {} # Return the result and an empty dictionary (actuations)


def other_main(sensors, actuators, n):
    return f"Alternative result: {n}"
