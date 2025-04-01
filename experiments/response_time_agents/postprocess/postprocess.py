import os
import csv
import re
import sys
import statistics


def extract_params(filename):
    match = re.match(r'msize(\d+)-bsize(\d+)-mode(\w+)\.log', filename)
    if match:
        msize, bsize, mode = match.groups()
        return int(msize), int(bsize), mode
    return None


def process_logs(directory):
    results = {"Edge": [], "Server": [], "Sequential": []}

    for filename in os.listdir(directory):
        params = extract_params(filename)
        if not params:
            continue

        msize, bsize, mode = params
        filepath = os.path.join(directory, filename)

        with open(filepath, 'r') as f:
            times_lines = [line for line in f if "Job completed after" in line]
            last_10_lines = times_lines[-10:] if len(
                times_lines) >= 10 else times_lines
            times = [int(re.search(r'\d+', line).group()) for line in
                     last_10_lines]

        if times:
            avg_time = sum(times) / len(times)
            std_dev = statistics.stdev(times) if len(times) > 1 else 0
            results[mode].append((msize, bsize, avg_time, std_dev))

    output_dir = os.path.join(os.path.dirname(__file__), "results")
    os.makedirs(output_dir, exist_ok=True)

    for mode, data in results.items():
        if not data:  # Skip if no data for this mode
            continue

        data.sort(key=lambda x: (x[0], x[1]))  # Sort by msize then bsize
        output_file = os.path.join(output_dir, f"results_{mode}.csv")

        with open(output_file, 'w', newline='') as csvfile:
            writer = csv.writer(csvfile)
            writer.writerow(["msize", "bsize", "average_time", "std_dev"])
            writer.writerows(data)


def main():
    if len(sys.argv) != 2:
        print("Usage: python script.py <directory>")
        sys.exit(1)

    directory = sys.argv[1]
    process_logs(directory)


if __name__ == "__main__":
    main()