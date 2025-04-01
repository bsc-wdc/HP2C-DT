import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import scienceplots

plt.style.use("science")


def load_data():
    results_path = os.path.join(os.path.dirname(__file__), "../results")
    edge_file = os.path.join(results_path, "results_Edge.csv")
    server_file = os.path.join(results_path, "results_Server.csv")
    #sequential_file = os.path.join(results_path, "results_Sequential.csv")

    edge_df = pd.read_csv(edge_file)
    server_df = pd.read_csv(server_file)
    #sequential_df = pd.read_csv(sequential_file)
    sequential_df = None

    return edge_df, server_df, sequential_df


def plot_results(edge_df, server_df, sequential_df):
    filepath = os.path.dirname(__file__)
    msize_values = sorted(edge_df['msize'].unique())

    for msize in msize_values:
        plt.figure(figsize=(8, 6))

        edge_subset = edge_df[edge_df['msize'] == msize]
        server_subset = server_df[server_df['msize'] == msize]
        #sequential_subset = sequential_df[sequential_df['msize'] == msize]

        bsize = edge_subset['bsize']
        x_ticks = np.arange(len(bsize))
        width = 0.2

        plt.bar(x_ticks - width, edge_subset['average_time'], width,
                label='Edge', yerr=edge_subset['std_dev'], capsize=4)
        plt.bar(x_ticks, server_subset['average_time'], width, label='Server',
                yerr=server_subset['std_dev'], capsize=4)
        """plt.bar(x_ticks + width, sequential_subset['average_time'], width,
                label='Sequential', yerr=sequential_subset['std_dev'],
                capsize=4)"""

        plt.xticks(x_ticks, labels=bsize)
        plt.yscale("log")
        plt.xlabel("Block Size (bsize)")
        plt.ylabel("Average Time (ms)")
        plt.title(f"Comparison of Execution Modes for msize={msize}")
        plt.legend()
        plt.grid(True, which="both", linestyle="--", linewidth=0.5)
        output_dir = os.path.join(filepath, "..", "results")
        output_path = os.path.join(output_dir, f"comparison_msize_{msize}.png")
        plt.savefig(output_path, dpi=300)
        plt.close()


def main():
    edge_df, server_df, sequential_df = load_data()
    plot_results(edge_df, server_df, sequential_df)


if __name__ == "__main__":
    main()
