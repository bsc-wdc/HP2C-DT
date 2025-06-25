import os
import sys
import pandas as pd
import re

dirname = os.path.dirname(__file__)

def extract_parameters(filename):
    """Extracts ts and ws from the filename."""
    match = re.search(r'ts(\d+)_ws(\d+)', filename)
    if match:
        ts, ws = int(match.group(1)), int(match.group(2))
        frequency = ts * ws  # Calculate frequency
        return ts, frequency
    return None, None

def process_csv_files(args):
    """Processes CSV files and creates a table with ts as rows and frequency as columns."""
    if len(args) > 1:
        directory = args[1]
    else:
        directory = f"{dirname}/../results/raw"

    all_data = {}
    phasor_data = {}
    all_frequencies = set()
    phasor_frequencies = set()

    for filename in os.listdir(directory):
        filepath = os.path.join(directory, filename)

        if not filename.endswith('.csv'):
            continue

        ts, frequency = extract_parameters(filename)
        if ts is None or frequency is None:
            continue

        df = pd.read_csv(filepath, delimiter=',')
        if df.shape[0] > 1:
            avg_bytes_per_second = df.iloc[1:]['bytes_per_millisecond'].mean() * 1000
        else:
            avg_bytes_per_second = None

        if filename.startswith('all_'):
            all_data.setdefault(ts, {})[frequency] = avg_bytes_per_second
            all_frequencies.add(frequency)
        else:
            phasor_data.setdefault(ts, {})[frequency] = avg_bytes_per_second
            phasor_frequencies.add(frequency)

    # Convert dictionaries to DataFrames and ensure proper column ordering
    all_columns_sorted = sorted(all_frequencies)
    phasor_columns_sorted = sorted(phasor_frequencies)

    all_df = pd.DataFrame.from_dict(all_data, orient='index').sort_index().reindex(columns=all_columns_sorted)
    phasor_df = pd.DataFrame.from_dict(phasor_data, orient='index').sort_index().reindex(columns=phasor_columns_sorted)

    output_dir = f"{dirname}/../results"
    os.makedirs(output_dir, exist_ok=True)
    all_df.to_csv(f"{output_dir}/all_table.csv")
    phasor_df.to_csv(f"{output_dir}/phasor_table.csv")

    print(f"Tables stored in {output_dir}")

if __name__ == "__main__":
    process_csv_files(sys.argv)
