# Dataset Generator using Multilevel Monte Carlo with Latin Hypercube Sampling

This repository contains an algorithm for generating datasets based on the Multilevel Monte Carlo method with Latin Hypercube Sampling. The purpose of this project is to efficiently run simulations in regions and subregions with high entropy.

## Overview

The implemented algorithm leverages the Multilevel Monte Carlo approach to create datasets. The main functions required for the algorithm are included, along with additional modules used for testing purposes. Below is a brief description of each module:

- **classes**: This module contains the main function and its dependencies.
- **simpleTest**: Here, you can find a simple test file to evaluate the functionality.
- **utils**: The random objective function is declared in this module.

## Running the Application

To run the application, execute the provided test file. You can also define new tests by specifying the required parameters for the "exec" method (the main method in the classes module). The parameters are as follows:

- **n_samples**: The number of samples taken for each dimension.
- **Dims**: A list of dimension objects, each containing:
    - **Variables**: A list of pairs containing the lower and upper bounds of each variable within the dimension.
    - **n_subsamples**: The number of subsamples taken for each sample.
    - **divs**: The number of splits in this dimension.
    - **dim_min**, **dim_max**: The lower and upper bounds of the dimension.
- **Function**: The objective function (imported from utils) used for evaluation.
- **error**: The dimension size limit for recursive exploration.

The execution of the algorithm returns detailed information for each branch cell, including the number of subsamples taken, stability of each subsample (result of evaluating the sample), cell's entropy, and delta_entropy. This information provides valuable insights into the dataset generation process.


