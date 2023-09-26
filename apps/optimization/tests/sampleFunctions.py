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


"""This file contains sample functions useful to test the algorithm.

It offers various objective functions such as Ackley, Levy N.13, and Schwefel, which are commonly used 
for testing these types of problems. It also includes basic functions like a linear and an exponential function. 
These functions can be tested by passing them as the third parameter to the MLMC function.
"""


import numpy as np
import math
import os
import time

from mlmcSim import MLMC, Variable


class Ackley2Function:
    def __init__(self, sleep):
        self.sleep = sleep

    def __call__(self, variables):
        x = np.array(variables)
        dim = len(x)
        sum1 = 0
        sum2 = 0
        for i in range(dim):
            sum1 += x[i] ** 2
            sum2 += math.cos(2 * math.pi * x[i])
        term1 = -20 * math.exp(-0.2 * math.sqrt(sum1 / dim))
        term2 = -math.exp(sum2 / dim)
        y = term1 + term2 + 20 + math.e
        time.sleep(self.sleep)
        return y


def ackley(x):
    dim = len(x)
    sum1 = 0
    sum2 = 0
    for i in range(dim):
        sum1 += x[i] ** 2
        sum2 += math.cos(2 * math.pi * x[i])
    term1 = -20 * math.exp(-0.2 * math.sqrt(sum1 / dim))
    term2 = -math.exp(sum2 / dim)
    y = term1 + term2 + 20 + math.e
    return y


def levy(variables):
    variables = np.array(variables)
    d = len(variables)
    term1 = (np.sin(3 * np.pi * variables[0])) ** 2
    term2 = ((variables[-1] - 1) ** 2) * (1 + (np.sin(2 * np.pi * variables[-1])) ** 2)
    term3 = 0
    for i in range(d - 1):
        term = ((variables[i] - 1) ** 2) * (
            1 + (np.sin(3 * np.pi * variables[i + 1])) ** 2
        )
        term3 += term
    res = term1 + term2 + term3
    return res


def schwefel(variables):
    x = np.array(variables)
    s = np.sum(-x * np.sin(np.sqrt(np.abs(x))))
    return 418.9829 * len(x) - s


def createVariables(n_variables):
    var = []
    for i in range(n_variables):
        var.append(Variable((-10, 10), "c", 0.001, 2))
    return var


def plain(variables):
    return 2


def exponential(variables):
    res = 0
    for i in range(len(variables)):
        res += 2 ** variables[i]
    return res
