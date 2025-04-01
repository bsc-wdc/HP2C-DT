import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import scienceplots

plt.style.use("science")

# Marker styles for Edge and Server
MARKERS = {"Edge": "o", "Server": "s"}

# Color map for different msize values
COLORS = plt.cm.viridis


def load_data():
    results_path = os.path.join(os.path.dirname(__file__), "../results")
    edge_file = os.path.join(results_path, "results_Edge.csv")
    server_file = os.path.join(results_path, "results_Server.csv")

    edge_df = pd.read_csv(edge_file)
    server_df = pd.read_csv(server_file)

    return edge_df, server_df


def plot_results(edge_df, server_df):
    filepath = os.path.dirname(__file__)
    output_dir = os.path.join(filepath, "..", "results")
    os.makedirs(output_dir, exist_ok=True)

    msize_values = sorted(edge_df['msize'].unique())
    color_map = {msize: COLORS(i / len(msize_values)) for i, msize in enumerate(msize_values)}

    plt.figure(figsize=(8, 6))

    for msize in msize_values:
        edge_subset = edge_df[edge_df['msize'] == msize]
        server_subset = server_df[server_df['msize'] == msize]

        # Plot Edge
        plt.plot(edge_subset['bsize'], edge_subset['average_time'],
                 marker=MARKERS["Edge"], linestyle='-',
                 label=f'Edge, msize={msize}',
                 color=color_map[msize])

        # Plot Server
        plt.plot(server_subset['bsize'], server_subset['average_time'],
                 marker=MARKERS["Server"], linestyle='--',
                 label=f'Server, msize={msize}',
                 color=color_map[msize])

    plt.xscale("log")
    plt.yscale("log")
    plt.xlabel("Block Size (bsize)")
    plt.ylabel("Average Execution Time (ms)")
    plt.title("Execution Time Comparison")
    plt.legend()
    plt.grid(True, which="both", linestyle="--", linewidth=0.5)

    output_path = os.path.join(output_dir, "execution_time.png")
    plt.savefig(output_path, dpi=300)
    plt.close()

    # Additional plot with execution times displayed next to points
    """plt.figure(figsize=(8, 6))

    for msize in msize_values:
        edge_subset = edge_df[edge_df['msize'] == msize]
        server_subset = server_df[server_df['msize'] == msize]

        # Plot Edge
        plt.plot(edge_subset['bsize'], edge_subset['average_time'],
                 marker=MARKERS["Edge"], linestyle='-',
                 label=f'Edge, msize={msize}',
                 color=color_map[msize])
        for i, txt in enumerate(edge_subset['average_time']):
            plt.text(edge_subset['bsize'].iloc[i], txt, f'{txt:.2f}', fontsize=8, ha='right')

        # Plot Server
        plt.plot(server_subset['bsize'], server_subset['average_time'],
                 marker=MARKERS["Server"], linestyle='--',
                 label=f'Server, msize={msize}',
                 color=color_map[msize])
        for i, txt in enumerate(server_subset['average_time']):
            plt.text(server_subset['bsize'].iloc[i], txt, f'{txt:.2f}', fontsize=8, ha='left')

    plt.xscale("log")
    plt.yscale("log")
    plt.xlabel("Block Size (bsize)")
    plt.ylabel("Average Execution Time (ms)")
    plt.title("Execution Time Comparison with Labels")
    plt.legend()
    plt.grid(True, which="both", linestyle="--", linewidth=0.5)

    output_path = os.path.join(output_dir, "execution_time_with_labels.png")
    plt.savefig(output_path, dpi=300)
    plt.close()"""


def main():
    edge_df, server_df = load_data()
    plot_results(edge_df, server_df)


if __name__ == "__main__":
    main()
