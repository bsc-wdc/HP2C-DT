from collections.abc import Sequence

def main(n):
    """Generator for Fibonacci numbers."""
    if isinstance(n, Sequence):
        n = n[0]
    n = int(n)
    if n < 0:
        n = -n
    if n == 0:
        return []
    elif n == 1:
        return [0]
    sequence = [0, 1]
    for _ in range(2, n):
        sequence.append(sequence[-1] + sequence[-2])
    return sequence
