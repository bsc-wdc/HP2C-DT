#  Copyright 2002-2023 Barcelona Supercomputing Center (www.bsc.es)

#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at

#       http://www.apache.org/licenses/LICENSE-2.0

#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.


"""
Function optimization using Multi Level Monte Carlo.

Provides the functions needed in the algorithm
"""


import numpy as np
import time
from itertools import chain
from pycompss.api.api import compss_wait_on
from pycompss.api.task import task
from pycompss.api.parameter import *
from sklearn.ensemble import RandomForestRegressor
from typing import List
from typing import Callable
from typing import Tuple


class Variable:
    """Variable class.

    An object from this class represents a concrete dimension of an specific cell.
    """

    def __init__(
        self, borders: Tuple[float, float], type: str, error: float, divs: int
    ) -> None:
        """Store the object's attributes.

        :param borders: Upper and lower limits of this dimension
        :param type: Indicates if the variable is continuous ("c") or discrete ("d")
        :param error: Minimum size of a cell in this dimension
        :param divs: Number of divisions of the variable in each level
        :return: None
        """
        self.error = error
        self.borders = borders
        self.type = type
        self.divs = divs


class Cell:
    """Cell class.

    An object from this class represents a hypercube in the searching space.
    """

    def __init__(
        self, variables: List[Variable], n_samples: int, children: List["Cell"]
    ):
        """Store the object's attributes.

        :param variables: The borders of the variables within a cell represent the limits of the cell in the search space
        :param n_samples: Number of samples to be taken within the cell in each level
        :param children: List of children cells (indicates the hierarchy of the solution)
        :return: None
        """
        self.n_samples = n_samples
        self.variables = variables
        self.children = children
        self.alive = True
        self.samples = []
        self.stats = []

    def subdivide(self, splits_per_level: int) -> None:
        """Assign subdivisions according to variable importances.

        :param splits_per_level: Times the number of cells will be doubled per level
        :return: None
        """
        X = np.array([sample[0] for sample in self.samples])
        Y = np.array([sample[1] for sample in self.samples])
        model = RandomForestRegressor()
        model.fit(X, Y)
        importances = model.feature_importances_
        cont = 0
        for i in range(len(importances)):
            if (
                self.variables[i].borders[1] - self.variables[i].borders[0]
            ) < self.variables[i].error:
                importances[i] = 0
                cont += 1
            if cont == len(importances):
                self.alive = False
                return
        subDivs = [1] * len(self.variables)
        for _ in range(splits_per_level):
            index_max_importance = np.argmax(importances)
            if importances[index_max_importance] != 0:
                subDivs[index_max_importance] *= 2
            importances[index_max_importance] /= 2
            if (
                self.variables[index_max_importance].borders[1]
                - self.variables[index_max_importance].borders[0]
            ) / subDivs[index_max_importance] < self.variables[
                index_max_importance
            ].error:
                importances[index_max_importance] = 0
        for i in range(len(subDivs)):
            self.variables[i].divs = subDivs[i]

    def getSamples(self, func: Callable) -> None:
        """Generate and evaluate samples.

        :param func: Objective function
        :return: None
        """
        for _ in range(int(self.n_samples)):
            sample = []
            for i in self.variables:
                if i.type == "c":  # continuous variable
                    sample.append(np.random.uniform(i.borders[0], i.borders[1]))
                elif i.type == "d":  # discrete variable
                    sample.append(round(np.random.uniform(i.borders[0], i.borders[1])))
            res = func(sample)
            self.samples.append((sample, res))

    def getStats(self, lambda_prune: int) -> None:
        """Obtain stats from stored samples.

        :param lambda_prune: Weighting factor in the prune
        :return: None
        """
        results = [sublista[1] for sublista in self.samples]
        min_cell = np.min(results)
        std_cell = np.std(results)
        max_cell = np.max(results)
        avg_cell = np.mean(results)
        min_std_cell = min(min_cell, (avg_cell - (lambda_prune * std_cell)))
        max_std_cell = max(max_cell, (avg_cell + (lambda_prune * std_cell)))
        self.stats = (
            min_cell,
            min_std_cell,
            max_std_cell,
            self.samples[results.index(min_cell)][0],
            avg_cell,
            std_cell,
        )

    @task(returns=1)
    def samplesStats(
        self, func, lambda_prune
    ) -> List[Tuple[float, float, float, List[float]]]:
        """Execute both getSamples and getStats as a task.

        :param func: Objective function
        :param lambda_prune: Weighting factor in the prune
        :return: List of stats of all cells in this level
        """
        self.getSamples(func)
        self.getStats(lambda_prune)
        return self.stats

    def printStats(self) -> None:
        """Print cell stats.

        :return: None
        """
        print("cell stats")
        for k in self.variables:
            print(k.borders)
        print(self.stats[0], self.stats[1], self.stats[2])
        print("___________________")

    @task()
    def getChildren(self, minMax: float, splits_per_level: int) -> List["Cell"]:
        """Subdivide the cell into 2^splits_per_level subcells.

        :param minMax: Cutoff value
        :param splits_per_level: Times the number of cells will be doubled per level
        :return: List of children
        """
        children = []
        CV = 50
        for i in range(len(self.variables)):
            if (
                self.variables[i].borders[1] - self.variables[i].borders[0]
            ) < self.variables[i].error:
                self.variables[i].divs = 1
        if self.stats[1] <= minMax:  # CV valor absoluto std?????
            if self.stats[4] != 0:
                CV = round(
                    self.stats[5] / np.abs(self.stats[4]) * 100
                )  # coefficient of variation
            else:
                CV = 50
            if CV > 0:
                self.subdivide(splits_per_level)
                children = gen_grid(CV, self.variables, self.alive)
                for i in range(len(self.samples)):
                    for j in range(len(children)):
                        belongs = True
                        for k in range(len(self.samples[i][0])):
                            if (
                                self.samples[i][0][k]
                                < children[j].variables[k].borders[0]
                                or self.samples[i][0][k]
                                > children[j].variables[k].borders[1]
                            ):
                                belongs = False
                                break
                        if belongs:
                            children[j].samples.append(self.samples[i])
                            if children[j].n_samples > 1:
                                children[j].n_samples = children[j].n_samples - 1
                            break
        self.alive = False
        return children


