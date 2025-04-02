import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import scienceplots

plt.style.use("science")

# Marker styles for Edge and Server
MARKERS = {"Edge Parallel": "o", "Server": "s", "Edge Sequential": "^"}

# Color map for different msize values
COLORS = plt.cm.viridis


def load_data(version):
    results_path = os.path.join(os.path.dirname(__file__), f"../results/{version}")
    edge_file = os.path.join(results_path, "results_Edge.csv")
    server_file = os.path.join(results_path, "results_Server.csv")
    sequential_file = os.path.join(results_path, "results_Sequential.csv")

    edge_df = pd.read_csv(edge_file)
    server_df = pd.read_csv(server_file)
    sequential_df = pd.read_csv(sequential_file)

    return edge_df, server_df, sequential_df


def plot_results(edge_df, server_df, sequential_df, version):
    filepath = os.path.dirname(__file__)
    output_dir = f"{filepath}/../results/{version}"
    os.makedirs(output_dir, exist_ok=True)

    msize_values = sorted(edge_df['msize'].unique())
    color_map = {msize: COLORS(i / len(msize_values)) for i, msize in enumerate(msize_values)}

    plt.figure(figsize=(8, 6))

    for msize in msize_values:
        edge_subset = edge_df[edge_df['msize'] == msize]
        server_subset = server_df[server_df['msize'] == msize]
        sequential_subset = sequential_df[sequential_df['msize'] == msize]

        # Plot Edge
        plt.plot(edge_subset['bsize'], edge_subset['average_time'],
                 marker=MARKERS["Edge Parallel"], linestyle='dotted',
                 label=f'Edge Parallel, msize={msize}',
                 color=color_map[msize])

        # Plot Edge Sequential
        plt.plot(sequential_subset['bsize'],
                 sequential_subset['average_time'],
                 marker=MARKERS["Edge Sequential"], linestyle='-',
                 label=f'Edge Sequential, msize={msize}',
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

    output_path = os.path.join(output_dir, "execution_time_exp2.pdf")
    plt.savefig(output_path, dpi=300)
    output_path = os.path.join(output_dir, "execution_time_exp2.png")
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


def main(version):
    edge_df, server_df, sequential_df = load_data(version)
    plot_results(edge_df, server_df, sequential_df, version)


if __name__ == "__main__":
    import sys
    if len(sys.argv) != 2:
        print("Usage: python run_tests.py <simple|simple_external")
        sys.exit(1)

    version = sys.argv[1]

    main(version)
