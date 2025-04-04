import os
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
import scienceplots

plt.style.use("science")

# Paleta de colores corregida
COLOR_PALETTES = {
    "Edge Parallel": ["#E58606", "#D55E00", "#FF9900"],  # Naranja corregido para m=1
    "Edge Sequential": ["#56B4E9", "#0072B2", "#1E90FF"],  # Tonos de azul
    "Cloud": ["#009E73", "#3CB371", "#2E8B57"]  # Tonos de verde
}

MARKER_SIZE = 3

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

    # Filter msize=8
    edge_df = edge_df[edge_df['msize'] != 8]
    server_df = server_df[server_df['msize'] != 8]
    sequential_df = sequential_df[sequential_df['msize'] != 8]

    msize_values = sorted(edge_df['msize'].unique())

    color_maps = {
        "Edge Parallel": {msize: COLOR_PALETTES["Edge Parallel"][i % 3] for i, msize in enumerate(msize_values)},
        "Edge Sequential": {msize: COLOR_PALETTES["Edge Sequential"][i % 3] for i, msize in enumerate(msize_values)},
        "Cloud": {msize: COLOR_PALETTES["Cloud"][i % 3] for i, msize in enumerate(msize_values)}
    }

    plt.figure(figsize=(8, 6))

    # Plot all Edge Sequential first
    for i, msize in enumerate(msize_values):
        sequential_subset = sequential_df[sequential_df['msize'] == msize]
        plt.plot(sequential_subset['bsize'], sequential_subset['average_time'],
                 marker='o', markersize=MARKER_SIZE,
                 label=f'Edge Sequential, $m={msize}$',
                 color=color_maps["Edge Sequential"][msize])

    # Then plot all Edge Parallel
    for i, msize in enumerate(msize_values):
        edge_subset = edge_df[edge_df['msize'] == msize]
        plt.plot(edge_subset['bsize'], edge_subset['average_time'],
                 marker='o', markersize=MARKER_SIZE,
                 label=f'Edge Parallel, $m={msize}$',
                 color=color_maps["Edge Parallel"][msize])

    # Finally plot all Cloud
    for i, msize in enumerate(msize_values):
        server_subset = server_df[server_df['msize'] == msize]
        plt.plot(server_subset['bsize'], server_subset['average_time'],
                 marker='o', markersize=MARKER_SIZE,
                 label=f'Cloud, $m={msize}$',
                 color=color_maps["Cloud"][msize])

    plt.xscale("log")
    plt.yscale("log")
    plt.xlabel("Block Size ($b$)")
    plt.ylabel("Average Execution Time (ms)")

    plt.legend(
        frameon=True,
        framealpha=0.7,
        facecolor='white'
    )

    plt.grid(True, which="both", linestyle="--", linewidth=0.5)
    output_path = os.path.join(output_dir, "execution_time_exp2.pdf")
    plt.savefig(output_path, dpi=300)
    output_path = os.path.join(output_dir, "execution_time_exp2.png")
    plt.savefig(output_path, dpi=300)
    plt.close()


def main(version):
    edge_df, server_df, sequential_df = load_data(version)
    plot_results(edge_df, server_df, sequential_df, version)


if __name__ == "__main__":
    import sys
    if len(sys.argv) != 2:
        print("Usage: python run_tests.py <simple|simple_external>")
        sys.exit(1)

    version = sys.argv[1]
    main(version)
