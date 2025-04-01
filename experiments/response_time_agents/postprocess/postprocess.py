import os
import csv
import re
import sys


def extract_params(filename):
    match = re.match(r'msize(\d+)-bsize(\d+)-mode(\w+)\.log', filename)
    if match:
        msize, bsize, mode = match.groups()
        return int(msize), int(bsize), mode
    return None


def process_logs(directory):
    results = {"Edge": [], "Server": []}

    for filename in os.listdir(directory):
        params = extract_params(filename)
        if not params:
            continue

        msize, bsize, mode = params
        filepath = os.path.join(directory, filename)

        with open(filepath, 'r') as f:
            # Read all lines that contain "Job completed after"
            times_lines = [line for line in f if "Job completed after" in line]
            # Take only the last 10 lines (or fewer if there aren't enough)
            last_10_lines = times_lines[-10:] if len(times_lines) >= 10 else times_lines
            # Extract the times from these lines
            times = [int(re.search(r'\d+', line).group()) for line in last_10_lines]

        if times:
            avg_time = sum(times) / len(times)
            results[mode].append((msize, bsize, avg_time))

    output_dir = os.path.join(os.path.dirname(__file__), "results")
    os.makedirs(output_dir, exist_ok=True)

    for mode, data in results.items():
        data.sort(key=lambda x: (x[0], x[1]))  # Sort by msize then bsize
        output_file = os.path.join(output_dir, f"results_{mode}.csv")

        with open(output_file, 'w', newline='') as csvfile:
            writer = csv.writer(csvfile)
            writer.writerow(["msize", "bsize", "average_time"])
            writer.writerows(data)


def main():
    if len(sys.argv) != 2:
        print("Usage: python script.py <directory>")
        sys.exit(1)

    directory = sys.argv[1]
    process_logs(directory)


if __name__ == "__main__":
    main()