def MLMC(
    n_levels: int,
    variables: List[Variable],
    func: Callable[[List[float]], float],
    splits_per_level: int = 3,
    lambda_prune: int = 3,
) -> Tuple[float, List[float]]:
    """Get result from function and other optional parameters.

    :param n_levels: Number of levels in the algorithm
    :param variables: List of variables defining the search space
    :param func: Objective function
    :param splits_per_level: Times the number of cells will be doubled per level
                             (default: 3)
    :param lambda_prune: Weighting factor in the prune
                         (default: 3)
    :return: Minimum value found and its associated sample
    """
    grid, stats, minimo, min_sample, minMax, samples, min_values = initialise(
        variables, func, lambda_prune
    )

    for i in range(n_levels):
        t = time.time()

        grid = get_Children(grid, minMax, splits_per_level)
        if len(grid) == 0:
            break

        stats = samples_Stats(grid, func, lambda_prune, samples)

        minimo, min_sample, minMax, min_values = minimum_Cutoff(
            minimo, minMax, stats, min_values, min_sample
        )
        grid = compss_wait_on(grid)
        show_Results(minimo, i, grid, t)

    print("#SAMPLES:", samples, sum(samples))
    return minimo, min_sample


def printgrid(grid: List[Cell]) -> None:
    """Print the whole grid.

    :param grid: Every cell in the algorithm
    :return: None
    """
    for i in grid:
        cont = 0
        for j in i.variables:
            print("Variable borders ", cont, ": ", j.borders)
            cont = cont + 1
        print("Cell stats: ", i.stats)
        print(
            "___________________________________________________________________________"
        )


@task()
def join(
    minim0: Tuple[float, float, float, List[float]],
    minim1: Tuple[float, float, float, List[float]],
) -> Tuple[float, float, float, List[float]]:
    """Join the results of the function getMinMax.

    :param minim0: Stats of the first cell to compare
    :param minim1: Stats of the second cell to compare
    :return: Stats of the cell with the minimum minimum value
    """
    min_sample = None
    minim = None
    if minim0[0] < minim1[0]:
        minim = minim0[0]
        min_sample = minim0[3]
    else:
        minim = minim1[0]
        min_sample = minim1[3]
    ret = (minim, minim0[1], min(minim0[2], minim1[2]), min_sample)
    return ret


def getMinMax(
    stats: List[Tuple[float, float, float, List[float]]]
) -> Tuple[float, float, List[float]]:
    """Get minimum, minimum upper limit and minimum sample from active cells.

    :param stats: List of stats of all cells in this level
    :return: Minimum, minimum upper limit and minimum sample
    """
    neighbor = 2
    while neighbor < len(stats):
        for result in range(0, len(stats), 2 * neighbor):
            if (result + neighbor) < len(stats):
                stats[result] = join(stats[result], stats[result + neighbor])
        neighbor = neighbor * 2
    stats = compss_wait_on(stats)
    return stats[0][0], stats[0][2], stats[0][3]


