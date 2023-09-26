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


"""Granularity test file.

This module examines the scalability of the infrastructure.
"""


import sys
import time
import numpy as np
import math

sys.path.append("../source")

from sampleFunctions import levy, schwefel, ackley, createVariables, Ackley2Function
from mlmcSim import MLMC
from pycompss.api.api import compss_barrier


def main():
    print("Granularity (objective function cost):")
    sleep = 0
    while sleep < 1:
        compss_barrier()
        t1 = time.time()
        print("COST(s):", sleep)
        ackley2 = Ackley2Function(sleep)
        var = createVariables(1)
        opt_levy, min_sample_levy = MLMC(5, var, ackley2)
        print("Optimum:", opt_levy)
        print("Minimum Sample:", min_sample_levy)
        if sleep == 0:
            sleep = 0.001
        else:
            sleep = sleep * 5
        print("Execution time:", time.time() - t1)
        print("")
        print("")


if __name__ == "__main__":
    main()
