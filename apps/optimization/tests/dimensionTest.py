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


"""Dimension test file.

This module assesses the scalability of the problem by checking various dimensions.
"""


import time
import sys

sys.path.append("../source")

from sampleFunctions import ackley, createVariables
from mlmcSim import MLMC


print("Dimension test:")
t = time.time()
for i in range(6):
    n_variables = 1
    while n_variables <= 16:
        print("Variables:", n_variables)
        var = createVariables(n_variables)
        opt_levy, min_sample_levy = MLMC(
            6, var, ackley, splits_per_level=2, lambda_prune=1
        )
        print("Optimum:", opt_levy)
        print("Minimum Sample:", min_sample_levy)
        print("Execution time:", time.time() - t)
        print("")
        print("")
        print("")
        n_variables = n_variables * 2