def gen_grid(
    samples_cell: int, variables: List[Variable], alive: bool = True
) -> List[Cell]:
    """Generate the grid based on the given variables.

    :param samples_cell: Number of samples to take within each cell
    :param variables: List of variables in the problem
    :param alive: Tells if cell must be subdivided or not
                  (default: true)
    :return: List with all the children cells
    """
    grid = []
    if alive:
        n_variables = len(variables)
        n_samples = samples_cell
        ini = tuple(variable.borders[0] for variable in variables)
        fin = tuple(variable.borders[1] for variable in variables)
        div = tuple(variable.divs for variable in variables)
        total_div = np.prod(div)
        for i in range(total_div):
            div_index = np.unravel_index(i, div)
            lower = [
                ini[j] + (fin[j] - ini[j]) / div[j] * div_index[j]
                for j in range(n_variables)
            ]
            upper = [
                ini[j] + (fin[j] - ini[j]) / div[j] * (div_index[j] + 1)
                for j in range(n_variables)
            ]
            nueva = []
            for j in range(len(variables)):
                nueva.append(
                    Variable(
                        (lower[j], upper[j]),
                        variables[j].type,
                        variables[j].error,
                        variables[j].divs,
                    )
                )
            grid.append(Cell(nueva, n_samples, []))
    return grid


def printStatsLevel(min: float, min_sample: List[float], i: int) -> None:
    """Print the stats of each level.

    :param min: Minimum found up to this level
    :param min_sample: Sample associated to min
    :param i: Current level
    :return: None
    """
    print("")
    print("MIN LEVEL ", i + 1, ": ", min)
    print("MIN SAMPLE: ", min_sample)


def samples_Stats(
    grid: List[Cell], func: Callable, lambda_prune: int, samples: List[int]
) -> List[Tuple[float, float, float, List[float]]]:
    """Call samplesStats for each cell.

    :param grid: Every cell in the algorithm
    :param func: Objective function
    :param lambda_prune: Weighting factor in the prune
    :param samples: List of number of samples taken within each level
    :return: List of stats of all cells in the problem
    """
    stats = [None] * len(grid)
    n_samples = 0
    for j in range(len(grid)):
        stats[j] = grid[j].samplesStats(func, lambda_prune)
        n_samples = n_samples + len(grid[j].samples)
    samples.append(n_samples)
    return stats


def initialise(
    variables: List[Variable], func: Callable, lambda_prune: int
) -> Tuple[
    List[Cell],
    List[Tuple[float, float, float, List[float]]],
    float,
    List[float],
    float,
    List[int],
    List[float],
]:
    """Set initial variables and evaluates first level.

    :param variables: List of variables in the problem
    :param func: Objective function
    :param lambda_prune: Weighting factor in the prune
    :return: Initial grid, stats, minimum, sample associated to this minimum, cutoff value, list of number of samples taken, and list of minimum values found
    """
    grid = []
    grid.append(Cell(variables, 50, []))
    stats = [grid[0].samplesStats(func, lambda_prune)]
    minim = getMinMax(stats)
    min = minim[0]
    min_sample = minim[2]
    minMax = minim[1]
    samples = [grid[0].n_samples]
    min_values = [min]
    return grid, stats, min, min_sample, minMax, samples, min_values


def get_Children(grid: List[Cell], minMax: int, splits_per_level: int) -> List[Cell]:
    """Call getChildren for each cell.

    :param grid: Every cell in the algorithm
    :param minMax: Cutoff value
    :param splits_per_level: Times the number of cells will be doubled per level
    :return: List of children cells
    """
    new = []
    for j in grid:
        children = j.getChildren(minMax, splits_per_level)
        new.append(children)
    new = compss_wait_on(new)
    new = list(set(chain(*new)))
    return new


def minimum_Cutoff(
    min: float,
    minMax: float,
    stats: List[Tuple[float, float, float, List[float]]],
    min_values: List[float],
    min_sample: List[float],
) -> Tuple[float, List[float], float, List[float]]:
    """Update minimum and cutoff value.

    :param min: Minimum found up to this level
    :param minMax: Cutoff value
    :param stats: List of stats of all cells in this level
    :param min_values: List of minimum values found
    :param min_sample: Sample associated to min
    :return: Updated minimum, minimum sample, cutoff value and list of minimum values
    """
    minim = getMinMax(stats)
    if min is None or minim[0] < min:
        min = minim[0]
        min_sample = minim[2]
    if minMax is None or minim[1] < minMax:
        minMax = minim[1]
    min_values.append(min)
    return min, min_sample, minMax, min_values


def show_Results(min: float, i: int, grid: List[Cell], t: float) -> None:
    """Print the execution data.

    :param min: Minimum value found
    :param i: Level index
    :param grid: List containing every cell in the algorithm
    :param t: Execution time
    :return: None
    """
    print("LEVEL", i + 1, ":")
    print("     minimum:      ", min)
    print("     cells:    ", len(grid))
    print("     time:      ", time.time() - t)
    print("")
